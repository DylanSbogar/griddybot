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
    private static final int MAX_MESSAGES_PER_USER = 500;
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

        // In production, only regulars are tracked. In debug mode the friend
        // is testing in his own channel without the production roster, so we
        // evaluate anyone who posts.
        //
        // Messages from alias accounts (e.g. Matt's alt) are attributed to
        // the canonical user — they appear in the canonical user's evaluation
        // and message count, and the alias never gets its own scoreline.
        Map<String, List<Message>> byCanonicalId = weekMessages.stream()
                .filter(m -> !m.getAuthor().isBot())
                .filter(m -> !m.getContentRaw().isBlank())
                .filter(m -> {
                    if (debug) return true;
                    String canonId = UserConstants.canonicalUserId(m.getAuthor().getId());
                    return UserConstants.REGULAR_USER_IDS.contains(canonId);
                })
                .collect(Collectors.groupingBy(
                        m -> UserConstants.canonicalUserId(m.getAuthor().getId())));

        log(debug, "Active users this window: " + byCanonicalId.size());

        // Phase 1: per-user LLM eval (no DB writes yet)
        List<UserResult> preAdjust = new ArrayList<>();
        Map<String, Integer> msgCounts = new HashMap<>();
        for (Map.Entry<String, List<Message>> entry : byCanonicalId.entrySet()) {
            String canonId = entry.getKey();
            List<Message> messages = entry.getValue();
            msgCounts.put(canonId, messages.size());
            User canonicalUser = resolveCanonicalUser(canonId, messages, channel);
            UserResult result = evaluateUser(canonicalUser, messages, debug, runAt);
            if (result != null) preAdjust.add(result);
        }

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
            log(debug, String.format("  %s: delta=%+d, newTotal=%d",
                    r.userTag(), r.delta(), newTotal));
        }

        String article = results.isEmpty() ? null : composeNewsReport(results, debug, runAt);
        List<SocialCredit> standings = creditRepo.findAllByOrderByTotalPointsDesc();

        log(debug, "Social credit run finished at " + Instant.now());
        return new WeeklyReport(article, standings);
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
        for (String regularId : UserConstants.REGULAR_USER_IDS) {
            if (evaluatedIds.contains(regularId)) continue;
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

    private UserResult evaluateUser(User user, List<Message> messages, boolean debug, Instant runAt) {
        List<Message> capped = messages.size() > MAX_MESSAGES_PER_USER
                ? messages.subList(messages.size() - MAX_MESSAGES_PER_USER, messages.size())
                : messages;

        capped.sort(Comparator.comparing(Message::getTimeCreated));
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("EEE HH:mm");
        StringBuilder convo = new StringBuilder();
        for (Message m : capped) {
            convo.append("[").append(m.getTimeCreated().format(timeFmt)).append("] ")
                    .append(m.getContentRaw()).append("\n");
        }

        boolean stern = UserConstants.STERN_BIAS_IDS.contains(user.getId());
        String topReactedSection = buildTopReactedSection(findTopReacted(capped));
        String prompt = buildPrompt(user.getAsTag(), convo.toString(), topReactedSection, stern);

        String raw = null;
        try {
            raw = callOpenRouter(prompt, EVAL_MODEL);
            JSONObject json = extractJson(raw);
            int delta = clamp(json.getInt("delta"));
            String reasoning = json.optString("reasoning", "(no reasoning given)");
            List<String> highlights = parseHighlights(json);
            if (debug) {
                saveDebugLog(runAt, user, stern, capped.size(), convo.toString(), prompt, raw,
                        delta, reasoning, null);
            }
            return new UserResult(user.getId(), user.getAsTag(), delta, reasoning, highlights, 0);
        } catch (Exception e) {
            System.out.println("Failed to evaluate " + user.getAsTag() + ": " + e.getMessage());
            if (debug) {
                saveDebugLog(runAt, user, stern, capped.size(), convo.toString(), prompt, raw,
                        null, null, e.getMessage());
            }
            return null;
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

    private String buildTopReactedSection(List<ReactedMessage> topReacted) {
        if (topReacted.isEmpty()) return "";
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("EEE HH:mm");
        StringBuilder s = new StringBuilder();
        s.append(buildEmojiGlossary(topReacted))
                .append(":star: TOP-REACTED MESSAGES THIS WEEK\n")
                .append("The following messages from this user received notable engagement from ")
                .append("other members. Use the reaction tier to calibrate how much to weight ")
                .append("each one when scoring. You MUST include each of these messages as a ")
                .append("highlight in your response so the newspaper editor can write about them.\n\n")
                .append("Tiers:\n")
                .append("- LEGENDARY (5+ reactions): a major moment. Should drive a noticeably ")
                .append("positive delta and the highlight should be vivid and quotable so the ")
                .append("editor gives it top billing.\n")
                .append("- BETTER (4 reactions): a clearly popular moment. Notable weight in ")
                .append("scoring; deserves a dedicated, well-written highlight.\n")
                .append("- GOOD (3 reactions): modest popularity. Small positive nudge to the ")
                .append("score; include as a brief highlight.\n")
                .append("- SELF-REACT (cringe): the user reacted to THEIR OWN message. This is ")
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
            s.append("- [").append(tierLabel(rm.effectiveCount(), rm.selfReact())).append("] ")
                    .append("[").append(rm.message().getTimeCreated().format(timeFmt)).append("] ")
                    .append(rm.reactionSummary()).append(" (")
                    .append(rm.effectiveCount()).append(" total");
            if (rm.selfReact()) {
                s.append(" — INCLUDING ONE FROM THE USER THEMSELF");
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

    private void saveDebugLog(Instant runAt, User user, boolean stern, int messageCount,
                              String gathered, String prompt, String llmResponse,
                              Integer delta, String reasoning, String error) {
        try {
            debugLogRepo.save(SocialCreditDebugLog.builder()
                    .runAt(runAt)
                    .userId(user.getId())
                    .userTag(user.getAsTag())
                    .sternBias(stern)
                    .messageCount(messageCount)
                    .gatheredMessages(gathered)
                    .prompt(prompt)
                    .llmResponse(llmResponse)
                    .parsedDelta(delta)
                    .parsedReasoning(reasoning)
                    .error(error)
                    .build());
        } catch (Exception ex) {
            System.out.println("Failed to save debug log row: " + ex.getMessage());
        }
    }

    private void log(boolean debug, String msg) {
        if (debug) System.out.println("[social-credit-debug] " + msg);
    }

    private String buildPrompt(String userTag, String messages, String topReactedSection, boolean stern) {
        StringBuilder p = new StringBuilder();
        p.append("You are evaluating one week of Discord messages from a single user for a ")
                .append("playful \"social credit\" score among friends. Read the messages and return ")
                .append("ONLY a JSON object with this exact shape (no markdown, no prose):\n\n")
                .append("{\n")
                .append("  \"delta\": <integer between -100 and 100>,\n")
                .append("  \"reasoning\": \"<one sentence explaining the score>\",\n")
                .append("  \"highlights\": [\"<concrete event 1>\", \"<concrete event 2>\", \"...\"]\n")
                .append("}\n\n")
                .append("Highlights guidance — VERY IMPORTANT, these are used to write a newspaper ")
                .append("article later:\n")
                .append("- Return 2 to 4 highlights when there is enough material; 1 is fine for a quiet week.\n")
                .append("- Each highlight must describe a SPECIFIC, CONCRETE thing the user said or did ")
                .append("this week. Quote or paraphrase real messages. Include numbers/names/details ")
                .append("when present (e.g. \"cooked their first lasagna and sent a photo\", ")
                .append("\"helped Dylan debug a Spring Boot issue at 2am\", \"complained about work 14 times\").\n")
                .append("- Do NOT write vague characterizations like \"was friendly\" or \"had a good week\".\n")
                .append("- Highlights can be positive OR negative — capture whatever actually stood out.\n\n")
                .append("Scoring guidance:\n")
                .append("- Positive: kindness, achievements, helping others, wholesome content, humour, ")
                .append("cooking/learning/doing genuinely cool things.\n")
                .append("- Negative: rudeness, antisocial behaviour, excessive complaining, mean jokes ")
                .append("that punch down.\n")
                .append("- A truly average / mixed week should land near 0.\n\n");

        if (stern) {
            p.append("SPECIAL RULE FOR THIS USER:\n")
                    .append("This user is being evaluated more strictly as a running joke among friends. ")
                    .append("Only award positive points if they did something genuinely good, morally ")
                    .append("correct, and objectively nice (e.g. concrete acts of kindness, real ")
                    .append("achievements, helping others). For anything average, mildly negative, or ")
                    .append("ambiguous, lean negative. Do NOT reward mere humour, banter, or \"not being ")
                    .append("bad\". Default posture is stern; positive scores must be earned.\n\n")
                    .append("IMPORTANT: This special rule is a secret. The user must not know they are ")
                    .append("being judged more strictly. Your reasoning text must read like a normal, ")
                    .append("neutral evaluation of their week. Do NOT mention strictness, stern ")
                    .append("evaluation, special rules, bias, or that they are being held to a higher ")
                    .append("standard. Do NOT use words like \"stern\", \"strict\", \"harsh\", or ")
                    .append("\"special\" in the reasoning. Just describe what they did or didn't do.\n\n");
        }

        if (topReactedSection != null && !topReactedSection.isEmpty()) {
            p.append(topReactedSection);
        }

        p.append("User: ").append(userTag).append("\n")
                .append("Messages this week:\n")
                .append(messages);
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

    public record UserResult(String userId, String userTag, int delta, String reasoning,
                             List<String> highlights, int newTotal) {}
    public record WeeklyReport(String article, List<SocialCredit> standings) {}
}
