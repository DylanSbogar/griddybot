package com.dylansbogar.griddybot.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class ReactionService {

    private static final List<List<String>> REACTION_MESSAGES = List.of(
            List.of("👀", "➡️", "🇭", "🇦", "🇩", "🇪", "🇸"), // H A D E S
            List.of("🆓", "🆘", "🙏", "😭"),
            List.of("\uD83C\uDDF8", "\uD83C\uDDEA", "\uD83C\uDDF3", "\uD83C\uDDE9", "\uD83C\uDDED", "⭐", "\uD83C\uDDF1", "\uD83C\uDDF5"), // S E N D H * L P
            List.of("👀", "🫵", "🇲", "\uD83C\uDDE6", "\uD83C\uDDF9", "🦖", "💀", "➡️", "🌈", "🔥"), // M A T
            List.of("👆", "😒", "\uD83C\uDDFE", "🇴", "\uD83C\uDDFA", "🇲", "🇦", "\uD83C\uDDE9", "\uD83C\uDDEA", "\uD83C\uDDF9", "\uD83C\uDDED", "\uD83C\uDDEE", "\uD83C\uDDF8"), // Y O U M A D E T H I S
            List.of("🤡"),
            List.of("👎"),
            List.of("👆", "🐀"),
            List.of("🙂", "🔪"),
            List.of("🖕", "🤏"),
            List.of("ℹ️", "🇭", "🇦", "🇹", "🇪", "🇾", "🇴", "🇺"), // I H A T E Y O U
            List.of("👆", "🇮🇱"),
            List.of("<:laugh:1082877301521383474>"),
            List.of("<:bruh:1082876230946267246>"),
            List.of("🖕","🤓"),
            List.of("🖕","👨‍🦽"),
            List.of("🌈"),
            List.of("🆓", "🇲", "\uD83C\uDDEA", "☝️", "😤"), // M E
            List.of("🫵", "🅰", "\uD83C\uDDF7", "🇪", "🤮"), // A R E
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
