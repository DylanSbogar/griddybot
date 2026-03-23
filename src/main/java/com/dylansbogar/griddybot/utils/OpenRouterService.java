package com.dylansbogar.griddybot.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class OpenRouterService {
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL_ID = "minimax/minimax-m2.7";

    public String ask(String prompt) {
        String fullPrompt = prompt + " (Please keep your response under 2000 characters)";
        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL_ID);
            body.put("messages", new JSONArray()
                    .put(new JSONObject()
                            .put("role", "user")
                            .put("content", fullPrompt)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + System.getenv("OPENROUTER_API_KEY"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            return "Seems I made a fucky wucky, please try again later.";
        }
    }
}
