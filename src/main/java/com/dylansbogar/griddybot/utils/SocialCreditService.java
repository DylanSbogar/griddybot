package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.SocialCredit;
import com.dylansbogar.griddybot.entities.SocialCreditDebugLog;
import com.dylansbogar.griddybot.repositories.SocialCreditDebugLogRepository;
import com.dylansbogar.griddybot.repositories.SocialCreditRepository;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocialCreditService {
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String API_KEY = System.getenv("OPENROUTER_API_KEY");
    private static final String EVAL_MODEL = "google/gemini-2.0-flash-001";
    private static final String EDITOR_MODEL = "minimax/minimax-m2.7";
    private static final String EDITOR_USER_ID = "__editor__";
    private static final String EVAL_USER_ID = "__eval__";
    // Global safety cap on the single conversation prompt. Far above the
    // realistic ~700 msgs/week — only trims a runaway week.
    private static final int MAX_MESSAGES_TOTAL = 2000;
    // The single whole-conversation call is one point of failure for the whole
    // week, so it gets retried: 1 attempt + 2 retries = 3 total.
    private static final int MAX_ATTEMPTS = 3;
    private static final int MIN_DELTA = -100;
    private static final int MAX_DELTA = 100;

    // Inactivity tracking — see CLAUDE.md / README for rationale.
    private static final int INACTIVITY_FLOOR = 50;
    private static final double INACTIVITY_THRESHOLD_FRACTION = 0.30;
    private static final int QUIET_PENALTY = -10;
    private static final int ABSENT_PENALTY = -20;

    // Reaction tiers — bot self-reactions are subtracted before tiering.
    private static final int REACTION_TIER_GOOD = 3;
    private static final int REACTION_TIER_BETTER = 4;
    private static final int REACTION_TIER_LEGENDARY = 5;

    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final SocialCreditRepository creditRepo;
    private final SocialCreditDebugLogRepository debugLogRepo;

    public WeeklyReport evaluateWeek(MessageChannel channel) {
        boolean debug = SocialCreditDebug.DEBUG_MODE;
        Instant runAt = Instant.now();
        Instant cutoff = debug
                ? runAt.minus(SocialCreditDebug.DEBUG_WINDOW_HOURS, ChronoUnit.HOURS)
                : runAt.minus(7, ChronoUnit.DAYS);

        log(debug, "Social credit run starting at " + runAt + " (window cutoff: " + cutoff + ")");

        List<Message> weekMessages;
        try {
            weekMessages = channel.getIterableHistory()
                    .takeWhileAsync(m -> m.getTimeCreated().toInstant().isAfter(cutoff))
                    .join();
        } catch (Exception e) {
            System.out.println("Failed to fetch channel history: " + e.getMessage());
            return new WeeklyReport(null, creditRepo.findAllByOrderByTotalPointsDesc());
        }

        log(debug, "Fetched " + weekMessages.size() + " messages from channel");

        // Filter to tracked authors (regulars only in production; anyone in
        // debug, since the friend tests in his own channel without the roster),
        // skip bots and blank content, and sort chronologically so the whole
        // week reads as one conversation. Alias accounts (e.g. Matt's alt) are
        // collapsed to the canonical user everywhere downstream.
        List<Message> tracked = weekMessages.stream()
                .filter(m -> !m.getAuthor().isBot())
                .filter(m -> !m.getContentRaw().isBlank())
                .filter(m -> {
                    if (debug) return true;
                    String canonId = UserConstants.canonicalUserId(m.getAuthor().getId());
                    return UserConstants.REGULAR_USER_IDS.contains(canonId);
                })
                .sorted(Comparator.comparing(Message::getTimeCreated))
                .collect(Collectors.toList());

        List<Message> capped = capTotal(tracked, MAX_MESSAGES_TOTAL);
        boolean capApplied = tracked.size() > MAX_MESSAGES_TOTAL;
        RunStats stats = new RunStats(
                weekMessages.size(), tracked.size(), capped.size(), capApplied);

        // Group by canonical user — used only for per-user message counts
        // (the inactivity phase) and to resolve each canonical user's display
        // name for the roster. It is NOT the unit of evaluation anymore.
        Map<String, List<Message>> byCanonId = capped.stream()
                .collect(Collectors.groupingBy(
                        m -> UserConstants.canonicalUserId(m.getAuthor().getId())));

        log(debug, "Messages: fetched=" + stats.fetched() + ", tracked=" + stats.tracked()
                + ", evaluated=" + stats.evaluated() + ", active users=" + byCanonId.size()
                + (capApplied ? "  *** CAP HIT — raise MAX_MESSAGES_TOTAL ***" : ""));

        if (byCanonId.isEmpty()) {
            log(debug, "No tracked messages this window; nothing to evaluate.");
            return new WeeklyReport(null, creditRepo.findAllByOrderByTotalPointsDesc());
        }

        Map<String, Integer> msgCounts = new HashMap<>();
        Map<String, String> roster = new LinkedHashMap<>();
        for (Map.Entry<String, List<Message>> entry : byCanonId.entrySet()) {
            String canonId = entry.getKey();
            msgCounts.put(canonId, entry.getValue().size());
            User canonicalUser = resolveCanonicalUser(canonId, entry.getValue(), channel);
            roster.put(canonId, canonicalUser.getAsTag());
        }

        String transcript = buildTranscript(capped, roster);
        String topReactedSection = buildTopReactedSection(findTopReacted(capped), roster);
        String prompt = buildPrompt(roster, transcript, topReactedSection);

        // Phase 1: single whole-conversation eval (up to 3 attempts).
        List<UserResult> preAdjust = evaluateConversation(
                prompt, roster, debug, runAt, stats, transcript);
        if (preAdjust == null) {
            // All retries exhausted — abort cleanly. Write nothing, post the
            // quiet embed rather than a broken or guessed recap.
            log(debug, "Aborting run: evaluation failed after " + MAX_ATTEMPTS + " attempts.");
            return new WeeklyReport(null, creditRepo.findAllByOrderByTotalPointsDesc());
        }

        // Raw (model) delta per user, captured before inactivity adjustments so
        // the debug log can show raw vs final and explain any gap.
        Map<String, Integer> rawDeltas = preAdjust.stream()
                .collect(Collectors.toMap(UserResult::userId, UserResult::delta));

        // Phase 2: apply inactivity adjustments (production only — debug runs
        // in a test channel where the production roster isn't posting).
        List<UserResult> adjusted = debug
                ? preAdjust
                : applyInactivityAdjustments(preAdjust, msgCounts, channel, debug);

        // Phase 3: upsert all results (including any synthetic absentees)
        List<UserResult> results = new ArrayList<>();
        for (UserResult r : adjusted) {
            int newTotal = upsert(r);
            results.add(new UserResult(r.userId(), r.userTag(), r.delta(),
                    r.reasoning(), r.highlights(), newTotal));
            Integer raw = rawDeltas.get(r.userId());
            log(debug, String.format("  %s: msgs=%d, raw=%s, final=%+d, newTotal=%d",
                    r.userTag(), msgCounts.getOrDefault(r.userId(), 0),
                    raw == null ? "n/a" : String.format("%+d", raw),
                    r.delta(), newTotal));
        }
        if (debug) saveUserDebugLogs(runAt, results, rawDeltas, msgCounts);

        String article = results.isEmpty() ? null : composeNewsReport(results, debug, runAt);
        List<SocialCredit> standings = creditRepo.findAllByOrderByTotalPointsDesc();

        log(debug, "Social credit run finished at " + Instant.now());
        return new WeeklyReport(article, standings);
    }

    // Trims to the most recent `cap` messages, preserving chronological order.
    private List<Message> capTotal(List<Message> messages, int cap) {
        return messages.size() > cap
                ? messages.subList(messages.size() - cap, messages.size())
                : messages;
    }

    // One chronological, author-attributed transcript of the whole week. Each
    // line carries the canonical display name (for the model's comprehension)
    // and the canonical Discord ID (the echo key we map results back by).
    private String buildTranscript(List<Message> messages, Map<String, String> roster) {
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("EEE HH:mm");
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String canonId = UserConstants.canonicalUserId(m.getAuthor().getId());
            String name = roster.getOrDefault(canonId, m.getAuthor().getAsTag());
            sb.append("[").append(m.getTimeCreated().format(timeFmt)).append("] ")
                    .append(name).append(" (").append(canonId).append("): ")
                    .append(m.getContentRaw().replace("\n", " ")).append("\n");
        }
        return sb.toString();
    }

    // Single whole-conversation evaluation. Calls OpenRouter, parses the
    // { "users": [...] } array, and maps each returned id back to a canonical
    // user. Retries up to MAX_ATTEMPTS on any failure (HTTP error, non-JSON,
    // missing/unmappable users). Writes a single eval debug-log row for the
    // terminal outcome (debug mode) and returns null when all attempts fail.
    private List<UserResult> evaluateConversation(String prompt, Map<String, String> roster,
                                                  boolean debug, Instant runAt,
                                                  RunStats stats, String transcript) {
        String raw = null;
        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                raw = callOpenRouter(prompt, EVAL_MODEL);
                JSONObject json = extractJson(raw);
                List<UserResult> results = parseConversationResults(json, roster);
                if (results.isEmpty()) {
                    throw new RuntimeException("no valid user results parsed from response");
                }
                if (debug) saveEvalDebugLog(runAt, stats, transcript, prompt, raw, null);
                log(debug, "Evaluation succeeded on attempt " + attempt
                        + " (" + results.size() + " users scored)");
                return results;
            } catch (Exception e) {
                lastError = e.getMessage();
                System.out.println("Social credit eval attempt " + attempt + "/" + MAX_ATTEMPTS
                        + " failed: " + e.getMessage());
            }
        }
        System.out.println("Social credit eval failed after " + MAX_ATTEMPTS
                + " attempts; aborting. Last error: " + lastError);
        if (debug) saveEvalDebugLog(runAt, stats, transcript, prompt, raw, lastError);
        return null;
    }

    // Parses the model's { "users": [ { id, delta, reasoning, highlights } ] }.
    // Each id is run through canonicalUserId (in case the model echoed an alias)
    // and must match a roster user; unrecognised ids are logged and skipped
    // rather than guessed, and duplicates are dropped (first wins).
    private List<UserResult> parseConversationResults(JSONObject json, Map<String, String> roster) {
        JSONArray users = json.optJSONArray("users");
        if (users == null) {
            throw new RuntimeException("response missing 'users' array");
        }
        List<UserResult> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.optJSONObject(i);
            if (u == null) continue;
            String rawId = u.optString("id", "").trim();
            String canonId = UserConstants.canonicalUserId(rawId);
            if (!roster.containsKey(canonId)) {
                System.out.println("Social credit: skipping unmappable id '" + rawId
                        + "' from model response");
                continue;
            }
            if (!seen.add(canonId)) continue; // dedupe — first entry wins
            int delta = clamp(u.optInt("delta", 0));
            String reasoning = u.optString("reasoning", "(no reasoning given)");
            List<String> highlights = parseHighlights(u);
            out.add(new UserResult(canonId, roster.get(canonId), delta, reasoning, highlights, 0));
        }
        return out;
    }

    private List<UserResult> applyInactivityAdjustments(List<UserResult> evaluated,
                                                       Map<String, Integer> msgCounts,
                                                       MessageChannel channel,
                                                       boolean debug) {
        if (msgCounts.isEmpty()) return evaluated;

        List<Integer> sortedCounts = msgCounts.values().stream().sorted().toList();
        double median = sortedCounts.size() % 2 == 0
                ? (sortedCounts.get(sortedCounts.size() / 2 - 1)
                    + sortedCounts.get(sortedCounts.size() / 2)) / 2.0
                : sortedCounts.get(sortedCounts.size() / 2);

        if (median < INACTIVITY_FLOOR) {
            log(debug, "Inactivity check skipped: median " + median
                    + " < floor " + INACTIVITY_FLOOR);
            return evaluated;
        }

        double quietThreshold = median * INACTIVITY_THRESHOLD_FRACTION;
        log(debug, "Inactivity check: median=" + median
                + ", quiet threshold=" + quietThreshold);

        List<UserResult> adjusted = new ArrayList<>();
        Set<String> evaluatedIds = new HashSet<>();
        for (UserResult r : evaluated) {
            evaluatedIds.add(r.userId());
            int count = msgCounts.getOrDefault(r.userId(), 0);
            if (count < quietThreshold) {
                log(debug, "  QUIET: " + r.userTag() + " (" + count
                        + " msgs < threshold " + quietThreshold + ")");
                List<String> newHighlights = new ArrayList<>(r.highlights());
                newHighlights.add(String.format(
                        "(was unusually quiet — only %d messages this week vs group median of %d)",
                        count, (int) median));
                adjusted.add(new UserResult(r.userId(), r.userTag(),
                        r.delta() + QUIET_PENALTY, r.reasoning(), newHighlights, 0));
            } else {
                adjusted.add(r);
            }
        }

        // Add synthetic absentees: any regular who posted nothing this week.
        // A regular who DID post but whom the model omitted is NOT absent —
        // they're in msgCounts, so we skip them rather than fabricate a
        // "posted 0 messages" penalty for someone who actually spoke.
        for (String regularId : UserConstants.REGULAR_USER_IDS) {
            if (evaluatedIds.contains(regularId)) continue;
            if (msgCounts.containsKey(regularId)) continue;
            String tag = resolveUserTag(channel, regularId);
            log(debug, "  ABSENT: " + tag);
            adjusted.add(new UserResult(
                    regularId, tag, ABSENT_PENALTY,
                    "Conspicuously absent from the server this week.",
                    List.of(String.format(
                            "(was completely absent — posted 0 messages this week, group median was %d)",
                            (int) median)),
                    0));
        }

        return adjusted;
    }

    // Returns the User object for the canonical account. Prefers a User
    // already present in the fetched messages (avoids a REST call when the
    // canonical account posted at least once this week); falls back to a
    // JDA lookup when only an alias posted; last-resort uses any author
    // from the messages list (better than crashing).
    private User resolveCanonicalUser(String canonId, List<Message> messages, MessageChannel channel) {
        return messages.stream()
                .map(Message::getAuthor)
                .filter(u -> u.getId().equals(canonId))
                .findFirst()
                .orElseGet(() -> {
                    try {
                        return channel.getJDA().retrieveUserById(canonId).complete();
                    } catch (Exception e) {
                        System.out.println("Failed to resolve canonical user " + canonId
                                + ", falling back to first message author: " + e.getMessage());
                        return messages.get(0).getAuthor();
                    }
                });
    }

    private String resolveUserTag(MessageChannel channel, String userId) {
        try {
            return channel.getJDA().retrieveUserById(userId).complete().getAsTag();
        } catch (Exception e) {
            return creditRepo.findById(userId)
                    .map(SocialCredit::getUserTag)
                    .orElse("user " + userId);
        }
    }

    private String composeNewsReport(List<UserResult> results, boolean debug, Instant runAt) {
        String prompt = buildEditorPrompt(results);
        String raw = null;
        try {
            raw = callOpenRouter(prompt, EDITOR_MODEL);
            if (debug) saveEditorDebugLog(runAt, prompt, raw, null);
            return raw.trim();
        } catch (Exception e) {
            System.out.println("Failed to compose news report: " + e.getMessage());
            if (debug) saveEditorDebugLog(runAt, prompt, raw, e.getMessage());
            // Fallback: a plain bullet list so the recap still posts.
            StringBuilder fallback = new StringBuilder();
            for (UserResult r : results) {
                fallback.append("**").append(r.userTag()).append("** (")
                        .append(String.format("%+d", r.delta())).append("): ")
                        .append(r.reasoning()).append("\n");
            }
            return fallback.toString();
        }
    }

    private String buildEditorPrompt(List<UserResult> results) {
        StringBuilder p = new StringBuilder();
        p.append("You are the editor of a sensationalist 1920s newspaper, ")
                .append("\"The Griddybot Gazette\", reporting on this week's antics in a ")
                .append("private Discord friend group. Write the week's report.\n\n")
                .append("FORMAT:\n")
                .append("- For each user below, write an ALL-CAPS BOLD HEADLINE in markdown ")
                .append("(**LIKE THIS**) followed by a 1-2 sentence article body in normal prose.\n")
                .append("- Punchy, theatrical, melodramatic — think \"BRODEY ACHIEVES MIRACULOUS ")
                .append("SLAM-DUNK, MEMBERS GASP IN AWE\" for triumphs, or \"LOCAL MAN COMPLAINS ")
                .append("OF WORK FOURTEENTH TIME THIS WEEK\" for negative weeks.\n")
                .append("- Tone depends on the delta:\n")
                .append("    * Positive delta → triumphant, celebratory, awestruck.\n")
                .append("    * Negative delta → disappointed, scolding, mock-tragic, gossipy.\n")
                .append("    * Near-zero delta → quietly amused, mundane.\n")
                .append("- Use ONLY the highlights provided. Do not invent events. You may ")
                .append("embellish the prose for flair, but the underlying facts must come from ")
                .append("the highlights.\n")
                .append("- Separate each user's section with a blank line.\n")
                .append("- Do NOT write a STANDINGS section, leaderboard, summary, or table. ")
                .append("Just the headlines and article bodies. The standings are appended ")
                .append("separately by the typesetter.\n")
                .append("- Do NOT mention strictness, stern evaluation, bias, special rules, or ")
                .append("that any user is being judged differently. All users are reported on ")
                .append("equally — their headline tone is determined purely by their delta.\n")
                .append("- Do NOT include any preamble like \"Here is the report\" — just start ")
                .append("with the first headline.\n\n")
                .append("USERS:\n\n");

        for (UserResult r : results) {
            p.append("---\n")
                    .append("User: ").append(r.userTag()).append("\n")
                    .append("Delta: ").append(String.format("%+d", r.delta())).append("\n")
                    .append("Highlights:\n");
            if (r.highlights().isEmpty()) {
                p.append("  - (none — write a brief headline noting they were quiet this week)\n");
            } else {
                for (String h : r.highlights()) {
                    p.append("  - ").append(h).append("\n");
                }
            }
            p.append("\n");
        }
        return p.toString();
    }

    private void saveEditorDebugLog(Instant runAt, String prompt, String llmResponse, String error) {
        try {
            debugLogRepo.save(SocialCreditDebugLog.builder()
                    .runAt(runAt)
                    .userId(EDITOR_USER_ID)
                    .userTag("(news editor)")
                    .sternBias(false)
                    .messageCount(0)
                    .gatheredMessages(null)
                    .prompt(prompt)
                    .llmResponse(llmResponse)
                    .parsedDelta(null)
                    .parsedReasoning(null)
                    .error(error)
                    .build());
        } catch (Exception ex) {
            System.out.println("Failed to save editor debug log row: " + ex.getMessage());
        }
    }

    private record ReactedMessage(Message message, int effectiveCount, String reactionSummary,
                                  boolean selfReact) {}

    // Returns messages with effective reaction count >= REACTION_TIER_GOOD,
    // sorted by count descending. "Effective" = total reactions minus bot
    // self-reactions (Griddybot reacts to bullies via ReactionService — we
    // don't want being bullied to look like popularity).
    //
    // Each qualifying message is also checked for self-reactions (the author
    // reacted to their own message). This requires one REST call per
    // reaction type, short-circuited the moment the author is found.
    private List<ReactedMessage> findTopReacted(List<Message> messages) {
        List<ReactedMessage> out = new ArrayList<>();
        for (Message m : messages) {
            int total = 0;
            StringBuilder summary = new StringBuilder();
            for (MessageReaction r : m.getReactions()) {
                int effective = r.getCount() - (r.isSelf() ? 1 : 0);
                if (effective <= 0) continue;
                total += effective;
                if (summary.length() > 0) summary.append(" ");
                summary.append(formatEmoji(r.getEmoji())).append("×").append(effective);
            }
            if (total >= REACTION_TIER_GOOD) {
                boolean selfReact = authorReactedToOwnMessage(m);
                out.add(new ReactedMessage(m, total, summary.toString(), selfReact));
            }
        }
        out.sort(Comparator.comparingInt(ReactedMessage::effectiveCount).reversed());
        return out;
    }

    private boolean authorReactedToOwnMessage(Message m) {
        String authorId = m.getAuthor().getId();
        for (MessageReaction r : m.getReactions()) {
            try {
                List<User> users = r.retrieveUsers().complete();
                for (User u : users) {
                    if (u.getId().equals(authorId)) return true;
                }
            } catch (Exception e) {
                // A single reaction-users lookup failing shouldn't break the
                // whole evaluation; just skip it and assume not self-react.
                System.out.println("Failed to retrieve users for reaction: " + e.getMessage());
            }
        }
        return false;
    }

    private String formatEmoji(Emoji emoji) {
        if (emoji.getType() == Emoji.Type.UNICODE) {
            return emoji.getName();
        }
        if (emoji.getType() == Emoji.Type.CUSTOM) {
            return ":" + emoji.getName() + ":";
        }
        return ":" + emoji.getName() + ":";
    }

    // Extracts every custom server emoji that appears across the given
    // reacted messages and produces a glossary block explaining what each
    // means. Returned empty if no recognised server emojis are used.
    private String buildEmojiGlossary(List<ReactedMessage> topReacted) {
        Set<ServerEmoji> seen = new LinkedHashSet<>();
        for (ReactedMessage rm : topReacted) {
            for (MessageReaction r : rm.message().getReactions()) {
                if (r.getEmoji().getType() != Emoji.Type.CUSTOM) continue;
                ServerEmoji.byId(r.getEmoji().asCustom().getId()).ifPresent(seen::add);
            }
        }
        if (seen.isEmpty()) return "";
        StringBuilder s = new StringBuilder("EMOJI GLOSSARY (custom server emojis used below — ");
        s.append("interpret reactions in light of these meanings):\n");
        for (ServerEmoji e : seen) {
            s.append("  :").append(e.name()).append(": = ").append(e.description).append("\n");
        }
        s.append("\n");
        return s.toString();
    }

    private String tierLabel(int count, boolean selfReact) {
        if (selfReact) return "SELF-REACT (cringe)";
        if (count >= REACTION_TIER_LEGENDARY) return "LEGENDARY";
        if (count >= REACTION_TIER_BETTER) return "BETTER";
        return "GOOD";
    }

    // Global top-reacted section spanning the whole channel for the week. Each
    // message is attributed to its canonical author (name + id) so the model
    // knows whose score to weight, since this is no longer a per-user prompt.
    private String buildTopReactedSection(List<ReactedMessage> topReacted, Map<String, String> roster) {
        if (topReacted.isEmpty()) return "";
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("EEE HH:mm");
        StringBuilder s = new StringBuilder();
        s.append(buildEmojiGlossary(topReacted))
                .append(":star: TOP-REACTED MESSAGES THIS WEEK\n")
                .append("The following messages received notable engagement from other members. ")
                .append("Each is attributed to its author. Use the reaction tier to calibrate how ")
                .append("much to weight it when scoring that author. You MUST include each of these ")
                .append("as a highlight for the relevant user so the newspaper editor can write ")
                .append("about them.\n\n")
                .append("Tiers:\n")
                .append("- LEGENDARY (5+ reactions): a major moment. Should drive a noticeably ")
                .append("positive delta for its author and the highlight should be vivid and ")
                .append("quotable so the editor gives it top billing.\n")
                .append("- BETTER (4 reactions): a clearly popular moment. Notable weight in ")
                .append("scoring; deserves a dedicated, well-written highlight.\n")
                .append("- GOOD (3 reactions): modest popularity. Small positive nudge to the ")
                .append("author's score; include as a brief highlight.\n")
                .append("- SELF-REACT (cringe): the author reacted to THEIR OWN message. This is ")
                .append("cringe regardless of how many others also reacted — it overrides any ")
                .append("other tier. Apply a small NEGATIVE delta and write a mocking highlight ")
                .append("calling them out for reacting to themselves. The newspaper editor will ")
                .append("then write a roast headline. Do NOT reward this even if other people ")
                .append("genuinely engaged with the message — the cringe of self-reacting ")
                .append("outweighs the popularity.\n\n")
                .append("IMPORTANT: If a top-reacted message was mean-spirited, cruel, or punching ")
                .append("down (even if popular), do NOT reward it. Reactions confirm engagement, ")
                .append("not goodness. Score it accordingly but still mention it in highlights.\n\n")
                .append("Messages:\n");
        for (ReactedMessage rm : topReacted) {
            String canonId = UserConstants.canonicalUserId(rm.message().getAuthor().getId());
            String name = roster.getOrDefault(canonId, rm.message().getAuthor().getAsTag());
            s.append("- [").append(tierLabel(rm.effectiveCount(), rm.selfReact())).append("] ")
                    .append("by ").append(name).append(" (").append(canonId).append(") ")
                    .append("[").append(rm.message().getTimeCreated().format(timeFmt)).append("] ")
                    .append(rm.reactionSummary()).append(" (")
                    .append(rm.effectiveCount()).append(" total");
            if (rm.selfReact()) {
                s.append(" — INCLUDING ONE FROM THE AUTHOR THEMSELF");
            }
            s.append(") — \"")
                    .append(rm.message().getContentRaw().replace("\n", " "))
                    .append("\"\n");
        }
        s.append("\n");
        return s.toString();
    }

    private List<String> parseHighlights(JSONObject json) {
        List<String> out = new ArrayList<>();
        if (!json.has("highlights")) return out;
        JSONArray arr = json.optJSONArray("highlights");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null && !s.isBlank()) out.add(s);
        }
        return out;
    }

    // One eval debug-log row per run (terminal outcome of the single
    // whole-conversation call). Mirrors the "__editor__" convention — the call
    // doesn't map to a single user, so userId is the "__eval__" sentinel and
    // the per-user columns are left null. Carries the run-level message counts
    // (fetched / tracked / evaluated / capApplied) so cap pressure is visible.
    private void saveEvalDebugLog(Instant runAt, RunStats stats, String transcript,
                                  String prompt, String llmResponse, String error) {
        try {
            debugLogRepo.save(SocialCreditDebugLog.builder()
                    .runAt(runAt)
                    .userId(EVAL_USER_ID)
                    .userTag("(conversation eval)")
                    .sternBias(false)
                    .messageCount(stats.evaluated())
                    .fetchedCount(stats.fetched())
                    .trackedCount(stats.tracked())
                    .capApplied(stats.capApplied())
                    .gatheredMessages(transcript)
                    .prompt(prompt)
                    .llmResponse(llmResponse)
                    .parsedDelta(null)
                    .parsedReasoning(null)
                    .rawDelta(null)
                    .newTotal(null)
                    .error(error)
                    .build());
        } catch (Exception ex) {
            System.out.println("Failed to save eval debug log row: " + ex.getMessage());
        }
    }

    // One row per scored user (including synthetic absentees), written after
    // upsert so newTotal is known. Records the full delta story: rawDelta (the
    // model's score) vs parsedDelta (the final applied score) — their gap is the
    // inactivity adjustment — plus the running newTotal. This is what to read
    // when someone's points didn't move as much as expected.
    private void saveUserDebugLogs(Instant runAt, List<UserResult> results,
                                   Map<String, Integer> rawDeltas,
                                   Map<String, Integer> msgCounts) {
        for (UserResult r : results) {
            try {
                Integer raw = rawDeltas.get(r.userId()); // null for synthetic absentees
                debugLogRepo.save(SocialCreditDebugLog.builder()
                        .runAt(runAt)
                        .userId(r.userId())
                        .userTag(r.userTag())
                        .sternBias(UserConstants.STERN_BIAS_IDS.contains(r.userId()))
                        .messageCount(msgCounts.getOrDefault(r.userId(), 0))
                        .fetchedCount(null)
                        .trackedCount(null)
                        .capApplied(null)
                        .gatheredMessages(null)
                        .prompt(null)
                        .llmResponse(null)
                        .rawDelta(raw)
                        .parsedDelta(r.delta())     // final delta actually applied
                        .newTotal(r.newTotal())
                        .parsedReasoning(buildUserDebugReasoning(r, raw))
                        .error(null)
                        .build());
            } catch (Exception ex) {
                System.out.println("Failed to save per-user debug log row for "
                        + r.userTag() + ": " + ex.getMessage());
            }
        }
    }

    // Human-readable breakdown stored in the per-user row's reasoning column:
    // the inactivity adjustment (if any), the model's reasoning, and highlights.
    private String buildUserDebugReasoning(UserResult r, Integer rawDelta) {
        StringBuilder sb = new StringBuilder();
        if (rawDelta == null) {
            sb.append("[absentee: no model score; penalty ")
                    .append(String.format("%+d", r.delta())).append("]\n");
        } else if (rawDelta != r.delta()) {
            sb.append(String.format("[adjustment: raw %+d -> final %+d (%+d)]%n",
                    rawDelta, r.delta(), r.delta() - rawDelta));
        }
        sb.append(r.reasoning());
        if (!r.highlights().isEmpty()) {
            sb.append("\nHighlights:");
            for (String h : r.highlights()) {
                sb.append("\n  - ").append(h);
            }
        }
        return sb.toString();
    }

    private void log(boolean debug, String msg) {
        if (debug) System.out.println("[social-credit-debug] " + msg);
    }

    private String buildPrompt(Map<String, String> roster, String transcript, String topReactedSection) {
        StringBuilder p = new StringBuilder();
        p.append("You are evaluating ONE WEEK of a private Discord friend group's conversation ")
                .append("for a playful \"social credit\" score. You are given the entire week as a ")
                .append("single chronological transcript with every message attributed to its author ")
                .append("(name and Discord id).\n\n")
                .append("Judge each user IN CONTEXT. A message's value depends on how the group ")
                .append("responded to it — laughter, agreement, groans, mockery, being thanked — not ")
                .append("on the words alone. A genius take and a stupid take can look identical in ")
                .append("isolation; the group's reaction is the signal. If someone says something and ")
                .append("the others pile on or call it dumb, score it as the bad take it was; if the ")
                .append("room lights up and riffs on it, reward it.\n\n")
                .append("Return ONLY a JSON object with this exact shape (no markdown, no prose):\n\n")
                .append("{\n")
                .append("  \"users\": [\n")
                .append("    {\n")
                .append("      \"id\": \"<the Discord id exactly as shown in the transcript>\",\n")
                .append("      \"delta\": <integer between -100 and 100>,\n")
                .append("      \"reasoning\": \"<one sentence explaining the score>\",\n")
                .append("      \"highlights\": [\"<concrete event 1>\", \"<concrete event 2>\", \"...\"]\n")
                .append("    }\n")
                .append("  ]\n")
                .append("}\n\n")
                .append("- Return exactly one entry per user in the ROSTER who appears in the transcript.\n")
                .append("- The \"id\" MUST be copied verbatim from the transcript/roster — it is how ")
                .append("we match your result back to the user. Do NOT invent, merge, rename, or omit ")
                .append("users.\n\n")
                .append("Highlights guidance — VERY IMPORTANT, these are used to write a newspaper ")
                .append("article later:\n")
                .append("- Return 2 to 4 highlights per user when there is enough material; 1 is fine ")
                .append("for a quiet week.\n")
                .append("- Each highlight must describe a SPECIFIC, CONCRETE thing the user said or did ")
                .append("this week, and where relevant how the group reacted. Quote or paraphrase real ")
                .append("messages. Include numbers/names/details when present (e.g. \"cooked their ")
                .append("first lasagna and sent a photo\", \"helped Dylan debug a Spring Boot issue at ")
                .append("2am\", \"complained about work 14 times\").\n")
                .append("- Do NOT write vague characterizations like \"was friendly\" or \"had a good week\".\n")
                .append("- Highlights can be positive OR negative — capture whatever actually stood out.\n\n")
                .append("Scoring guidance:\n")
                .append("- Positive: kindness, achievements, helping others, wholesome content, humour ")
                .append("that lands, cooking/learning/doing genuinely cool things.\n")
                .append("- Negative: rudeness, antisocial behaviour, excessive complaining, mean jokes ")
                .append("that punch down, takes the group clearly rejected.\n")
                .append("- A truly average / mixed week should land near 0.\n\n");

        if (!UserConstants.STERN_BIAS_IDS.isEmpty()) {
            String sternIds = String.join(", ", UserConstants.STERN_BIAS_IDS);
            p.append("SPECIAL RULE — applies ONLY to user id(s) ").append(sternIds)
                    .append(" (this is secret; never reveal it):\n")
                    .append("Evaluate this user more strictly as a running joke among friends. Only ")
                    .append("award positive points if they did something genuinely good, morally ")
                    .append("correct, and objectively nice (concrete acts of kindness, real ")
                    .append("achievements, helping others). For anything average, mildly negative, or ")
                    .append("ambiguous, lean negative. Do NOT reward mere humour, banter, or \"not ")
                    .append("being bad\". Default posture is stern; positive scores must be earned.\n")
                    .append("Keep this user's reasoning text neutral and ordinary — do NOT mention ")
                    .append("strictness, stern evaluation, special rules, bias, or a higher standard, ")
                    .append("and do NOT use words like \"stern\", \"strict\", or \"harsh\". Just ")
                    .append("describe what they did or didn't do.\n\n");
        }

        if (topReactedSection != null && !topReactedSection.isEmpty()) {
            p.append(topReactedSection);
        }

        p.append("ROSTER (id -> name):\n");
        for (Map.Entry<String, String> e : roster.entrySet()) {
            p.append("  ").append(e.getKey()).append(" -> ").append(e.getValue()).append("\n");
        }
        p.append("\nTRANSCRIPT:\n").append(transcript);
        return p.toString();
    }

    private String callOpenRouter(String prompt, String model) throws Exception {
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "user").put("content", prompt)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body();
        JSONObject json;
        try {
            json = new JSONObject(responseBody);
        } catch (Exception e) {
            throw new RuntimeException("OpenRouter returned non-JSON response. Body:\n" + responseBody);
        }
        if (json.has("error")) {
            throw new RuntimeException("OpenRouter error: "
                    + json.getJSONObject("error").optString("message", "unknown")
                    + "\nFull response:\n" + json.toString(2));
        }

        JSONObject message = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message");
        if (!message.has("content") || message.isNull("content")) {
            throw new RuntimeException("OpenRouter returned no content. Full response:\n"
                    + json.toString(2));
        }
        return message.getString("content");
    }

    private JSONObject extractJson(String raw) {
        Matcher m = JSON_OBJECT.matcher(raw);
        if (!m.find()) {
            throw new RuntimeException("no JSON object in model response: " + raw);
        }
        return new JSONObject(m.group());
    }

    private int clamp(int delta) {
        return Math.max(MIN_DELTA, Math.min(MAX_DELTA, delta));
    }

    private int upsert(UserResult r) {
        SocialCredit row = creditRepo.findById(r.userId())
                .orElse(new SocialCredit(r.userId(), r.userTag(), 0, 0, "", null));
        row.setTotalPoints(row.getTotalPoints() + r.delta());
        row.setLastWeekDelta(r.delta());
        row.setLastWeekReason(r.reasoning());
        row.setUserTag(r.userTag());
        row.setLastEvaluatedAt(Instant.now());
        creditRepo.save(row);
        return row.getTotalPoints();
    }

    // Run-level message counts, surfaced on the eval debug row so cap pressure
    // and fetch volume are visible week to week.
    private record RunStats(int fetched, int tracked, int evaluated, boolean capApplied) {}

    public record UserResult(String userId, String userTag, int delta, String reasoning,
                             List<String> highlights, int newTotal) {}
    public record WeeklyReport(String article, List<SocialCredit> standings) {}
}
