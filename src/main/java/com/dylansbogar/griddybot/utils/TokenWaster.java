package com.dylansbogar.griddybot.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;

import java.util.List;
import java.util.Random;

public class TokenWaster {
    private final static List<String> personalities = List.of(
            "penguinz0 (Moist Cr1tikal)",
            "xQc",
            "NorthernLion",
            "Internet Historian",
            "Gordon Ramsey",
            "Joe Rogan",
            "Andrew Tate",
            "Elon Mush (Twitter Era)",
            "Donald Trump",
            "Joe Biden",
            "Asmongold",
            "Hasan Piker"
    );

    private final static List<String> topics = List.of(
      "Is a hot dog a sandwich?",
      "Whats the meaning of life?",
      "Is cereal a soup?",
      "How do I get a girlfriend?",
      "How do I get a boyfriend?",
      "Whats the best way to get rich?",
      "Is Minecraft Peak?",
      "Was the moon landing real?",
      "What are your feelings about race?",
      "A rat has stolen my sandwich. How do I get revenge?",
      "I want to start a religion. Advice?",
      "Matt is the worst person in this server, right?"
    );

    public static void wasteTokens(OpenRouterService openRouterService, int wasteCount, MessageChannel channel) {
        Random random = new Random();
        ConversationHistory conversationHistory = new ConversationHistory();

        String topic = topics.get(random.nextInt(0, topics.size()));
        conversationHistory.getMessages().add(new JSONObject().put("role", "user").put("content", topic));

        for(int i = 0; i < wasteCount + 2; i++) {
            String context = String.format("You are %s, reply to following using your personality and cadence. Limit your response to 100 characters",
                    personalities.get(random.nextInt(0, personalities.size())));

            String response = openRouterService.ask(conversationHistory, context, "minimax/minimax-m2.7");
            channel.sendMessage(response).queue();

            conversationHistory.getMessages().add(new JSONObject().put("role", "user").put("content", response));
        }
    }
}
