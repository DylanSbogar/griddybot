package com.dylansbogar.griddybot.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class ReactionService {

    private static final List<List<String>> REACTION_MESSAGES = List.of(
            List.of("👀", "➡️", "🇭", "🇦", "🇩", "🇪", "🇸"),
            List.of("🆓", "🆘", "🙏", "😭"),
            List.of("🆂", "🅴", "🅽", "🅳", "🅷", "*️", "🅻", "🅿"),
            List.of("👀", "🫵", "🅼", "🅰", "🆃", "🦖", "💀", "➡️", "🌈", "🔥"),
            List.of("👆", "😒", "🆈", "🅾", "🆄", "🇲", "🇦", "🅳", "🇪", "🆃", "🅷", "ℹ️", "🆂"),
            List.of("🤡"),
            List.of("👎"),
            List.of("👆", "🐀"),
            List.of("🙂", "🔪"),
            List.of("🖕", "🤏"),
            List.of("ℹ️", "🇭", "🇦", "🇹", "🇪", "🇾", "🇴", "🇺"),
            List.of("👆", "🇮🇱"),
            List.of("<:laugh:1082877301521383474>"),
            List.of("<:bruh:1082876230946267246>"),
            List.of("🖕","🤓"),
            List.of("🖕","👨‍🦽"),
            List.of("🌈"),
            List.of("🆓", "🅼", "🅴", "☝️", "😤"),
            List.of("🫵", "🅰", "🆁", "🅴", "🤮"),
            List.of("😈", "📋", "✍️", "👀"),
            List.of("⏳", "😒", "🫵", "💀"),
            List.of("🙂", "😬", "🙃", "💀"),
            List.of("🧠", "💥", "😵", "⛓️")
    );

    private final List<List<String>> remainingMessages = new ArrayList<>();

    /**
     * Reacts to a message with a randomly chosen reaction sequence.
     */
    public void reactToMessage(Message message) {
        if(remainingMessages.isEmpty()) {
            remainingMessages.addAll(REACTION_MESSAGES);
            Collections.shuffle(remainingMessages);
        }

        List<String> chosen = remainingMessages.remove(0);
        applyReactions(message, chosen);
    }

    private void applyReactions(Message message, List<String> reactList) {
        applyReactionChain(message, reactList, 0);
    }

    private void applyReactionChain(Message message, List<String> reactList, int index) {
        if (index >= reactList.size()) return;

        message.addReaction(Emoji.fromUnicode(reactList.get(index))).queue(
                success -> {
                    applyReactionChain(message, reactList, index + 1);
                },
                error -> {
                    System.out.println("Failed to react with: " + reactList.get(index) + " — " + error.getMessage());
                }
        );
    }
}
