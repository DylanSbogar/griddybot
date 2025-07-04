package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.PostedDeal;
import com.dylansbogar.griddybot.repositories.DealHistoryRepository;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class MessageListener extends ListenerAdapter {
    private static final String ozbargain = "https://www.ozbargain.com.au/node/";
    private static final Pattern thanksPattern = Pattern.compile("thanks griddy", Pattern.CASE_INSENSITIVE);
    private static final Pattern lovePattern = Pattern.compile("^i love (.*)", Pattern.CASE_INSENSITIVE);

    public final DealHistoryRepository dealHistoryRepository;

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // Ensure griddybot does not respond to itself.
        if (event.getAuthor().isBot()) return;

        Message message = event.getMessage();
        String content = message.getContentRaw();
        MessageChannel channel = event.getChannel();

        Matcher thanks = thanksPattern.matcher(content);
        Matcher love = lovePattern.matcher(content);

        if (content.startsWith(ozbargain)) {
            // Extract the id from the ozBargain URL using a regex.
            Pattern dealIdPattern = Pattern.compile("https?://www\\.ozbargain\\.com\\.au/node/(\\d+)");
            Matcher dealIdMatcher = dealIdPattern.matcher(content);

            if (dealIdMatcher.find()) {
                String dealId = dealIdMatcher.group(1);

                if (dealHistoryRepository.existsById(dealId)) {
                    channel.sendMessage(":rotating_light: Repost detected :rotating_light:").queue();
                } else {
                    dealHistoryRepository.save(new PostedDeal(dealId));
                    channel.sendMessage("Thanks just bought").queue();
                }
            }
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
        }
    }
}
