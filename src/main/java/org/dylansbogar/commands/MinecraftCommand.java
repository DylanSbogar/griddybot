package org.dylansbogar.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.dylansbogar.utils.EmbedGenerator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MinecraftCommand extends ListenerAdapter {
    private static final EmbedGenerator embedGenerator = new EmbedGenerator();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("minecraft")) {
            event.deferReply().queue(); // Defer the reply whilst we fetch the server information.

            String SERVER = Objects.requireNonNull(event.getOption("server")).getAsString();

            String URL = String.format("https://api.mcsrvstat.us/2/%s", SERVER);
            String ICON_URL = String.format("https://api.mcsrvstat.us/icon/%s", SERVER);
            try {
                HttpClient client = HttpClient.newHttpClient();

                // Create a HttpRequest with the GET method.
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(URL))
                        .GET()
                        .build();

                // Send the request, and receive its response.
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject responseBody = new JSONObject(response.body());

                String status = responseBody.getBoolean("online") ? ":white_check_mark:" : ":x:";
                JSONObject playersObj = responseBody.getJSONObject("players");
                int online = playersObj.getInt("online");
                JSONArray playersList = playersObj.getJSONArray("list");

                // Create all the fields.
                List<MessageEmbed.Field> fields = new ArrayList<>();
                fields.add(new MessageEmbed.Field("Status", status, true));
                fields.add(new MessageEmbed.Field("Online", String.valueOf(online), true));

                // Optional field to list all the online players.
                if (online > 0) {
                    fields.add(new MessageEmbed.Field("Players", playersList.join(", ").replace("\"", ""), true));
                }

                // Generate the embed and send it back.
                EmbedBuilder embed = embedGenerator.generateEmbed("Minecraft Server Status", SERVER, ICON_URL, fields);
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            } catch (IOException | InterruptedException e) {
                event.getHook().sendMessage("There was an error fetching the Minecraft server information.").queue();
                throw new RuntimeException(e);
            }
        }
    }
}
