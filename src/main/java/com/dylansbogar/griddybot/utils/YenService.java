package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.ExchangeRate;
import com.dylansbogar.griddybot.repositories.ExchangeRateRepository;
import org.json.JSONObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class YenService {
    private static final String EXCHANGE_RATE_API_KEY = System.getenv("EXCHANGE_RATE_API_KEY");
    private static final String URL =
            String.format("https://v6.exchangerate-api.com/v6/%s/pair/AUD/JPY", EXCHANGE_RATE_API_KEY);

    private final ExchangeRateRepository exchangeRateRepo;

    public YenService(ExchangeRateRepository exchangeRateRepo) {
        this.exchangeRateRepo = exchangeRateRepo;
    }

    public String fetchExchangeRate() {
        // Grab today's date in the dd/MM/yyyy format.
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String formattedDate = today.format(formatter);

        // Fetch the 2 most recent exchange rates.
        List<ExchangeRate> rates = exchangeRateRepo.findAllOrderByTimestamp(PageRequest.of(0, 2));
        ExchangeRate todayRate;
        ExchangeRate yesterdayRate = null;

        if (!rates.isEmpty()) {
            if (rates.get(0).getId().equals(formattedDate)) { // TODO: Replace with current date;
                // Use these two for the rates.
                todayRate = rates.get(0);
                yesterdayRate = rates.get(1);

                // Send the message.
                return buildMessage(todayRate, yesterdayRate);
            }
            yesterdayRate = rates.get(0);
        }

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

            BigDecimal rate = responseBody.getBigDecimal("conversion_rate").setScale(2, RoundingMode.UP);
            Long lastUpdated = responseBody.getLong("time_last_update_unix");

            todayRate = ExchangeRate.builder()
                    .id(formattedDate)
                    .rate(rate)
                    .dateUnix(lastUpdated)
                    .build();
            exchangeRateRepo.save(todayRate);

            return buildMessage(todayRate, yesterdayRate);
        } catch (InterruptedException | IOException e) {
            return "There was an error whilst fetching the current exchange rate.";
        }
    }

    private String buildMessage(ExchangeRate today, ExchangeRate yesterday) {
        String todayDate = convertToFormattedDate(today.getId());
        String message = String.format("The current conversion rate of 1 AUD to JPY is: ¥%s, as of %s",
                today.getRate(), todayDate);

        // Add previous comparison, only if one exists.
        if (yesterday != null) {
            int diff = today.getRate().compareTo(yesterday.getRate());
            String emotes = diff > 0 ? ":chart: :chart:" : ":sob: :sob:";
            message += String.format("\nPrevious conversion rate: ¥%s %s", yesterday.getRate(), emotes);
        }

        return message;
    }

    private String convertToFormattedDate(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate date = LocalDate.parse(dateString, formatter);
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
        return date.format(outputFormatter);
    }
}
