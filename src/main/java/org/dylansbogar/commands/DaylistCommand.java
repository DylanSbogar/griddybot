package org.dylansbogar.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class DaylistCommand extends ListenerAdapter {
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
            }

//            ClassLoader loader = Thread.currentThread().getContextClassLoader();
//            loader.getResourceAsStream("daylist.json");
//
//            event.getHook().sendMessage("Daylist!").queue();
        }
    }
}
