package com.dylansbogar.griddybot.utils;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {
    private static final String MODEL_ID = "gemini-2.5-flash";

    public String askGemini(String prompt) {
        prompt += " (Please keep your response under 2000 characters)";
        try (Client client = Client.builder()
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .httpOptions(HttpOptions.builder().apiVersion("v1").build())
                .build()) {

            GenerateContentResponse response = client.models.generateContent(MODEL_ID, Content.fromParts(Part.fromText(prompt)), null);

            if (response.text() == null || response.text().isEmpty()) {
                return "unghhhhh i fucked up";
            }

            return response.text();
        } catch (Exception e) {
            return "Seems I made a fucky wucky, please try again later.";
        }
    }
}
