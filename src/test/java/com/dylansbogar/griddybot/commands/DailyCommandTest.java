package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.entities.DailyResult;
import com.dylansbogar.griddybot.utils.DailyService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
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

    @Test
    void bragMessagesPreserveEveryCharacterAcrossDiscordSizedChunks() {
        String resultText = "Wordle 1 2/6\n" + "🟩".repeat(1100) + "\nFinished";
        DailyResult result = DailyResult.builder().game(game(1)).resultText(resultText).build();

        List<String> messages = DailyCommand.formatBrag(List.of(result), "Player");

        assertTrue(messages.size() > 1);
        assertTrue(messages.stream().allMatch(message -> message.length() <= 1900));
        assertEquals("**Game 1 — Player**\n" + resultText, String.join("", messages));
    }

    @Test
    void dailyMessagesIncludeThePlayerAndPreserveLongResults() {
        String resultText = "Game 1 #123\n" + "🟩".repeat(1100) + "\nFinished";

        List<String> messages = DailyCommand.formatDaily(game(1), "<@123>", resultText);

        assertTrue(messages.size() > 1);
        assertTrue(messages.stream().allMatch(message -> message.length() <= 1900));
        assertEquals("<@123> just played **Game 1**\n" + resultText, String.join("", messages));
    }

    @Test
    void savingADailyPostsTheResultToTheChannel() {
        DailyService service = mock(DailyService.class);
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        MessageCreateAction publicMessage = mock(MessageCreateAction.class);
        ReplyCallbackAction deferral = mock(ReplyCallbackAction.class);
        OptionMapping result = mock(OptionMapping.class);
        DailyGame game = game(1);
        String resultText = "Game 1 #123\n🟩🟩";
        when(event.getName()).thenReturn("daily");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user");
        when(user.getAsMention()).thenReturn("<@123>");
        when(event.deferReply(true)).thenReturn(deferral);
        when(event.getChannel()).thenReturn(channel);
        when(event.getOption("result")).thenReturn(result);
        when(result.getAsString()).thenReturn(resultText);
        when(service.saveResult("user", resultText)).thenReturn(
                new DailyService.SaveOutcome(game, LocalDate.of(2026, 11, 16), false, true));
        String post = "<@123> just played **Game 1**\n" + resultText;
        when(channel.sendMessage(post)).thenReturn(publicMessage);
        when(publicMessage.setAllowedMentions(anyList())).thenReturn(publicMessage);

        new DailyCommand(service).onSlashCommandInteraction(event);

        verify(service).saveResult("user", resultText);
        verify(channel).sendMessage(post);
        verify(publicMessage).setAllowedMentions(anyList());
    }

    @Test
    void registrationAnnouncesToTheChannel() {
        DailyService service = mock(DailyService.class);
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        MessageCreateAction publicMessage = mock(MessageCreateAction.class);
        ReplyCallbackAction deferral = mock(ReplyCallbackAction.class);
        OptionMapping name = mock(OptionMapping.class);
        OptionMapping url = mock(OptionMapping.class);
        OptionMapping example = mock(OptionMapping.class);
        DailyGame game = game(1);
        when(event.getName()).thenReturn("registerdaily");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user");
        when(event.deferReply(true)).thenReturn(deferral);
        when(event.getChannel()).thenReturn(channel);
        when(event.getOption("name")).thenReturn(name);
        when(event.getOption("url")).thenReturn(url);
        when(event.getOption("resultexample")).thenReturn(example);
        when(name.getAsString()).thenReturn("Game 1");
        when(url.getAsString()).thenReturn("https://example.com/game");
        when(example.getAsString()).thenReturn("Game 1 #123");
        when(service.register("Game 1", "https://example.com/game", "Game 1 #123")).thenReturn(game);
        String announcement = "Registered Game 1. Use /addmydaily name:Game 1 to put it on your list.";
        when(channel.sendMessage(announcement)).thenReturn(publicMessage);
        when(publicMessage.setAllowedMentions(anyList())).thenReturn(publicMessage);

        new DailyCommand(service).onSlashCommandInteraction(event);

        verify(event).deferReply(true);
        verify(channel).sendMessage(announcement);
        verify(publicMessage).setAllowedMentions(anyList());
    }

    @Test
    void bragWithoutNamePostsAllTodaysResultsToTheChannel() {
        DailyService service = mock(DailyService.class);
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        User user = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        MessageCreateAction publicMessage = mock(MessageCreateAction.class);
        ReplyCallbackAction deferral = mock(ReplyCallbackAction.class);
        DailyResult result = DailyResult.builder().game(game(1)).resultText("Game 1 #123\n🟩🟩").build();
        when(event.getName()).thenReturn("brag");
        when(event.getUser()).thenReturn(user);
        when(user.getId()).thenReturn("user");
        when(user.getName()).thenReturn("Player");
        when(event.deferReply(true)).thenReturn(deferral);
        when(event.getChannel()).thenReturn(channel);
        when(service.brag("user", "")).thenReturn(List.of(result));
        String post = "**Game 1 — Player**\nGame 1 #123\n🟩🟩";
        when(channel.sendMessage(post)).thenReturn(publicMessage);
        when(publicMessage.setAllowedMentions(anyList())).thenReturn(publicMessage);

        new DailyCommand(service).onSlashCommandInteraction(event);

        verify(service).brag("user", "");
        verify(channel).sendMessage(post);
    }

    private DailyGame game(int index) {
        return DailyGame.builder().name("Game " + index)
                .url("https://example.com/" + "a".repeat(250) + index).build();
    }

    private static int occurrences(String haystack, String needle) {
        return (haystack.length() - haystack.replace(needle, "").length()) / needle.length();
    }
}
