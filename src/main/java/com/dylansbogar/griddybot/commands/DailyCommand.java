package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.DailyGame;
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
        if (!List.of("daily", "registerdaily", "adddaily", "removedaily", "dailies", "randomdaily", "listdailies")
                .contains(event.getName())) return;

        event.deferReply(true).queue();
        try {
            String userId = event.getUser().getId();
            List<String> replies = switch (event.getName()) {
                case "daily" -> {
                    DailyService.SaveOutcome outcome = dailyService.saveResult(userId, option(event, "result"));
                    yield List.of((outcome.replaced() ? "Updated" : "Saved") + " your " + outcome.game().getName()
                            + " result for " + outcome.date() + ".");
                }
                case "registerdaily" -> {
                    DailyGame game = dailyService.register(option(event, "name"), option(event, "url"),
                            option(event, "resultexample"));
                    yield List.of("Registered " + game.getName() + ". Use /adddaily name:" + game.getName()
                            + " to put it on your list.");
                }
                case "adddaily" -> List.of("Added " + dailyService.add(userId, option(event, "name")).getName()
                        + " to your daily list.");
                case "removedaily" -> List.of("Removed " + dailyService.remove(userId, option(event, "name")).getName()
                        + " from your daily list.");
                case "dailies" -> formatList(dailyService.list(userId));
                case "randomdaily" -> {
                    DailyGame game = dailyService.randomPending(userId);
                    yield List.of("Try " + game.getName() + ": <" + game.getUrl() + ">");
                }
                case "listdailies" -> formatAvailable(dailyService.availableGames());
                default -> throw new IllegalStateException("Unexpected daily command");
            };
            sendReplies(event, replies, 0);
        } catch (IllegalArgumentException e) {
            sendReplies(event, List.of(e.getMessage()), 0);
        }
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
            return List.of("Your daily list is empty. Use /listdailies and /adddaily to choose games.");
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
