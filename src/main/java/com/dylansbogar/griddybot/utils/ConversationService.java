package com.dylansbogar.griddybot.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService {
    private static final String SUMMARY_MODEL = "meta-llama/llama-3.2-3b-instruct";
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String API_KEY = System.getenv("OPENROUTER_API_KEY");

    private final Map<String, ConversationHistory> histories = new ConcurrentHashMap<>();

    public ConversationHistory getHistory(String channelId) {
        return histories.computeIfAbsent(channelId, k -> new ConversationHistory());
    }

    public void addMessage(String channelId, String role, String content) {
        ConversationHistory history = getHistory(channelId);
        history.getMessages().add(new JSONObject().put("role", role).put("content", content));

        if (history.getMessages().size() > 20) {
            List<JSONObject> toSummarize = new ArrayList<>(history.getMessages().subList(0, 20));
            JSONObject latest = history.getMessages().get(20);

            String newSummary = summarize(toSummarize, history.getSummary());
            history.setSummary(newSummary);
            history.getMessages().clear();
            history.getMessages().add(latest);
        }
    }

    private String summarize(List<JSONObject> messages, String existingSummary) {
        try {
            StringBuilder conversation = new StringBuilder();
            if (existingSummary != null && !existingSummary.isEmpty()) {
                conversation.append("Previous summary: ").append(existingSummary).append("\n\n");
            }
            conversation.append("Conversation:\n");
            for (JSONObject msg : messages) {
                conversation.append(msg.getString("role")).append(": ")
                        .append(msg.getString("content")).append("\n");
            }

            String prompt = "Summarize the following conversation concisely, incorporating any previous summary. Keep it under 500 characters.\n\n"
                    + conversation;

            JSONObject body = new JSONObject()
                    .put("model", SUMMARY_MODEL)
                    .put("messages", new JSONArray()
                            .put(new JSONObject()
                                    .put("role", "user")
                                    .put("content", prompt)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Authorization", "Bearer " + API_KEY)
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
            return existingSummary != null ? existingSummary : "";
        }
    }
}
