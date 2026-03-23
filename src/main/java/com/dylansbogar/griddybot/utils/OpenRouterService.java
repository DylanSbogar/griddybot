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
    private String modelId = "minimax/minimax-m2.7";

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    private final GoogleSearchService googleSearchService;

    public OpenRouterService(GoogleSearchService googleSearchService) {
        this.googleSearchService = googleSearchService;
    }

    public String ask(String prompt) {
        String fullPrompt = prompt + " (Please keep your response under 2000 characters)";
        try {
            JSONArray messages = new JSONArray()
                    .put(new JSONObject()
                            .put("role", "user")
                            .put("content", fullPrompt));

            JSONArray tools = new JSONArray()
                    .put(new JSONObject()
                            .put("type", "function")
                            .put("function", new JSONObject()
                                    .put("name", "web_search")
                                    .put("description", "Search the web for current information.")
                                    .put("parameters", new JSONObject()
                                            .put("type", "object")
                                            .put("properties", new JSONObject()
                                                    .put("query", new JSONObject()
                                                            .put("type", "string")
                                                            .put("description", "The search query")))
                                            .put("required", new JSONArray().put("query")))));

            JSONObject firstResponse = callApi(messages, tools);
            JSONArray choices = firstResponse.getJSONArray("choices");
            JSONObject choice = choices.getJSONObject(0);
            JSONObject responseMessage = choice.getJSONObject("message");

            // If the model wants to call a tool, execute it and send results back
            if (responseMessage.has("tool_calls") && !responseMessage.isNull("tool_calls")) {
                JSONArray toolCalls = responseMessage.getJSONArray("tool_calls");
                messages.put(responseMessage);

                for (int i = 0; i < toolCalls.length(); i++) {
                    JSONObject toolCall = toolCalls.getJSONObject(i);
                    String toolCallId = toolCall.getString("id");
                    String query = toolCall.getJSONObject("function")
                            .getJSONObject("arguments") instanceof JSONObject
                            ? toolCall.getJSONObject("function").getJSONObject("arguments").getString("query")
                            : new JSONObject(toolCall.getJSONObject("function").getString("arguments")).getString("query");

                    String searchResults = googleSearchService.search(query);

                    messages.put(new JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", toolCallId)
                            .put("content", searchResults));
                }

                JSONObject finalResponse = callApi(messages, null);
                return finalResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
            }

            return responseMessage.getString("content");
        } catch (Exception e) {
            return "Seems I made a fucky wucky, please try again later.";
        }
    }

    private JSONObject callApi(JSONArray messages, JSONArray tools) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", modelId);
        body.put("messages", messages);
        if (tools != null) {
            body.put("tools", tools);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + System.getenv("OPENROUTER_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject json = new JSONObject(response.body());
        if (json.has("error")) {
            String errorMsg = json.getJSONObject("error").optString("message", "unknown error");
            throw new RuntimeException("OpenRouter error: " + errorMsg);
        }
        return json;
    }
}
