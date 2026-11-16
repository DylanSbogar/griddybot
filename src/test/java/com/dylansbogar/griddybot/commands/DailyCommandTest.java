package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.utils.DailyService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyCommandTest {
    @Test
    void validationErrorIsPrivateFromTheInitialDeferral() {
        DailyService service = mock(DailyService.class);
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        User user = mock(User.class);
        InteractionHook hook = mock(InteractionHook.class);
        ReplyCallbackAction deferral = mock(ReplyCallbackAction.class);
        WebhookMessageCreateAction<Message> message = mock(WebhookMessageCreateAction.class);
        when(event.getName()).thenReturn("daily");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user");
        when(event.getHook()).thenReturn(hook);
        when(event.deferReply(true)).thenReturn(deferral);
        when(service.saveResult("user", "")).thenThrow(new IllegalArgumentException("Paste a result."));
        when(hook.sendMessage("Paste a result.")).thenReturn(message);
        when(message.setAllowedMentions(anyList())).thenReturn(message);
        when(message.setEphemeral(true)).thenReturn(message);

        new DailyCommand(service).onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(message).setEphemeral(true);
    }

    @Test
    void availableGamesIncludeEveryLinkAcrossMessages() {
        List<DailyGame> games = IntStream.range(0, 40).mapToObj(this::game).toList();

        List<String> messages = DailyCommand.formatAvailable(games);

        assertTrue(messages.size() > 1);
        assertTrue(messages.stream().allMatch(message -> message.length() <= 1900));
        String combined = String.join("\n", messages);
        for (DailyGame game : games) {
            assertEquals(1, occurrences(combined, "<" + game.getUrl() + ">"));
        }
    }

    @Test
    void personalListIncludesEveryPendingAndCompletedGame() {
        List<DailyGame> pending = IntStream.range(0, 40).mapToObj(this::game).toList();
        List<DailyGame> completed = IntStream.range(40, 80).mapToObj(this::game).toList();

        List<String> messages = DailyCommand.formatList(
                new DailyService.DailyList(LocalDate.of(2026, 11, 16), pending, completed));

        assertTrue(messages.size() > 1);
        assertTrue(messages.stream().allMatch(message -> message.length() <= 1900));
        String combined = String.join("\n", messages);
        for (DailyGame game : pending) {
            assertEquals(1, occurrences(combined, "• " + game.getName() + " — <" + game.getUrl() + ">"));
        }
        for (DailyGame game : completed) {
            assertEquals(1, combined.lines().filter(line -> line.equals("• " + game.getName())).count());
        }
    }

    private DailyGame game(int index) {
        return DailyGame.builder().name("Game " + index)
                .url("https://example.com/" + "a".repeat(250) + index).build();
    }

    private static int occurrences(String haystack, String needle) {
        return (haystack.length() - haystack.replace(needle, "").length()) / needle.length();
    }
}
