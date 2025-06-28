package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.utils.GeminiService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageListener extends ListenerAdapter {
    private static final String ozbargain = "https://www.ozbargain.com.au/node/";
    private static final Pattern thanksPattern = Pattern.compile("thanks griddy", Pattern.CASE_INSENSITIVE);
    private static final Pattern lovePattern = Pattern.compile("^i love (.*)", Pattern.CASE_INSENSITIVE);

    private final GeminiService geminiService;
    public MessageListener(GeminiService geminiService) { this.geminiService = geminiService; }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // Ensure griddybot does not respond to itself.
        if (event.getAuthor().isBot()) return;

        SelfUser griddyBot = event.getJDA().getSelfUser();
        Message message = event.getMessage();
        String content = message.getContentRaw();
        MessageChannel channel = event.getChannel();

        Matcher thanks = thanksPattern.matcher(content);
        Matcher love = lovePattern.matcher(content);

        Pattern promptPattern = Pattern.compile("<@!?" + griddyBot.getId() + ">\\s*(.*)");
        Matcher promptMatcher = promptPattern.matcher(event.getMessage().getContentRaw());

        if (content.startsWith(ozbargain)) {
            channel.sendMessage("Thanks just bought").queue();
        } else if (content.equalsIgnoreCase("gm") || content.equalsIgnoreCase("gn")) {
            channel.sendMessage(content).queue();
        } else if (thanks.find()) {
            channel.sendMessage("No worries <3").queue();
        } else if (love.find()) {
            String lovedThing = love.group(1);
            if (lovedThing.length() > 1950) { // To ensure we don't surpass Discords maximum message length.
                channel.sendMessage("yikes, I don't love all that").queue();
            } else {
                channel.sendMessage(String.format("I love %s charlie\nI love %s!!!", lovedThing, lovedThing)).queue();
            }
        } else if (message.getMentions().getUsers().contains(griddyBot) && promptMatcher.find()) {
            channel.sendMessage(geminiService.askGemini(promptMatcher.group(1))).queue();
        }
    }
}
