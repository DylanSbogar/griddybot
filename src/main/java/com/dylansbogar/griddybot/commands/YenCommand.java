package com.dylansbogar.griddybot.commands;

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class YenCommand extends ListenerAdapter {
    private static final String EXCHANGE_RATE_API_KEY = System.getenv("EXCHANGE_RATE_API_KEY");
    private static final String URL =
            String.format("https://v6.exchangerate-api.com/v6/%s/pair/AUD/JPY", EXCHANGE_RATE_API_KEY);

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("yen")) {
            event.deferReply().queue(); // Defer the reply whilst we fetch the current exchange rate.

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

                // Grab the conversion rate and round up to 2 decimal places.
                BigDecimal conversionRate = responseBody.
                        getBigDecimal("conversion_rate").setScale(2, RoundingMode.UP);

                // Grab the lastUpdated and convert it to local time zone.
                long lastUpdated = responseBody.getLong("time_last_update_unix");
                Instant instant = Instant.ofEpochSecond(lastUpdated);
                ZonedDateTime localDateTime = instant.atZone(ZoneId.systemDefault());
                DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL);

                event.getHook().sendMessage(
                        String.format("The current conversion rate of 1 AUD to JPY is: ¥%s, as of %s", conversionRate,
                                localDateTime.format(formatter))).queue();
            } catch (InterruptedException | IOException e) {
                event.getHook().sendMessage("There was an error whilst fetching the current exchange rate.").queue();
                throw new RuntimeException(e);
            }
        }
    }
}