package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.DailyGame;
import com.dylansbogar.griddybot.entities.DailyPreference;
import com.dylansbogar.griddybot.entities.DailyResult;
import com.dylansbogar.griddybot.repositories.DailyGameRepository;
import com.dylansbogar.griddybot.repositories.DailyPreferenceRepository;
import com.dylansbogar.griddybot.repositories.DailyResultRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DailyService {
    private static final ZoneId DAILY_ZONE = ZoneId.of("Australia/Sydney");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,49}");
    private static final Pattern HEADER_NUMBER = Pattern.compile("^\\s*#?\\s*(\\d[\\d,]*)\\b");
    private static final Pattern PUZZLE_NUMBER = Pattern.compile("(?i)^puzzle\\s*#?\\s*(\\d[\\d,]*)\\b");
    private static final Pattern HASH_NUMBER = Pattern.compile("#\\s*(\\d[\\d,]*)\\b");
    private static final Pattern URL = Pattern.compile("https://[^\\s\\]>)]+", Pattern.CASE_INSENSITIVE);

    private final DailyGameRepository games;
    private final DailyPreferenceRepository preferences;
    private final DailyResultRepository results;

    @PostConstruct
    public void seedGames() {
        seed("Wordle", "https://www.nytimes.com/games/wordle/index.html");
        seed("Connections", "https://www.nytimes.com/games/connections");
        seed("Valodle", "https://valodle.eu/");
        seed("Stardewdle", "https://stardewdle.com/");
        seed("Narutodle", "https://narutodle.net/");
        seed("Gregdle", "https://gregdle.carletti.work/");
    }

    private void seed(String name, String url) {
        if (games.findByNameKey(key(name)).isEmpty()) {
            games.save(DailyGame.builder().name(name).nameKey(key(name)).url(url).build());
        }
    }

    @Transactional
    public DailyGame register(String name, String url, String example) {
        String cleanName = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (!NAME.matcher(cleanName).matches()) {
            throw new IllegalArgumentException("Use a game name of 1–50 letters, numbers, spaces, periods, underscores or hyphens.");
        }
        if (games.findByNameKey(key(cleanName)).isPresent()) {
            throw new IllegalArgumentException("That daily is already registered.");
        }
        if (!validUrl(url)) {
            throw new IllegalArgumentException("Use a valid HTTPS game link.");
        }
        if (example == null || example.isBlank() ||
                (!mentionsGame(example, cleanName) && !containsGameUrl(example, url))) {
            throw new IllegalArgumentException("The example must mention the game in its opening lines or include its game link.");
        }
        return games.save(DailyGame.builder().name(cleanName).nameKey(key(cleanName)).url(url.trim()).build());
    }

    @Transactional
    public SaveOutcome saveResult(String userId, String text) {
        if (text == null || text.isBlank() || text.length() > 6000) {
            throw new IllegalArgumentException("Paste a result of up to 6000 characters.");
        }
        DailyGame game = detectGame(text, games.findAll());

        LocalDate today = LocalDate.now(DAILY_ZONE);
        DailyResult result = results.findByUserIdAndGameAndPlayedOn(userId, game, today).orElse(null);
        boolean replaced = result != null;
        if (result == null) {
            result = DailyResult.builder().userId(userId).game(game).playedOn(today).build();
        }
        result.setResultText(text.trim());
        String number = puzzleNumber(text, game.getName());
        result.setPuzzleNumber(number != null && number.length() <= 50 ? number : null);
        result.setPostedAt(OffsetDateTime.now(DAILY_ZONE));
        results.save(result);
        return new SaveOutcome(game, today, replaced);
    }

    @Transactional
    public DailyGame add(String userId, String name) {
        DailyGame game = findGame(name);
        if (preferences.findByUserIdAndGame(userId, game).isPresent()) {
            throw new IllegalArgumentException(game.getName() + " is already on your daily list.");
        }
        preferences.save(DailyPreference.builder().userId(userId).game(game).build());
        return game;
    }

    @Transactional
    public DailyGame remove(String userId, String name) {
        DailyGame game = findGame(name);
        DailyPreference preference = preferences.findByUserIdAndGame(userId, game)
                .orElseThrow(() -> new IllegalArgumentException(game.getName() + " is not on your daily list."));
        preferences.delete(preference);
        return game;
    }

    @Transactional(readOnly = true)
    public DailyList list(String userId) {
        LocalDate today = LocalDate.now(DAILY_ZONE);
        Set<java.util.UUID> completed = new HashSet<>();
        results.findByUserIdAndPlayedOn(userId, today)
                .forEach(result -> completed.add(result.getGame().getId()));
        List<DailyGame> selected = preferences.findByUserId(userId).stream()
                .map(DailyPreference::getGame)
                .sorted(Comparator.comparing(DailyGame::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new DailyList(today,
                selected.stream().filter(g -> !completed.contains(g.getId())).toList(),
                selected.stream().filter(g -> completed.contains(g.getId())).toList());
    }

    @Transactional(readOnly = true)
    public DailyGame randomPending(String userId) {
        List<DailyGame> pending = list(userId).pending();
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("No unfinished dailies on your list. Use /adddaily to add one.");
        }
        return pending.get(ThreadLocalRandom.current().nextInt(pending.size()));
    }

    @Transactional(readOnly = true)
    public List<DailyGame> availableGames() {
        return games.findAll().stream()
                .sorted(Comparator.comparing(DailyGame::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private DailyGame findGame(String name) {
        return games.findByNameKey(key(name)).orElseThrow(() ->
                new IllegalArgumentException("Unknown daily. Use /listdailies to see registered games."));
    }

    private static String key(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static DailyGame detectGame(String result, List<DailyGame> registered) {
        List<DailyGame> mentioned = registered.stream()
                .filter(game -> mentionsGame(result, game.getName()))
                .sorted(Comparator.comparingInt((DailyGame game) -> game.getName().length()).reversed())
                .toList();
        if (mentioned.size() == 1 || (mentioned.size() > 1
                && mentioned.get(0).getName().toLowerCase(Locale.ROOT)
                .startsWith(mentioned.get(1).getName().toLowerCase(Locale.ROOT)))) {
            return mentioned.get(0);
        }
        List<DailyGame> linked = registered.stream()
                .filter(game -> containsGameUrl(result, game.getUrl())).toList();
        if (linked.size() == 1) return linked.get(0);
        if (mentioned.size() > 1 || linked.size() > 1) {
            throw new IllegalArgumentException("This result matches multiple dailies. Check the game names and links.");
        }
        throw new IllegalArgumentException("I couldn't recognize that result. Register its game with /registerdaily first.");
    }

    static boolean mentionsGame(String result, String name) {
        if (result == null || name == null || name.isBlank()) return false;
        Pattern title = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        int checked = 0;
        for (String line : result.split("\\R")) {
            if (line.isBlank()) continue;
            if (title.matcher(URL.matcher(line).replaceAll("")).find()) return true;
            if (++checked == 3) break;
        }
        return false;
    }

    static boolean containsGameUrl(String result, String gameUrl) {
        if (result == null || gameUrl == null) return false;
        URI game = URI.create(gameUrl);
        Matcher urls = URL.matcher(result);
        while (urls.find()) {
            try {
                URI found = URI.create(urls.group().replaceFirst("[.,!;]+$", ""));
                if (game.getHost() != null && found.getHost() != null
                        && normalizeHost(game.getHost()).equals(normalizeHost(found.getHost()))
                        && (game.getPath().isBlank() || game.getPath().equals("/")
                        || found.getPath().equals(game.getPath())
                        || found.getPath().startsWith(game.getPath() + "/"))) return true;
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed links in a copied result.
            }
        }
        return false;
    }

    private static String normalizeHost(String host) {
        return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
    }

    static String puzzleNumber(String result, String name) {
        for (String line : result.split("\\R")) {
            Matcher puzzleMatch = PUZZLE_NUMBER.matcher(line.trim());
            if (puzzleMatch.find()) return puzzleMatch.group(1).replace(",", "");
        }
        Pattern title = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        for (String line : result.split("\\R")) {
            Matcher nameMatch = title.matcher(line);
            if (!nameMatch.find()) continue;
            String afterName = line.substring(nameMatch.end());
            Matcher directNumber = HEADER_NUMBER.matcher(afterName);
            if (directNumber.find()) return directNumber.group(1).replace(",", "");
            Matcher afterHash = HASH_NUMBER.matcher(afterName);
            if (afterHash.find()) return afterHash.group(1).replace(",", "");
            Matcher beforeHash = HASH_NUMBER.matcher(line.substring(0, nameMatch.start()));
            String beforeNumber = null;
            while (beforeHash.find()) beforeNumber = beforeHash.group(1).replace(",", "");
            return beforeNumber;
        }
        return null;
    }

    private static boolean validUrl(String value) {
        if (value == null || value.length() > 500 || !value.equals(value.trim())) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && !value.matches("(?s).*\\s.*");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public record SaveOutcome(DailyGame game, LocalDate date, boolean replaced) {}
    public record DailyList(LocalDate date, List<DailyGame> pending, List<DailyGame> completed) {}
}
