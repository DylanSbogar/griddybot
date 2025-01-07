package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.Daylist;
import com.dylansbogar.griddybot.entities.DaylistDescription;
import com.dylansbogar.griddybot.repositories.DaylistDescriptionRepository;
import com.dylansbogar.griddybot.repositories.DaylistRepository;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.data.domain.PageRequest;

import java.awt.print.Pageable;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class DaylistCommand extends ListenerAdapter {
    private final DaylistRepository daylistRepo;
    private final DaylistDescriptionRepository daylistDescriptionRepo;

    // Set of valid days of the week to check against input.
    private static final Set<String> DAYS_OF_WEEK = Set.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    public DaylistCommand(DaylistRepository daylistRepo, DaylistDescriptionRepository daylistDescriptionRepo) {
        this.daylistRepo = daylistRepo;
        this.daylistDescriptionRepo = daylistDescriptionRepo;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("daylist")) {
            event.deferReply().queue(); // Defer the reply whilst we add the daylist entry.

            // Retrieve the input options from the command.
            OptionMapping daylistIn = event.getOption("daylist");
            OptionMapping fileIn = event.getOption("file");

            String daylist = handleDaylistInput(daylistIn, fileIn, event);

            // If no daylist present, notify the user.
            if (daylist == null) {
                event.getHook().sendMessage("No daylist provided. Please try again.").queue();
                return;
            }

            // Process the parsed daylist.
            String[] splitDaylist = splitAndLowerCaseDaylist(daylist);
            int length = splitDaylist.length;

            // Extract the day and time from splitDaylist.
            String day = extractDay(splitDaylist, length);
            String time = extractTime(splitDaylist, length);

            // Extract the description from splitDaylist.
            String[] description = extractDescription(splitDaylist, length, day, time);

            // Check which words from description are new for all users, and for the user themselves.
            LinkedHashSet<String> globalNewWords = findNewWords(description, daylistDescriptionRepo.findAllDescriptions());
            LinkedHashSet<String> selfNewWords = findNewWords(description, daylistDescriptionRepo.findAllDescriptionsFromUser(event.getUser().getId()));

            // Build and save the new daylist objects to the database.
            saveDaylistAndDescription(event, daylist, day, time, description);

            event.getHook().sendMessage(String.format("**%s**\n\nNew words for you: %s\nNew words for all: %s",
                    daylist,
                    String.join(", ", selfNewWords),
                    String.join(", ", globalNewWords)
            )).queue();
        } else if (event.getName().equals("undodaylist")) {
            event.deferReply().queue(); // Defer the reply whilst we delete the user's last daylist entry.

            // Fetch the last daylist entered by the user.
            PageRequest pageReq = PageRequest.of(0, 1);
            List<Daylist> daylists = daylistRepo.findLastByUserIdOrderByTimestampDesc(event.getUser().getId(), pageReq);
            if (!daylists.isEmpty()) {
                daylistRepo.deleteById(daylists.get(0).getId());
                event.getHook().sendMessage("Successfully deleted your last daylist.").queue();
            } else {
                event.getHook().sendMessage("No daylist found for the user to delete.").queue();
            }
        }
    }

    /**
     * Handles the daylist input provided by the user, either directly or through an attachment.
     * @param daylistIn The option containing the daylist input text.
     * @param fileIn The option containing a file, if provided.
     * @param event The interaction event that triggered the command.
     * @return The daylist string or null if no valid input is found.
     */
    private String handleDaylistInput(OptionMapping daylistIn, OptionMapping fileIn, SlashCommandInteractionEvent event) {
        if (fileIn != null) {
            Message.Attachment file = fileIn.getAsAttachment();
            // daylist = result of OCR;
            return null; // Assume OCR result or another logic for file handling
        } else if (daylistIn != null) {
            return daylistIn.getAsString();
        }
        return null;
    }

    /**
     * Splits and lowercases the provided daylist string.
     * @param daylist The daylist string input by the user.
     * @return An array of lowercase words from the daylist string.
     */
    private String[] splitAndLowerCaseDaylist(String daylist) {
        return Arrays.stream(daylist.split(" "))
                .map(String::toLowerCase)
                .toArray(String[]::new);
    }

    /**
     * Extracts the day from the split daylist string.
     * @param splitDaylist The split daylist string.
     * @param length The length of the split daylist.
     * @return The extracted day.
     */
    private String extractDay(String[] splitDaylist, int length) {
        String beforeTime = splitDaylist[length - 2];
        boolean twoWordTime = beforeTime.equals("late") || beforeTime.equals("early");

        if (twoWordTime) {
            return splitDaylist[length - 3];
        } else {
            String day = splitDaylist[length - 2];
            return DAYS_OF_WEEK.contains(day) ? day : "";
        }
    }

    /**
     * Extracts the time from the split daylist string.
     * @param splitDaylist The split daylist string.
     * @param length The length of the split daylist.
     * @return The extracted time.
     */
    private String extractTime(String[] splitDaylist, int length) {
        String beforeTime = splitDaylist[length - 2];
        boolean twoWordTime = beforeTime.equals("late") || beforeTime.equals("early");

        if (twoWordTime) {
            return String.format("%s %s", splitDaylist[length - 2], splitDaylist[length - 1]);
        } else if (splitDaylist[length - 1].equals("moment")){ // Edge case "for this moment"
            return "for this moment";
        }
        return splitDaylist[length - 1];
    }

    /**
     * Extracts the description from the split daylist string.
     * @param splitDaylist The split daylist string.
     * @param length The length of the split daylist.
     * @param day The extracted day.
     * @param time The extracted time.
     * @return An array of description words.
     */
    private String[] extractDescription(String[] splitDaylist, int length, String day, String time) {
        int descriptionLength = (time.isEmpty() ? length - 2 : length - 3);
        return Arrays.copyOfRange(splitDaylist, 0, descriptionLength);
    }


    /**
     * Finds the new words in the description that are not present in the provided list of existing words.
     * @param splitDaylist The split daylist string.
     * @param existingWords The list of existing words to compare against.
     * @return A LinkedHashSet of new words.
     */
    private LinkedHashSet<String> findNewWords(String[] splitDaylist, List<String> existingWords) {
        LinkedHashSet<String> newWords = new LinkedHashSet<>();

        // Iterate over the split daylist words
        for (String word : splitDaylist) {
            // If the word isn't in the existing list, add it to the new words set
            if (!existingWords.contains(word)) {
                newWords.add(word);
            }
        }

        return newWords;
    }

    /**
     * Saves the daylist and its descriptions to the database.
     * @param event The interaction event triggered by the command.
     * @param daylist The original daylist string input by the user.
     * @param day The extracted day.
     * @param time The extracted time.
     * @param description The array of description words.
     */
    private void saveDaylistAndDescription(SlashCommandInteractionEvent event, String daylist, String day, String time, String[] description) {
        Daylist testDaylist = Daylist.builder()
                .userId(event.getUser().getId())
                .day(day)
                .time(time)
                .timestamp(OffsetDateTime.now())
                .build();
        daylistRepo.save(testDaylist);

        List<DaylistDescription> descriptions = Arrays.stream(description)
                .map(desc -> DaylistDescription.builder()
                        .daylist(testDaylist)
                        .userId(event.getUser().getId())
                        .description(desc)
                        .build())
                .collect(Collectors.toList());

        daylistDescriptionRepo.saveAll(descriptions);
    }
}
