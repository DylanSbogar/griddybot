package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.Reminder;
import com.dylansbogar.griddybot.repositories.ReminderRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class RemindMeCommand extends ListenerAdapter {
    private final ReminderRepository reminderRepo;

    @Autowired
    public RemindMeCommand(ReminderRepository reminderRepo) {
        this.reminderRepo = reminderRepo;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("remindme")) {
            event.deferReply().queue(); // Defer the reply whilst we set the reminder.
            OptionMapping dateIn = event.getOption("date");
            OptionMapping messageIn = event.getOption("message");

            // Fallback in case the user is somehow able to submit the command without the required info.
            if (dateIn == null | messageIn == null) {
                event.getHook().sendMessage("Please enter a date and message.").queue();
                return;
            }

            // Process the date string, verify its integrity.
            String message = messageIn.getAsString();
            String date = dateIn.getAsString();
            if (!validateDate(date)) {
                event.getHook().sendMessage("Please insert a valid date in the dd/mm/yyyy format.")
                        .setEphemeral(true).queue();
                return;
            }

            // Send confirmation, and save the reminder to the database.
            event.getHook().sendMessage(String.format("Set a reminder for %s on %s.", message, date)).queue(
                    replyMessage -> {
                        String replyMessageId = replyMessage.getId();

                        Reminder reminder = Reminder.builder()
                                .userId(event.getUser().getId())
                                .messageId(replyMessageId)
                                .message(message)
                                .date(date)
                                .build();
                        System.out.println(replyMessageId);
                        reminderRepo.save(reminder);
                    }
            );
        }
    }

    public static boolean validateDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        try {
            LocalDate.parse(date, formatter); // Attempt to parse date, return true if successful.
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
