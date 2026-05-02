package com.dylansbogar.griddybot.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.List;
import java.util.Random;

public class ReactionService {

    private static final List<List<String>> REACTION_MESSAGES = List.of(
            List.of("👀", "➡️", "🇭", "🇦", "🇩", "🇪", "🇸"),
            List.of("🆓", "🆘", "🙏", "😭"),
            List.of("🆂", "🅴", "🅽", "🅳", "🅷", "*️", "🅻", "🅿"),
            List.of("👀", "🫵", "🅼", "🅰", "🆃", "🦖", "💀", "➡️", "🌈", "🔥"),
            List.of("👆", "😒", "🆈", "🅾", "🆄", "🇲", "🇦", "🅳", "🇪", "🆃", "🅷", "ℹ️", "🆂"),
            List.of("🫵", "🤡", "🔒"),
            List.of("🤡"),
            List.of("👎"),
            List.of("👆", "🐀"),
            List.of("🙂", "🔪"),
            List.of("🖕", "🤏"),
            List.of("ℹ️", "🇭", "🇦", "🇹", "🇪", "🇾", "🇴", "🇺"),
            List.of("👆", "🇮🇱")
    );

    /**
     * Reacts to a message with a randomly chosen reaction sequence.
     */
    public static void reactToMessage(Message message) {
        List<String> chosen = REACTION_MESSAGES.get(
                new Random().nextInt(0, REACTION_MESSAGES.size())
        );
        applyReactions(message, chosen);
    }

    private static void applyReactions(Message message, List<String> reactList) {
        applyReactionChain(message, reactList, 0);
    }

    private static void applyReactionChain(Message message, List<String> reactList, int index) {
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
