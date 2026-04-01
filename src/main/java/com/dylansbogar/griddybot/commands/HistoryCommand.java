package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.utils.ConversationHistory;
import com.dylansbogar.griddybot.utils.ConversationService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class HistoryCommand extends ListenerAdapter {
    private final ConversationService conversationService;

    public HistoryCommand(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("history")) return;

        ConversationHistory history = conversationService.getHistory(event.getChannel().getId());
        String summary = history.getSummary();

        if (summary == null || summary.isEmpty()) {
            event.reply("No summary yet — conversation hasn't been compacted.").setEphemeral(true).queue();
        } else {
            event.reply("**Conversation summary:**\n" + summary).setEphemeral(true).queue();
        }
    }
}
