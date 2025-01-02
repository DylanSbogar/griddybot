package com.dylansbogar.griddybot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class EmoteCommand extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("emote")) {
            event.deferReply().queue(); // Defer the reply whilst we fetch the emote.

            // Retrieve the input option.
            OptionMapping emoteIn = event.getOption("emote");

            if (emoteIn == null) {
                event.getHook().sendMessage("Please enter an emote name.").queue();
                return;
            }

            String emote = emoteIn.getAsString().toLowerCase();

            // Loop through every file inside :classpath/emotes folder.
            Resource emoteDirRes = new ClassPathResource("emotes");
            try {
                File emoteDir = emoteDirRes.getFile();
                if (emoteDir.isDirectory()) {
                    for (File file : emoteDir.listFiles()) {
                        String filename = file.getName().substring(0, file.getName().lastIndexOf('.'));
                        if (filename.equals(emote)) {
                            event.getHook().sendMessage("").addFiles(FileUpload.fromData(file)).queue();
                            return;
                        }
                    }
                    event.getHook().sendMessage(String.format("Unable to fetch the %s emote locally.", emote)).queue();
                    // TODO: Use GraphQL to fetch.
                } else {
                    event.getHook().sendMessage("No stored emotes.").queue();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}