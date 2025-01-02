package com.dylansbogar.griddybot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
                    // fetchEmote(emote, event);
                    // TODO: Use GraphQL to fetch.
                } else {
                    event.getHook().sendMessage("No stored emotes.").queue();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void fetchEmote(String emote, SlashCommandInteractionEvent event) {
        String url = String.format("https://api.betterttv.net/3/emotes/shared/search?query=%s&offset=0&limit=1", emote);

        try {
            HttpClient client = HttpClient.newHttpClient();

            // Create a HttpRequest with the GET method.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            // Send the request, and receive its response.
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject responseBody = new JSONObject(response.body());
            String emoteId = responseBody.getString("id");
            event.getHook().sendMessage(String.format("https://cdn.betterttv.net/emote/%s/3x.webp", emoteId)).queue();
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage(String.format("There was an error fetching the %s emote", emote)).queue();
        }
    }
}