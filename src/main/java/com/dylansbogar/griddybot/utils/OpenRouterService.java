package com.dylansbogar.griddybot.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
public class OpenRouterService {
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String API_KEY = System.getenv("OPENROUTER_API_KEY");
    private String modelId = "@preset/brodeys-cringe";

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    private final GoogleSearchService googleSearchService;

    public OpenRouterService(GoogleSearchService googleSearchService) {
        this.googleSearchService = googleSearchService;
    }

    public String ask(ConversationHistory history) {
        try {
            JSONArray messages = new JSONArray();

            if (history.getSummary() != null && !history.getSummary().isEmpty()) {
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", "Previous conversation context: " + history.getSummary()));
            }

            List<JSONObject> historyMessages = history.getMessages();
            for (int i = 0; i < historyMessages.size(); i++) {
                JSONObject msg = historyMessages.get(i);
                if (i == historyMessages.size() - 1 && "user".equals(msg.getString("role"))) {
                    messages.put(new JSONObject()
                            .put("role", "user")
                            .put("content", msg.getString("content") + " (Please keep your response under 2000 characters)"));
                } else {
                    messages.put(msg);
                }
            }

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
                return extractContent(finalResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message"));
            }

            return extractContent(responseMessage);
        } catch (Exception e) {
            return "Seems I made a fucky wucky, please try again later.\n`" + e.getMessage() + "`";
        }
    }

    private String extractContent(JSONObject message) {
        String content = message.optString("content", null);
        if (content == null) {
            throw new RuntimeException("null content in response: " + message);
        }
        return content;
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
                .header("Authorization", "Bearer " + API_KEY)
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
