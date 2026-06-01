package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.PostedDeal;
import com.dylansbogar.griddybot.repositories.DealHistoryRepository;
import com.dylansbogar.griddybot.utils.*;
import net.dv8tion.jda.api.entities.*;
import static com.dylansbogar.griddybot.utils.UserConstants.BULLY_IDS;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
    private static final List<String> ANNOYING_QUESTIONS = List.of("hey %s, how are you?", "hey %s, quick question",
            "hey %s, you free?", "hi %s, how was your weekend?");

    public final DealHistoryRepository dealHistoryRepository;
    private final OzbargainService ozbargainService;
    private final OpenRouterService openRouterService;
    private final ConversationService conversationService;
    private final InstagramService instagramService;
    private final ReactionService reactionService;

    Random random = new Random();
    private final List<Message> heldMessages = new ArrayList<>();
    private List<Message> blockingQuestion = new ArrayList<>();

    public MessageListener(DealHistoryRepository dealHistoryRepository, OzbargainService ozbargainService,
                           OpenRouterService openRouterService, ConversationService conversationService,
                           InstagramService instagramService, ReactionService reactionService) {
        this.dealHistoryRepository = dealHistoryRepository;
        this.ozbargainService = ozbargainService;
        this.openRouterService = openRouterService;
        this.conversationService = conversationService;
        this.instagramService = instagramService;
        this.reactionService = reactionService;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
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
        MessageChannel channel = event.getChannel();

        Matcher thanks = thanksPattern.matcher(content);
        Matcher love = lovePattern.matcher(content);
        Matcher instagramMatcher = instagramReelPattern.matcher(content);

        Pattern promptPattern = Pattern.compile("<@!?" + griddyBot.getId() + ">\\s*(.*)");
        Matcher promptMatcher = promptPattern.matcher(event.getMessage().getContentRaw());

        if(BULLY_IDS.contains(event.getAuthor().getId())) {
            if(!blockingQuestion.isEmpty()) {
                Message referencedMessage = message.getReferencedMessage();
                if(message.getType().equals(MessageType.INLINE_REPLY) && blockingQuestion.contains(referencedMessage)) {
                    reactionService.reactToMessage(event.getMessage());

                    String question = referencedMessage.getContentRaw();
                    String prompt = "You are being very annoying. If this message does not clearly answer the question \"" +  question + "\" " +
                            "please rephrase and repeat the question (limited to 50 characters). If it does answer the question, output only \"TRUE\"\n.";

                    ConversationHistory conversationHistory = new ConversationHistory();
                    conversationHistory.getMessages().add(new JSONObject().put("role", "user").put("content", message.getContentRaw()));
                    String griddyReply = openRouterService.ask(conversationHistory, prompt, "minimax/minimax-m2.7");

                    if(griddyReply.contains("TRUE")) {
                        for(var heldMessage: heldMessages) {
                            heldMessage.getReferencedMessage().reply(String.format("At %s, %s sent: %s", heldMessage.getTimeCreated(),
                                    heldMessage.getAuthor().getAsTag(), heldMessage.getContentRaw())).queue();
                        }
                        blockingQuestion.clear();
                        heldMessages.clear();
                    } else {
                        message.reply(griddyReply).queue(blockingQuestion::add);
                    }
                } else {
                    message.delete().queue();
                    channel.sendMessage("^").queue();
                    return;
                }

            }
        }

        if (instagramMatcher.find()) {
            if(BULLY_IDS.contains(event.getAuthor().getId())) {
                channel.sendMessage("Owops, sowwy, I dont hewp buwwies >:( . Undew you want to add me to hades-homosexuaws? <:duc:1082878057444036678>").queue();
                return;
            }

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
            if(BULLY_IDS.contains(event.getAuthor().getId())) {
                if(content.equalsIgnoreCase("gm")) {
                    channel.sendMessage("Bad morning >:(").queue();
                } else {
                    channel.sendMessage("Bad night >:(").queue();
                }
                return;
            }
            channel.sendMessage(content).queue();
        } else if (thanks.find()) {
            if(BULLY_IDS.contains(event.getAuthor().getId())) {
                channel.sendMessage("...").queue();
            } else {
                channel.sendMessage("No worries <3").queue();
            }
        } else if (love.find()) {
            if(BULLY_IDS.contains(event.getAuthor().getId())) {
                channel.sendMessage("yikes, I dont love anything you do >:(").queue();
            } else {
                String lovedThing = love.group(1);
                if (lovedThing.length() > 1950) { // To ensure we don't surpass Discords maximum message length.
                    channel.sendMessage("yikes, I don't love all that").queue();
                } else {
                    channel.sendMessage(String.format("I love %s charlie\nI love %s!!!", lovedThing, lovedThing)).queue();
                }
            }
	} else if(cleaned.matches("(?:67)|(?:\\b(?:6|six)\\b.*\\b(?:7|seven)\\b)")) {
            channel.sendMessage("https://tenor.com/view/bosnov-67-bosnov-67-67-meme-gif-16727368109953357722").queue();
        } else if(cleaned.matches("(?:76)|(?:\\b(?:7|seven)\\b.*\\b(?:6|six)\\b)")) {
	    channel.sendMessage("https://tenor.com/view/staring-press-close-staredown-train-gif-22975756").queue();
        } else if (message.getMentions().getUsers().contains(griddyBot) && promptMatcher.find()) {
            if(BULLY_IDS.contains(event.getAuthor().getId())) {
                channel.sendMessage("Owops, sowwy, I dont hewp buwwies >:( . Undew you want to add me to hades-homosexuaws? <:duc:1082878057444036678>").queue();
                return;
            }
            String prompt = promptMatcher.group(1);
            String channelId = channel.getId();
            channel.retrieveMessageById(message.getId()).queue(msg -> {
                conversationService.addMessage(channelId, "user", prompt);
                String response = openRouterService.ask(conversationService.getHistory(channelId));
                conversationService.addMessage(channelId, "assistant", response);
                msg.reply(response).queue();
            });
        } else if(message.getType().equals(MessageType.INLINE_REPLY)) {
            Message referencedMessage = message.getReferencedMessage();

            if(referencedMessage == null) {
                return; // Should be impossible
            }

            if(BULLY_IDS.contains(referencedMessage.getAuthor().getId())) {
                heldMessages.add(message);
                message.delete().queue();

                if(blockingQuestion.isEmpty()) {
                    String question = ANNOYING_QUESTIONS.get(random.nextInt(0, ANNOYING_QUESTIONS.size()));
                    channel.sendMessage( String.format(question, referencedMessage.getAuthor().getAsTag())).queue(blockingQuestion::add);
                }
            }
        }
    }
}
