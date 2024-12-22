package org.dylansbogar.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class YenCommand extends ListenerAdapter {
    private static final String EXCHANGE_RATE_API_KEY = System.getenv("EXCHANGE_RATE_API_KEY");

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("yen")) {
            event.deferReply().queue();

            // Fetch the current exchange rate.
            try {
                String url = String.format("https://v6.exchangerate-api.com/v6/%s/pair/AUD/JPY", EXCHANGE_RATE_API_KEY);

                HttpClient client = HttpClient.newHttpClient();

                // Create an HttpRequest with the GET method
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                // Send the request and receive the response
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject responseBody = new JSONObject(response.body());

                BigDecimal conversionRate = responseBody.getBigDecimal("conversion_rate").setScale(2, RoundingMode.UP);
                Long lastUpdated = responseBody.getLong("time_last_update_unix");

                event.getHook().sendMessage(String.format("The current conversion rate of 1 AUD to JPY is: %s, as of xyz", conversionRate.toString())).queue();
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
