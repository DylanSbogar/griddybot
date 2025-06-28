package com.dylansbogar.griddybot.commands;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;

public class GeminiCommand extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("um")) {
            event.deferReply().queue(); // Defer the reply whilst we fetch the server information.

            String PROMPT = Objects.requireNonNull(event.getOption("prompt")).getAsString();

            String modelId = "gemini-2.5-flash";

            try (Client client = Client.builder()
                    .httpOptions(HttpOptions.builder().apiVersion("v1").build())
                    .build()) {

                GenerateContentResponse response = client.models.generateContent(modelId, Content.fromParts(Part.fromText(PROMPT)), null);

                if (response.text() == null || response.text().isEmpty()) {
                    event.getHook().sendMessage("unghhhhh i fucked up").queue();
                }

                event.getHook().sendMessage(response.text()).queue();

            }
        }
    }
}