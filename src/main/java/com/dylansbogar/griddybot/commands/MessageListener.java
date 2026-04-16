package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.PostedDeal;
import com.dylansbogar.griddybot.repositories.DealHistoryRepository;
import com.dylansbogar.griddybot.utils.ConversationService;
import com.dylansbogar.griddybot.utils.InstagramService;
import com.dylansbogar.griddybot.utils.OpenRouterService;
import com.dylansbogar.griddybot.utils.OzbargainService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageListener extends ListenerAdapter {
    private static final String ozbargain = "https://www.ozbargain.com.au/node/";
    private static final Pattern thanksPattern = Pattern.compile("thanks griddy", Pattern.CASE_INSENSITIVE);
    private static final Pattern lovePattern = Pattern.compile("^i love (.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern instagramReelPattern = Pattern.compile(
            "https?://(?:www\\.)?instagram\\.com/reel/[A-Za-z0-9_-]+/?(?:\\?[^\\s]*)?",
            Pattern.CASE_INSENSITIVE
    );

    public final DealHistoryRepository dealHistoryRepository;
    private final OzbargainService ozbargainService;
    private final OpenRouterService openRouterService;
    private final ConversationService conversationService;
    private final InstagramService instagramService;

    public MessageListener(DealHistoryRepository dealHistoryRepository, OzbargainService ozbargainService, OpenRouterService openRouterService, ConversationService conversationService, InstagramService instagramService) {
        this.dealHistoryRepository = dealHistoryRepository;
        this.ozbargainService = ozbargainService;
        this.openRouterService = openRouterService;
        this.conversationService = conversationService;
        this.instagramService = instagramService;
    }

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
        Matcher instagramMatcher = instagramReelPattern.matcher(content);

        Pattern promptPattern = Pattern.compile("<@!?" + griddyBot.getId() + ">\\s*(.*)");
        Matcher promptMatcher = promptPattern.matcher(event.getMessage().getContentRaw());

        if (instagramMatcher.find()) {
            String instagramUrl = instagramMatcher.group();
            channel.retrieveMessageById(event.getMessageId()).queue(msg -> {
                String mediaUrl = instagramService.getMediaUrl(instagramUrl);
                if (mediaUrl != null) {
                    msg.reply(mediaUrl).queue();
                } else {
                    msg.reply("Couldn't retrieve media for that reel, sorry!").queue();
                }
            });
        } else if (content.startsWith(ozbargain)) {
            // Extract the full URL and then the id from the ozBargain URL using a regex.
            Pattern fullUrlPattern = Pattern.compile("https?://www\\.ozbargain\\.com\\.au/node/\\d+");
            Matcher fullUrlMatcher = fullUrlPattern.matcher(content);

            if (fullUrlMatcher.find()) {
                String fullUrl = fullUrlMatcher.group();
                String dealId = fullUrl.replaceAll("\\D+", "");

 		if (dealHistoryRepository.existsById(dealId)) {
     			channel.retrieveMessageById(event.getMessageId()).queue(msg ->
                            msg.reply(":rotating_light: Repost detected :rotating_light:").queue());
                } else {
                    dealHistoryRepository.save(new PostedDeal(dealId));
                    channel.retrieveMessageById(event.getMessageId()).queue(msg ->
                            msg.reply("Thanks just bought").queue());
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
        } else if (message.getMentions().getUsers().contains(griddyBot) && promptMatcher.find()) {
            String prompt = promptMatcher.group(1);
            String channelId = channel.getId();
            channel.retrieveMessageById(message.getId()).queue(msg -> {
                conversationService.addMessage(channelId, "user", prompt);
                String response = openRouterService.ask(conversationService.getHistory(channelId));
                conversationService.addMessage(channelId, "assistant", response);
                msg.reply(response).queue();
            });
        }
    }
}
