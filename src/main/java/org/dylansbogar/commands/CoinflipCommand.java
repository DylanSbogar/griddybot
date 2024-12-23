package org.dylansbogar.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Random;

public class CoinflipCommand extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("coinflip")) {
            event.deferReply().queue(); // Defer the reply whilst the coin flips.

            // Select a random number between 1 and 20.
            Random random = new Random();
            int flips = random.nextInt((20 - 1) + 1) + 5;

            // Select heads of tails based on the number of times the coin flipped.
            String result = (flips %2 == 0) ? "Tails" : "Heads";

            event.getHook().sendMessage(
                    String.format("The coin landed on ***%s*** :coin:", result)).queue();
        }
    }
}
