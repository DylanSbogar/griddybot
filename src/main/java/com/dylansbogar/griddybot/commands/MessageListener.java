package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.PostedDeal;
import com.dylansbogar.griddybot.repositories.DealHistoryRepository;
import com.dylansbogar.griddybot.utils.*;
import net.dv8tion.jda.api.entities.*;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageListener extends ListenerAdapter {
    private static final String ozbargain = "https://www.ozbargain.com.au/node/";
    private static final Pattern thanksPattern = Pattern.compile("thanks griddy", Pattern.CASE_INSENSITIVE);
    private static final Pattern lovePattern = Pattern.compile("^i love (.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern mediaPattern = Pattern.compile(
            "https?://(?:www\\.)?(?:instagram\\.com/reels?/[A-Za-z0-9_-]+/?(?:\\?[^\\s]*)?|(?:x\\.com|fxtwitter\\.com)/[A-Za-z0-9_]{1,15}/status/\\d+/?(?:\\?[^\\s]*)?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern sixSevenPattern = Pattern.compile("(?:67)|(?:\\b(?:6|six)\\b.*\\b(?:7|seven)\\b)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern sevenSixPattern = Pattern.compile("(?:76)|(?:\\b(?:7|seven)\\b.*\\b(?:6|six)\\b)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);


    public final DealHistoryRepository dealHistoryRepository;
    private final OpenRouterService openRouterService;
    private final ConversationService conversationService;
    private final MediaService mediaService;

    public final List<String> jumpscares = List.of(
            "https://klipy.com/gifs/deepfriedwoomy-1",
            "https://klipy.com/gifs/bonnie-fnaf-31",
            "https://klipy.com/gifs/oceanmam-fnaf",
            "https://klipy.com/gifs/nightmare-foxy-fnaf",
            "https://klipy.com/gifs/fnaf-freddy-40",
            "https://klipy.com/gifs/fnaf-4-jumpscare",
            "https://klipy.com/gifs/toy-bonnie-jumpscare",
            "https://klipy.com/gifs/fnaf-2-balloon-boy",
            "https://klipy.com/gifs/matpat-game-theory-2"
    );

    public MessageListener(DealHistoryRepository dealHistoryRepository, OpenRouterService openRouterService,
                           ConversationService conversationService, MediaService mediaService) {
        this.dealHistoryRepository = dealHistoryRepository;
        this.openRouterService = openRouterService;
        this.conversationService = conversationService;
        this.mediaService = mediaService;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (ThreadLocalRandom.current().nextDouble() < 0.005) {
            // Fetch a random .gif from the jumpscare list, and send it.
            String jumpscare = jumpscares.get(ThreadLocalRandom.current().nextInt(jumpscares.size()));
            channel.sendMessage(jumpscare).queue();
        }

        // Ensure griddybot does not respond to itself.
        if (event.getAuthor().isBot()) return;

        SelfUser griddyBot = event.getJDA().getSelfUser();
        Message message = event.getMessage();
        String content = message.getContentRaw();
        // Clean content to ignore URLs, emojis, and mentions
        String cleaned = content
                .replaceAll("https?://\\S+", "")
                .replaceAll("<a?:\\w+:\\d+>", "")
                .replaceAll("<@!?\\d+>|<@&\\d+>", "")
                .trim();

        Matcher thanks = thanksPattern.matcher(content);
        Matcher love = lovePattern.matcher(content);
        Matcher mediaMatcher = mediaPattern.matcher(content);

        Pattern mePattern = Pattern.compile("\\bme\\b", Pattern.CASE_INSENSITIVE);
        Matcher meMatcher = mePattern.matcher(event.getMessage().getContentRaw());

        Pattern promptPattern = Pattern.compile("<@!?" + griddyBot.getId() + ">\\s*(.*)");
        Matcher promptMatcher = promptPattern.matcher(event.getMessage().getContentRaw());
        if (meMatcher.find() && ThreadLocalRandom.current().nextDouble() < 0.05) {
            channel.sendMessage("https://klipy.com/gifs/gongaga-me").queue();
        } else if (mediaMatcher.find()) {
            String url = mediaMatcher.group();
            channel.retrieveMessageById(event.getMessageId()).queue(msg -> {
                String formattedMessage = "";
                if (url.contains("x.com")) {
                    msg.delete().queue();
                    String newUrl = msg.getContentRaw().replace("x.com", "gtnhsucks.xyz")
                            .replaceAll("(https?://gtnhsucks\\.xyz/\\S+?/status/\\d+)(\\?\\S*)?", "$1/en$2");
                    formattedMessage = String.format("> %s\n message posted by <@!%s> as %s",
                            newUrl,
                            event.getAuthor().getId(),
                            event.getMessage().getTimeCreated().atZoneSameInstant(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("hh:mm a"))
                    );
                } else {
                    String mediaUrl = mediaService.getMediaUrl(url);
                    if (mediaUrl != null) {
                        msg.delete().queue();
                        formattedMessage = String.format("> %s\n posted by <@!%s> at %s",
                                mediaMatcher.replaceAll(mediaUrl),
                                event.getAuthor().getId(),
                                event.getMessage().getTimeCreated().atZoneSameInstant(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("hh:mm a")));
                    } else {
                        msg.reply("Unable to retrieve media :/").queue();
                        return;
                    }
                }

                // Determine whether to reply if applicable, or just send a raw message.
                Message referencedMsg = msg.getReferencedMessage();
                if (referencedMsg != null) {
                    referencedMsg.reply(formattedMessage).queue();
                } else {
                    msg.getChannel().sendMessage(formattedMessage).queue();
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
        } else if (sixSevenPattern.matcher(cleaned).find()) {
            channel.sendMessage("https://tenor.com/view/bosnov-67-bosnov-67-67-meme-gif-16727368109953357722").queue();
        } else if (sevenSixPattern.matcher(cleaned).find()) {
            channel.sendMessage("https://tenor.com/view/staring-press-close-staredown-train-gif-22975756").queue();
        } else if (message.getMentions().getUsers().contains(griddyBot) && promptMatcher.find()) {
            String prompt = promptMatcher.group(1);
            String channelId = channel.getId();
            channel.retrieveMessageById(message.getId()).queue(msg -> {
                conversationService.addMessage(channelId, "user", prompt);
                String response = openRouterService.ask(conversationService.getHistory(channelId));
                conversationService.addMessage(channelId, "assistant", response);
                msg.reply(response).queue();
            });
        } else if (message.getType().equals(MessageType.INLINE_REPLY)) {
            Message referencedMessage = message.getReferencedMessage();
            if (referencedMessage == null) {
                return; // Should be impossible
            }
        }
    }
}
