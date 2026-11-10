package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.utils.DailyService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

@RequiredArgsConstructor
public class DailyCommand extends ListenerAdapter {
    private final DailyService dailyService;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!List.of("daily", "registerdaily", "adddaily", "removedaily", "dailies", "randomdaily", "listdailies")
                .contains(event.getName())) return;

        event.deferReply().queue();
        try {
            String userId = event.getUser().getId();
            String reply = switch (event.getName()) {
                case "daily" -> {
                    DailyService.SaveOutcome outcome = dailyService.saveResult(userId, option(event, "result"));
                    yield (outcome.replaced() ? "Updated" : "Saved") + " your " + outcome.game().getName()
                            + " result for " + outcome.date() + ".";
                }
                case "registerdaily" -> {
                    DailyGame game = dailyService.register(option(event, "name"), option(event, "url"),
                            option(event, "resultexample"));
                    yield "Registered " + game.getName() + ". Use /adddaily name:" + game.getName()
                            + " to put it on your list.";
                }
                case "adddaily" -> "Added " + dailyService.add(userId, option(event, "name")).getName()
                        + " to your daily list.";
                case "removedaily" -> "Removed " + dailyService.remove(userId, option(event, "name")).getName()
                        + " from your daily list.";
                case "dailies" -> formatList(dailyService.list(userId));
                case "randomdaily" -> {
                    DailyGame game = dailyService.randomPending(userId);
                    yield "Try " + game.getName() + ": <" + game.getUrl() + ">";
                }
                case "listdailies" -> formatAvailable(dailyService.availableGames());
                default -> throw new IllegalStateException("Unexpected daily command");
            };
            event.getHook().sendMessage(reply).setAllowedMentions(List.of()).queue();
        } catch (IllegalArgumentException e) {
            event.getHook().sendMessage(e.getMessage()).setEphemeral(true).queue();
        }
    }

    private static String option(SlashCommandInteractionEvent event, String name) {
        var option = event.getOption(name);
        return option == null ? "" : option.getAsString();
    }

    private static String formatList(DailyService.DailyList list) {
        if (list.pending().isEmpty() && list.completed().isEmpty()) {
            return "Your daily list is empty. Use /listdailies and /adddaily to choose games.";
        }
        StringBuilder message = new StringBuilder("Your dailies for ").append(list.date()).append(":\n");
        if (list.pending().isEmpty()) {
            message.append("All done today!\n");
        } else {
            message.append("Still to do:\n");
            for (DailyGame game : list.pending()) {
                appendLimited(message, "• " + game.getName() + " — <" + game.getUrl() + ">\n");
            }
        }
        if (!list.completed().isEmpty()) {
            message.append("Done: ");
            appendLimited(message, String.join(", ", list.completed().stream().map(DailyGame::getName).toList()));
        }
        return message.toString();
    }

    private static String formatAvailable(List<DailyGame> games) {
        StringBuilder message = new StringBuilder("Registered dailies:\n");
        for (DailyGame game : games) {
            appendLimited(message, "• " + game.getName() + " — <" + game.getUrl() + ">\n");
        }
        return message.toString();
    }

    private static void appendLimited(StringBuilder message, String line) {
        if (message.length() + line.length() <= 1900) message.append(line);
        else if (!message.toString().endsWith("…")) message.append("…");
    }
}
