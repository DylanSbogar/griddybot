package com.dylansbogar.griddybot;

import com.dylansbogar.griddybot.commands.*;
import com.dylansbogar.griddybot.entities.Reminder;
import com.dylansbogar.griddybot.entities.SocialCredit;
import com.dylansbogar.griddybot.repositories.*;
import com.dylansbogar.griddybot.utils.*;
import com.dylansbogar.griddybot.utils.ozbargain.Deal;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
@RequiredArgsConstructor
public class BotConfig {
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");
    private static final String CHANNEL_ID = System.getenv("CHANNEL_ID");
    private static final int MIN_UPVOTES = 5;
    private static final double MIN_UPVOTES_PER_MINUTE = 1.0;
    private static final LocalDate campaignStartDate = LocalDate.of(2026, 4, 26);

    private final DaylistRepository daylistRepo;
    private final DaylistDescriptionRepository daylistDescriptionRepo;
    private final EmoteRepository emoteRepo;
    private final YenService yenService;
    private final ReminderRepository reminderRepo;
    private final OzbargainService ozbargainService;
    private final DealHistoryRepository dealHistoryRepo;
    private final LastServerRepository lastServerRepo;
    private final OpenRouterService openRouterService;
    private final ConversationService conversationService;
    private final InstagramService instagramService;
    private final ReactionService reactionService;
    private final SocialCreditService socialCreditService;
    private final SocialCreditRepository socialCreditRepo;

    private final EmbedGenerator embedGenerator = new EmbedGenerator();

    private JDA api;

