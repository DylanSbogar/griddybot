package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.Daylist;
import com.dylansbogar.griddybot.entities.DaylistDescription;
import com.dylansbogar.griddybot.repositories.DaylistDescriptionRepository;
import com.dylansbogar.griddybot.repositories.DaylistRepository;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class DaylistCommand extends ListenerAdapter {
    private final DaylistRepository daylistRepo;
    private final DaylistDescriptionRepository daylistDescriptionRepo;

    public DaylistCommand(DaylistRepository daylistRepo, DaylistDescriptionRepository daylistDescriptionRepo) {
        this.daylistRepo = daylistRepo;
        this.daylistDescriptionRepo = daylistDescriptionRepo;
    }


    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("daylist")) {
            event.deferReply().queue(); // Defer the reply whilst we add the daylist entry.

            OptionMapping daylistIn = event.getOption("daylist");
            OptionMapping fileIn = event.getOption("file");

            String daylist;

            if (daylistIn != null) {
                daylist = daylistIn.getAsString();
                System.out.println(daylist);
            } else if (fileIn != null) {
                Message.Attachment file = fileIn.getAsAttachment();
                System.out.println(file.getUrl());
                // daylist = result of OCR;
            } else {
                event.getHook().sendMessage("No daylist provided. Please try again.").queue();
                return;
            }

            Daylist testDaylist = Daylist.builder()
                    .userId("testId")
                    .day("Monday")
                    .time("Evening")
                    .timestamp(OffsetDateTime.now())
                    .build();
            daylistRepo.save(testDaylist);

            DaylistDescription testDescription = DaylistDescription.builder()
                    .daylist(testDaylist)
                    .description("Testing...")
                    .build();
            daylistDescriptionRepo.save(testDescription);

//            ClassLoader loader = Thread.currentThread().getContextClassLoader();
//            loader.getResourceAsStream("daylist.json");
//
//            event.getHook().sendMessage("Daylist!").queue();
        }
    }
}