package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.entities.DailyPreference;
import com.dylansbogar.griddybot.entities.DailyResult;
import com.dylansbogar.griddybot.repositories.DailyGameRepository;
import com.dylansbogar.griddybot.repositories.DailyPreferenceRepository;
import com.dylansbogar.griddybot.repositories.DailyResultRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DailyServiceTest {
    private final DailyGameRepository games = mock(DailyGameRepository.class);
    private final DailyPreferenceRepository preferences = mock(DailyPreferenceRepository.class);
    private final DailyResultRepository results = mock(DailyResultRepository.class);
    private final DailyService service = new DailyService(games, preferences, results);

    @Test
    void savesWordleResultAndExtractsPuzzleNumber() {
        DailyGame wordle = game("Wordle");
        when(games.findAll()).thenReturn(List.of(wordle));
        when(results.findByUserIdAndGameAndPlayedOn(eq("user"), eq(wordle), any()))
                .thenReturn(Optional.empty());

        DailyService.SaveOutcome outcome = service.saveResult("user",
                "Wordle 1,970 3/6\n\n⬛⬛⬛🟨🟨\n⬛🟩🟩🟨🟩\n🟩🟩🟩🟩🟩");

        ArgumentCaptor<DailyResult> saved = ArgumentCaptor.forClass(DailyResult.class);
        verify(results).save(saved.capture());
        assertEquals("Wordle", outcome.game().getName());
        assertFalse(outcome.replaced());
        assertEquals(LocalDate.now(ZoneId.of("Australia/Sydney")), saved.getValue().getPlayedOn());
        assertEquals("1970", saved.getValue().getPuzzleNumber());
        assertTrue(saved.getValue().getResultText().contains("🟩🟩🟩🟩🟩"));
    }

    @Test
    void recognizesConnectionsAndOnlyMatchesCompleteNames() {
        DailyGame connections = game("Connections");
        when(games.findAll()).thenReturn(List.of(connections));
        when(results.findByUserIdAndGameAndPlayedOn(eq("user"), eq(connections), any()))
                .thenReturn(Optional.empty());

        service.saveResult("user", "Connections\nPuzzle #1248\n🟨🟨🟨🟨\n🟩🟩🟩🟩\n🟪🟪🟪🟪\n🟦🟦🟦🟦");

        ArgumentCaptor<DailyResult> saved = ArgumentCaptor.forClass(DailyResult.class);
        verify(results).save(saved.capture());
        assertEquals("1248", saved.getValue().getPuzzleNumber());
        assertFalse(DailyService.mentionsGame("ConnectionsUnlimited #1", "Connections"));
    }

    @Test
    void recognizesTheFourSharedResultFormats() {
        DailyGame valodle = game("Valodle", "https://valodle.eu/");
        DailyGame stardewdle = game("Stardewdle", "https://stardewdle.com/");
        DailyGame narutodle = game("Narutodle", "https://narutodle.net/");
        DailyGame gregdle = game("Gregdle", "https://gregdle.carletti.work/");
        when(games.findAll()).thenReturn(List.of(valodle, stardewdle, narutodle, gregdle));

        DailyService.SaveOutcome valodleSaved = service.saveResult("user",
                "I found the agent #1580 #Valodle in Classic mode in 6 tries 🦾\n🟩🟩🟩🟩🟩\n"
                        + "and 1 more...\nhttps://valodle.eu/");
        DailyService.SaveOutcome stardewSaved = service.saveResult("user",
                "11/10/2026\nI solved today's Stardewdle!\n🟥🟥🟥🟥🟥\n🟩🟩🟩🟩🟩\n"
                        + "Play at: [https://stardewdle.com/](https://stardewdle.com/)");
        DailyService.SaveOutcome narutoSaved = service.saveResult("user",
                "I found #Narutodle character #922 in classic mode in 14 shots 🍥\n🟩🟩🟩🟩🟩🟩🟩\n"
                        + "https://narutodle.net");
        DailyService.SaveOutcome gregSaved = service.saveResult("user",
                "Gregdle gtnh Machines · HARD daily · 2026-11-10\n⬇️🟩⬆️🟩🟥🟥\n8 guesses\n"
                        + "https://gregdle.carletti.work");

        assertEquals(valodle, valodleSaved.game());
        assertEquals(stardewdle, stardewSaved.game());
        assertEquals(narutodle, narutoSaved.game());
        assertEquals(gregdle, gregSaved.game());
        ArgumentCaptor<DailyResult> saved = ArgumentCaptor.forClass(DailyResult.class);
        verify(results, times(4)).save(saved.capture());
        assertEquals("1580", saved.getAllValues().get(0).getPuzzleNumber());
        assertNull(saved.getAllValues().get(1).getPuzzleNumber());
        assertEquals("922", saved.getAllValues().get(2).getPuzzleNumber());
        assertNull(saved.getAllValues().get(3).getPuzzleNumber());
    }

    @Test
    void linkCanIdentifyResultWithoutGameName() {
        DailyGame game = game("Some Game", "https://example.org/play");
        when(games.findAll()).thenReturn(List.of(game));

        DailyService.SaveOutcome saved = service.saveResult("user",
                "Puzzle complete!\n🟩🟩🟩\nPlay: https://example.org/play");

        assertEquals(game, saved.game());
    }

    @Test
    void secondResultOnSameDayUpdatesExistingRow() {
        DailyGame wordle = game("Wordle");
        DailyResult existing = DailyResult.builder().id(UUID.randomUUID()).game(wordle)
                .userId("user").resultText("old").build();
        when(games.findAll()).thenReturn(List.of(wordle));
        when(results.findByUserIdAndGameAndPlayedOn(eq("user"), eq(wordle), any()))
                .thenReturn(Optional.of(existing));

        DailyService.SaveOutcome outcome = service.saveResult("user", "Wordle 1,970 3/6\n🟩🟩🟩🟩🟩");

        assertTrue(outcome.replaced());
        assertEquals("Wordle 1,970 3/6\n🟩🟩🟩🟩🟩", existing.getResultText());
        verify(results).save(same(existing));
    }

    @Test
    void registrationRequiresMatchingExampleAndHttpsLink() {
        assertThrows(IllegalArgumentException.class,
                () -> service.register("Minecraftdle", "https://minecraftdle.net", "Wordle 123 3/6"));
        assertThrows(IllegalArgumentException.class,
                () -> service.register("Minecraftdle", "http://minecraftdle.net", "Minecraftdle #123\n🟩"));

        service.register("Minecraftdle", "https://minecraftdle.net", "Minecraftdle #123\n🟩");
        ArgumentCaptor<DailyGame> saved = ArgumentCaptor.forClass(DailyGame.class);
        verify(games).save(saved.capture());
        assertEquals("minecraftdle", saved.getValue().getNameKey());
    }

    @Test
    void registrationAcceptsGameNameOnSecondLineOrLinkOnly() {
        service.register("Stardewdle", "https://stardewdle.com/",
                "11/10/2026\nI solved today's Stardewdle!\n🟩🟩🟩🟩🟩");
        service.register("Another Game", "https://another.example/play",
                "Puzzle complete!\n🟩🟩🟩\nhttps://another.example/play");
        verify(games, times(2)).save(any(DailyGame.class));
    }

    @Test
    void listOnlyShowsUnfinishedSelectedGames() {
        DailyGame wordle = game("Wordle");
        DailyGame connections = game("Connections");
        when(preferences.findByUserId("user")).thenReturn(List.of(
                DailyPreference.builder().userId("user").game(wordle).build(),
                DailyPreference.builder().userId("user").game(connections).build()));
        when(results.findByUserIdAndPlayedOn(eq("user"), any())).thenReturn(List.of(
                DailyResult.builder().userId("user").game(wordle).build()));

        DailyService.DailyList list = service.list("user");
        assertEquals(List.of(connections), list.pending());
        assertEquals(List.of(wordle), list.completed());
        assertEquals(connections, service.randomPending("user"));
    }

    private static DailyGame game(String name) {
        return game(name, "https://example.com");
    }

    private static DailyGame game(String name, String url) {
        return DailyGame.builder().id(UUID.randomUUID()).name(name)
                .nameKey(name.toLowerCase()).url(url).build();
    }
}
