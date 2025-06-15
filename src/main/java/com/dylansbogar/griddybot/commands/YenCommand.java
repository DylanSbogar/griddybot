package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.utils.YenService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class YenCommand extends ListenerAdapter {
    private final YenService yenService;

    public YenCommand(YenService yenService) {
        this.yenService = yenService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("yen")) {
            event.deferReply().queue(); // Defer the reply whilst we fetch the current exchange rate.
            event.getHook().sendMessage(yenService.fetchExchangeRate())
                    .addFiles(yenService.generateChartImage(14)).queue();
        }
    }
}