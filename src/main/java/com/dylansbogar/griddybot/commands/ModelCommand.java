package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.utils.OpenRouterService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ModelCommand extends ListenerAdapter {
    private final OpenRouterService openRouterService;

    public ModelCommand(OpenRouterService openRouterService) {
        this.openRouterService = openRouterService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("model")) return;

        String modelId = event.getOption("model_id").getAsString();
        openRouterService.setModelId(modelId);
        event.reply("Model set to `" + modelId + "`").queue();
    }
}
