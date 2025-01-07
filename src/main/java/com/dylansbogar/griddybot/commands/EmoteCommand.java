package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.Emote;
import com.dylansbogar.griddybot.repositories.EmoteRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.json.JSONArray;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

public class EmoteCommand extends ListenerAdapter {
    private final EmoteRepository emoteRepo;

    private static final String EMOTE_URL = "https://cdn.betterttv.net/emote/%s/3x.webp";

    public EmoteCommand(EmoteRepository emoteRepo) {
        this.emoteRepo = emoteRepo;
    }

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

            // Attempt to fetch the emotes id from the database.
            Optional<Emote> storedEmote = emoteRepo.findByName(emote);
            if (storedEmote.isEmpty()) {
                  fetchEmote(emote, event);
            } else {
                  String url = String.format(EMOTE_URL, storedEmote.get().getEmoteId());
                  event.getHook().sendMessage(url).queue();
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
            JSONArray responseBody = new JSONArray(response.body());

            // If an emote was not found, notify the user.
            if (responseBody.isEmpty()) {
                event.getHook().sendMessage(String.format("No emote found with the name %s", emote)).queue();
                return;
            }
            String emoteId = responseBody.getJSONObject(0).getString("id");

            // Save the new emote to the database.
            Emote newEmote = Emote.builder()
                    .emoteId(emoteId)
                    .name(emote)
                    .build();
            emoteRepo.save(newEmote);

            event.getHook().sendMessage(String.format(EMOTE_URL, emoteId)).queue();
        } catch (IOException | InterruptedException e) {
            event.getHook().sendMessage(String.format("There was an error fetching the %s emote", emote)).queue();
        }
    }
}