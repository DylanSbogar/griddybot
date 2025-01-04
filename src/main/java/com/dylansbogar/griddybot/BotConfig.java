package com.dylansbogar.griddybot;

import com.dylansbogar.griddybot.commands.*;
import com.dylansbogar.griddybot.entities.Reminder;
import com.dylansbogar.griddybot.repositories.DaylistDescriptionRepository;
import com.dylansbogar.griddybot.repositories.DaylistRepository;
import com.dylansbogar.griddybot.repositories.EmoteRepository;
import com.dylansbogar.griddybot.repositories.ReminderRepository;
import com.dylansbogar.griddybot.utils.YenService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

@Configuration
public class BotConfig {
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");
    private static final String CHANNEL_ID = System.getenv("CHANNEL_ID");

    private final DaylistRepository daylistRepo;
    private final DaylistDescriptionRepository daylistDescriptionRepo;
    private final EmoteRepository emoteRepo;
    private final YenService yenService;
    private final ReminderRepository reminderRepo;

    private JDA api;

    public BotConfig(DaylistRepository daylistRepo,
                     DaylistDescriptionRepository daylistDescriptionRepo,
                     EmoteRepository emoteRepo,
                     YenService yenService,
                     ReminderRepository reminderRepo) {
        this.daylistRepo = daylistRepo;
        this.daylistDescriptionRepo = daylistDescriptionRepo;
        this.emoteRepo = emoteRepo;
        this.yenService = yenService;
        this.reminderRepo = reminderRepo;
    }

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
                new MessageListener(),
                new CoinflipCommand(),
                new DaylistCommand(daylistRepo, daylistDescriptionRepo),
                new EmoteCommand(emoteRepo),
                new MinecraftCommand(),
                new YenCommand(yenService),
                new RemindMeCommand(reminderRepo));

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
                        .addOption(OptionType.STRING, "message", "The message you wish to remind yourself.", true)
        ).queue();

        return api;
    }

    @Scheduled(cron = "0 0 11 * * *")
    public void checkConversionRate() {
        if (api != null) {
            api.getTextChannelById(CHANNEL_ID).sendMessage(yenService.fetchExchangeRate()).queue();
        }
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
}
