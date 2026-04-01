package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.LastServer;
import com.dylansbogar.griddybot.repositories.LastServerRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class LastServerCommand extends ListenerAdapter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final long RECORD_ID = 1L;

    private final LastServerRepository lastServerRepository;

    public LastServerCommand(LastServerRepository lastServerRepository) {
        this.lastServerRepository = lastServerRepository;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("setlastserver")) {
            String date = event.getOption("date").getAsString();
            String description = event.getOption("description").getAsString();

            try {
                LocalDate.parse(date, FORMATTER);
            } catch (DateTimeParseException e) {
                event.reply("Invalid date format. Please use DD/MM/YY.").setEphemeral(true).queue();
                return;
            }

            lastServerRepository.save(LastServer.builder()
                    .id(RECORD_ID)
                    .date(date)
                    .description(description)
                    .build());

            event.reply("Last server set to **" + date + "** — " + description).queue();

        } else if (event.getName().equals("lastserver")) {
            Optional<LastServer> record = lastServerRepository.findById(RECORD_ID);

            if (record.isEmpty()) {
                event.reply("No last server has been set yet. Use `/setlastserver` to set one.").queue();
                return;
            }

            LastServer lastServer = record.get();
            LocalDate past = LocalDate.parse(lastServer.getDate(), FORMATTER);
            long days = ChronoUnit.DAYS.between(past, LocalDate.now());

            event.reply("It has been **" + days + " day" + (days == 1 ? "" : "s") + "** since " + lastServer.getDescription()).queue();
        }
    }
}
