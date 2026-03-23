package com.dylansbogar.griddybot.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class GoogleSearchService {
    private static final String API_URL = "https://www.googleapis.com/customsearch/v1";

    public String search(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = API_URL
                    + "?key=" + System.getenv("GOOGLE_API_KEY")
                    + "&cx=" + System.getenv("GOOGLE_SEARCH_ENGINE_ID")
                    + "&q=" + encodedQuery
                    + "&num=5";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            JSONArray items = json.optJSONArray("items");

            if (items == null || items.isEmpty()) {
                return "No results found.";
            }

            StringBuilder results = new StringBuilder();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                results.append(item.optString("title")).append("\n");
                results.append(item.optString("link")).append("\n");
                results.append(item.optString("snippet")).append("\n\n");
            }

            return results.toString().trim();
        } catch (Exception e) {
            return "Search failed: " + e.getMessage();
        }
    }
}
