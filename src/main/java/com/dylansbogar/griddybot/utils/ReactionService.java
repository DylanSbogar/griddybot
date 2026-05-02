package com.dylansbogar.griddybot.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.List;
import java.util.Random;

public class ReactionService {

    private static final List<String> REACTION_MESSAGES = List.of(
            "👀➡️ 🇭 🇦 🇩 🇪 🇸",
            "🆓🆘🙏😭",
            "🆂🅴🅽🅳 🅷🅴🅻🅿",
            "👁️👁️ 🫵 🅼🅰🆃🦖 💀➡️🌈🔥",
            "👆😒 🆈🅾🆄 🅳🅸🅳 🆃🅷ℹ️🆂",
            "🫵🤡🔒",
            "🤡",
            "👎",
            "👆🐀",
            "🙂🔪",
            "🖕🤏",
            "👁️🇲🇦🇹🇹👁️ 😤 🅻🅴🆃 🅼🇪 🇴🆄🆃",
            "ℹ️ 🇭🇦🇹🇪 🇾🇴🇺",
            "👆🇮🇱"
    );

    /**
     * Reacts to a message with a randomly chosen reaction sequence.
     */
    public static void reactToMessage(Message message) {
        ;
        String chosen = REACTION_MESSAGES.get(
                new Random().nextInt(0, REACTION_MESSAGES.size())
        );
        applyReactions(message, chosen);
    }

    private static void applyReactions(Message message, String reactString) {
        applyReactionChain(message, reactString.toCharArray(), 0);
    }

    private static void applyReactionChain(Message message, char[] chars, int index) {
        if (index >= chars.length) return;

        message.addReaction(Emoji.fromUnicode(String.valueOf(chars[index]))).queue(
                success -> {
                    applyReactionChain(message, chars, index + 1);
                },
                error -> {
                    System.out.println("Failed to react with: " + chars[index] + " — " + error.getMessage());
                }
        );
    }
}
