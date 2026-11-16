package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.entities.DailyResult;
import com.dylansbogar.griddybot.utils.DailyService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class DailyCommand extends ListenerAdapter {
    private static final int MESSAGE_LIMIT = 1900;
    private final DailyService dailyService;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!List.of("daily", "brag", "registerdaily", "unregisterdaily", "addmydaily", "removemydaily",
                        "dailies", "d", "randomdaily", "listalldailies")
                .contains(event.getName())) return;

        event.deferReply(true).queue();
        try {
            String userId = event.getUser().getId();
            List<String> replies = switch (event.getName()) {
                case "daily" -> {
                    String result = option(event, "result");
                    DailyService.SaveOutcome outcome = dailyService.saveResult(userId, result);
                    String confirmation = (outcome.replaced() ? "Updated" : "Saved") + " your " + outcome.game().getName()
                            + " result for " + outcome.date() + "."
                            + (outcome.addedToList() ? " Added it to your daily list." : "");
                    postPublic(event, formatDaily(outcome.game(), event.getUser().getAsMention(), result), 0,
                            confirmation);
                    yield List.of();
                }
                case "brag" -> {
                    List<DailyResult> results = dailyService.brag(userId, option(event, "name"));
                    postPublic(event, formatBrag(results, event.getUser().getName()), 0,
                            "Posted " + results.size() + " daily result" + (results.size() == 1 ? "" : "s") + ".");
                    yield List.of();
                }
                case "registerdaily" -> {
                    DailyGame game = dailyService.register(option(event, "name"), option(event, "url"),
                            option(event, "resultexample"));
                    String announcement = "Registered " + game.getName() + ". Use /addmydaily name:" + game.getName()
                            + " to put it on your list.";
                    postPublic(event, List.of(announcement), 0, "Registered " + game.getName() + ".");
                    yield List.of();
                }
                case "unregisterdaily" -> {
                    DailyGame game = dailyService.unregister(option(event, "name"));
                    postPublic(event, List.of("Unregistered " + game.getName() + ". Saved results were kept."), 0,
                            "Unregistered " + game.getName() + ".");
                    yield List.of();
                }
                case "addmydaily" -> List.of("Added " + dailyService.add(userId, option(event, "name")).getName()
                        + " to your daily list.");
                case "removemydaily" -> List.of("Removed " + dailyService.remove(userId, option(event, "name")).getName()
                        + " from your daily list.");
                case "dailies", "d" -> formatList(dailyService.list(userId));
                case "randomdaily" -> {
                    DailyGame game = dailyService.randomPending(userId);
                    yield List.of("Try " + game.getName() + ": <" + game.getUrl() + ">");
                }
                case "listalldailies" -> formatAvailable(dailyService.availableGames());
                default -> throw new IllegalStateException("Unexpected daily command");
            };
            if (!replies.isEmpty()) sendReplies(event, replies, 0);
        } catch (IllegalArgumentException e) {
            sendReplies(event, List.of(e.getMessage()), 0);
        }
    }

    private static void postPublic(SlashCommandInteractionEvent event, List<String> messages, int index,
                                   String confirmation) {
        event.getChannel().sendMessage(messages.get(index)).setAllowedMentions(List.of())
                .setSuppressEmbeds(true).queue(sent -> {
            if (index + 1 < messages.size()) postPublic(event, messages, index + 1, confirmation);
            else sendReplies(event, List.of(confirmation), 0);
        }, error -> sendReplies(event,
                List.of("Could not finish posting here. Check the channel before retrying."), 0));
    }

    private static void sendReplies(SlashCommandInteractionEvent event, List<String> replies, int index) {
        event.getHook().sendMessage(replies.get(index)).setAllowedMentions(List.of()).setEphemeral(true)
                .queue(sent -> {
                    if (index + 1 < replies.size()) sendReplies(event, replies, index + 1);
                });
    }

    private static String option(SlashCommandInteractionEvent event, String name) {
        var option = event.getOption(name);
        return option == null ? "" : option.getAsString();
    }

    static List<String> formatList(DailyService.DailyList list) {
        if (list.pending().isEmpty() && list.completed().isEmpty()) {
            return List.of("Your daily list is empty. Use /listalldailies and /addmydaily to choose games.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Your dailies for " + list.date() + ":");
        if (list.pending().isEmpty()) {
            lines.add("All done today!");
        } else {
            lines.add("Still to do:");
            for (DailyGame game : list.pending()) {
                lines.add("• " + game.getName() + " — <" + game.getUrl() + ">");
            }
        }
        if (!list.completed().isEmpty()) {
            lines.add("Done:");
            for (DailyGame game : list.completed()) lines.add("• " + game.getName());
        }
        return chunkLines(lines);
    }

    static List<String> formatBrag(List<DailyResult> results, String userName) {
        List<String> messages = new ArrayList<>();
        String cleanUserName = userName.replaceAll("[\\r\\n]", " ");
        for (DailyResult result : results) {
            String post = "**" + result.getGame().getName() + " — " + cleanUserName + "**\n" + result.getResultText();
            messages.addAll(chunkPost(post));
        }
        return messages;
    }

    static List<String> formatDaily(DailyGame game, String userMention, String result) {
        return chunkPost(userMention + " just played **" + game.getName() + "**\n" + result.trim());
    }

    private static List<String> chunkPost(String post) {
        List<String> messages = new ArrayList<>();
        int start = 0;
        while (start < post.length()) {
            int end = Math.min(start + MESSAGE_LIMIT, post.length());
            if (end < post.length() && Character.isHighSurrogate(post.charAt(end - 1))) end--;
            messages.add(post.substring(start, end));
            start = end;
        }
        return messages;
    }

    static List<String> formatAvailable(List<DailyGame> games) {
        List<String> lines = new ArrayList<>();
        lines.add("Registered dailies:");
        for (DailyGame game : games) {
            lines.add("• " + game.getName() + " — <" + game.getUrl() + ">");
        }
        return chunkLines(lines);
    }

    private static List<String> chunkLines(List<String> lines) {
        List<String> messages = new ArrayList<>();
        StringBuilder message = new StringBuilder();
        for (String line : lines) {
            if (message.length() + line.length() + (message.isEmpty() ? 0 : 1) > MESSAGE_LIMIT) {
                messages.add(message.toString());
                message.setLength(0);
            }
            if (!message.isEmpty()) message.append('\n');
            message.append(line);
        }
        if (!message.isEmpty()) messages.add(message.toString());
        return messages;
    }
}
