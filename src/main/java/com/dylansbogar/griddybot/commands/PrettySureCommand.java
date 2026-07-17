package com.dylansbogar.griddybot.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.List;

public class PrettySureCommand extends ListenerAdapter {
    private static final List<String> URLs = List.of(
            "https://klipy.com/gifs/pretty-sure-mark-grayson",
            "https://tenor.com/view/threw-a-trashbag-are-you-sure-into-space-at-work-invincible-gif-17914355764938628475",
            "https://klipy.com/gifs/pretty-sure-into-space",
            "https://klipy.com/gifs/are-you-sure-omni-man-3"
    );

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("prettysure")) {
            event.deferReply().queue();

            InteractionHook hook = event.getHook();
            URLs.stream()
                    .<RestAction<Message>>map(hook::sendMessage)
                    .reduce((a, b) -> a.flatMap(prev -> b))
                    .ifPresent(RestAction::queue );
        }
    }
}