    @Bean
    JDA jda() {
        final JDABuilder builder = JDABuilder.createDefault(BOT_TOKEN,
                EnumSet.of(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES
                ));
        this.api = builder.build();

        // Each command class is defined here.
        api.addEventListener(
                new MessageListener(dealHistoryRepo, ozbargainService, openRouterService, conversationService,
                        instagramService, reactionService),
                new ModelCommand(openRouterService),
                new LastServerCommand(lastServerRepo),
                new HistoryCommand(conversationService),
                new CoinflipCommand(),
                new DaylistCommand(daylistRepo, daylistDescriptionRepo),
                new EmoteCommand(emoteRepo),
                new MinecraftCommand(),
                new YenCommand(yenService),
                new RemindMeCommand(reminderRepo),
                new LeaderboardCommand(socialCreditRepo));

        // The text-content of each slash command, which shows to the user upon typing.
        api.updateCommands().addCommands(
                Commands.slash("coinflip", "Flip a coin!"),
                Commands.slash("daylist", "Add your current daylist and track your moods.")
                        .addOption(OptionType.STRING, "daylist", "The daylist", true),
                // .addOption(OptionType.ATTACHMENT, "file", "Image of the daylist."),
                Commands.slash("emote", "Retrieve your favourite 7TV emotes.")
                        .addOption(OptionType.STRING, "emote", "The name of the emote.", true),
                Commands.slash("minecraft", "Get the status of any Minecraft server.")
                        .addOption(OptionType.STRING, "server", "The URL of the server.", true),
                Commands.slash("undodaylist", "Undo your most recent daylist."),
                Commands.slash("yen", "Gets the current conversion rate of $1 AUD to JPY."),
                Commands.slash("remindme", "Sets a reminder for a specific date.")
                        .addOption(OptionType.STRING, "date", "The date you wish to be reminded, in dd/mm/yyyy format.", true)
                        .addOption(OptionType.STRING, "message", "The message you wish to remind yourself.", true),
                Commands.slash("model", "Set the AI model used by the bot.")
                        .addOption(OptionType.STRING, "model_id", "The OpenRouter model ID (e.g. minimax/minimax-m2.7).", true),
                Commands.slash("setlastserver", "Set the date and description of the last server.")
                        .addOption(OptionType.STRING, "date", "The date in DD/MM/YY format.", true)
                        .addOption(OptionType.STRING, "description", "Description of the server.", true),
                Commands.slash("lastserver", "See how many days it's been since the last server."),
                Commands.slash("history", "Show the AI conversation summary for this channel."),
                Commands.slash("leaderboard", "Show the social credit leaderboard.")
        ).queue();

        return api;
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void checkReminders() {
        if (api != null) {
            System.out.println("Checking for any reminders...");
            // Grab today's date in the dd/MM/yyyy format.
            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            String formattedDate = today.format(formatter);
            System.out.println("Today's date: " + formattedDate);

            List<Reminder> reminders = reminderRepo.findAll();
            for (Reminder rem : reminders) {
                if (rem.getDate().equals(formattedDate)) {
                    // Reply to the original message.
                    api.getTextChannelById(CHANNEL_ID)
                            .retrieveMessageById(rem.getMessageId()).queue(msg ->
                                    msg.reply(String.format("<@%s> %s", rem.getUserId(), rem.getMessage())).queue());

                    // Delete the reminder once it's been sent out.
                    reminderRepo.deleteById(rem.getId());
                }
            }
        }
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void runSocialCreditWeekly() {
        if (SocialCreditDebug.DEBUG_MODE) return;
        runSocialCredit(false);
    }

    // Spring's @Scheduled does NOT replay a cron firing that was missed while
    // the app was down — and a Pi reboots. If the bot is offline at 9am Monday,
    // that week would otherwise never be scored, and the gap falls permanently
    // out of the next run's 7-day window. On startup we check the most recent
    // successful evaluation: if it's more than 7 days old, at least one Monday
    // was missed, so we run the weekly evaluation once to catch up. A fresh
    // install (no prior run) is left alone — there is nothing to recover, so we
    // just wait for the first Monday.
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpSocialCreditOnStartup() {
        if (SocialCreditDebug.DEBUG_MODE || api == null) return;
        // JDA logs in asynchronously; wait for it on a daemon thread so a bad
        // token (which would never become ready) can't hang app startup.
        Thread t = new Thread(() -> {
            try {
                api.awaitReady();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Instant lastRun = socialCreditRepo.findAll().stream()
                    .map(SocialCredit::getLastEvaluatedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            if (lastRun == null) {
                System.out.println("Social credit: no prior evaluation on record; "
                        + "skipping startup catch-up (waiting for the next Monday).");
                return;
            }
            if (lastRun.isAfter(Instant.now().minus(7, ChronoUnit.DAYS))) {
                System.out.println("Social credit: last evaluation " + lastRun
                        + " is within 7 days; no catch-up needed.");
                return;
            }
            System.out.println("Social credit: last evaluation " + lastRun
                    + " is over 7 days ago — a weekly run was missed. Catching up now.");
            runSocialCredit(false);
        }, "social-credit-catchup");
        t.setDaemon(true);
        t.start();
    }

    @Scheduled(cron = SocialCreditDebug.DEBUG_CRON)
    public void runSocialCreditDebug() {
        if (!SocialCreditDebug.DEBUG_MODE) return;
        runSocialCredit(true);
    }

    private void runSocialCredit(boolean debug) {
        if (api == null) return;
        MessageChannel channel = api.getTextChannelById(CHANNEL_ID);
        if (channel == null) {
            System.out.println("Social credit: channel " + CHANNEL_ID + " not found");
            return;
        }
        System.out.println("Running social credit evaluation (debug=" + debug + ")...");
        SocialCreditService.WeeklyReport report = socialCreditService.evaluateWeek(channel);
        channel.sendMessageEmbeds(embedGenerator.buildSocialCreditEmbed(report, debug).build()).queue();
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void checkOzb() {
        if (api != null) {
            Map<String, Deal> newDealsMap = ozbargainService.fetchDeals();
            for (Deal deal : newDealsMap.values()) {
                double minsSinceDealPosted = Duration.between(deal.getPubDate(), ZonedDateTime.now()).toMinutes();
                if (((double) deal.getVotesPos() / minsSinceDealPosted > MIN_UPVOTES_PER_MINUTE) && deal.getVotesPos() >= MIN_UPVOTES) {
                    if (ozbargainService.canPostDeal(deal)) {
                        api.getTextChannelById(CHANNEL_ID)
                                .sendMessageEmbeds(ozbargainService.buildOzBargainEmbed(deal).build()).queue();
                    }
                }
            }
        }
    }
}
