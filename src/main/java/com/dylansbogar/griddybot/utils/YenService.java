package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.ExchangeRate;
import com.dylansbogar.griddybot.repositories.ExchangeRateRepository;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.json.JSONObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class YenService {
    private static final String EXCHANGE_RATE_API_KEY = System.getenv("EXCHANGE_RATE_API_KEY");
    private static final String URL =
            String.format("https://v6.exchangerate-api.com/v6/%s/pair/AUD/USD", EXCHANGE_RATE_API_KEY);

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
        String message = String.format("The current conversion rate of 1 AUD to USD is: %s, as of %s",
                today.getRate(), todayDate);

        // Add previous comparison, only if one exists.
        if (yesterday != null) {
            int diff = today.getRate().compareTo(yesterday.getRate());
            String emotes = diff > 0 ? ":chart: :chart:" : ":sob: :sob:";
            message += String.format("\nPrevious conversion rate: %s %s", yesterday.getRate(), emotes);
        }

        return message;
    }

    private String convertToFormattedDate(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        LocalDate date = LocalDate.parse(dateString, formatter);
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
        return date.format(outputFormatter);
    }

    public FileUpload generateChartImage(int days) {
        PageRequest pageRequest = PageRequest.of(0, days);
        List<ExchangeRate> rates = exchangeRateRepo.findAllOrderByTimestampAsc(pageRequest);

        TimeSeries series = new TimeSeries("Conversion Rate");
        for (ExchangeRate rate : rates) {
            int day = Integer.parseInt(rate.getId().substring(0, 2));
            int month = Integer.parseInt(rate.getId().substring(3, 5));
            int year = Integer.parseInt(rate.getId().substring(6));
            // Extract day, month and year from rate.getDate();
            series.add(new Day(day, month, year), rate.getRate());
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);

        // Build the chart.
        JFreeChart chart = ChartFactory.createTimeSeriesChart("Conversion Rate (AUD to USD)",
                "Date", "Rate", dataset, false, false, false);
        chart.setBackgroundPaint(Color.decode("#303338"));
        chart.getTitle().setPaint(Color.WHITE);

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.decode("#2E3035"));

        ValueAxis xAxis = plot.getDomainAxis();
        xAxis.setLabelPaint(Color.WHITE);
        xAxis.setTickLabelPaint(Color.WHITE);

        // Override the date formatting on the x-axis.
        DateAxis dateAxis = (DateAxis) xAxis;
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM");
        dateAxis.setDateFormatOverride(dateFormat);
        dateAxis.setTickUnit(new DateTickUnit(DateTickUnitType.DAY, 1));

        ValueAxis yAxis = plot.getRangeAxis();
        yAxis.setLabelPaint(Color.WHITE);
        yAxis.setTickLabelPaint(Color.WHITE);

        NumberAxis numberYAxis = (NumberAxis) yAxis;
        numberYAxis.setTickUnit(new NumberTickUnit(0.05));

        try {
            File tempFile = File.createTempFile("chart", ".png");
            tempFile.deleteOnExit();

            // Write the chart out to tempFile.
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ChartUtils.writeChartAsPNG(output, chart, 800, 600);
                try (FileOutputStream fileOutput = new FileOutputStream(tempFile)) {
                    fileOutput.write(output.toByteArray());
                }
            }
            return FileUpload.fromData(tempFile, "chart.png");
        } catch (IOException e) {
            return null;
        }
    }
}
