package com.dylansbogar.griddybot;

import com.dylansbogar.griddybot.commands.*;
import com.dylansbogar.griddybot.repositories.DaylistDescriptionRepository;
import com.dylansbogar.griddybot.repositories.DaylistRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;

@Configuration
public class BotConfig {
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    private final DaylistRepository daylistRepo;
    private final DaylistDescriptionRepository daylistDescriptionRepo;

    public BotConfig(DaylistRepository daylistRepo, DaylistDescriptionRepository daylistDescriptionRepo) {
        this.daylistRepo = daylistRepo;
        this.daylistDescriptionRepo = daylistDescriptionRepo;
    }

    @Bean
    JDA jda() {
        final JDABuilder builder = JDABuilder.createDefault(BOT_TOKEN,
                EnumSet.of(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES
                ));
        JDA api = builder.build();

        // Each command class is defined here.
        api.addEventListener(
                new MessageListener(),
                new CoinflipCommand(),
                new DaylistCommand(daylistRepo, daylistDescriptionRepo),
                new EmoteCommand(),
                new MinecraftCommand(),
                new UndoDaylistCommand(),
                new YenCommand());

        // The text-content of each slash command, which shows to the user upon typing.
        api.updateCommands().addCommands(
                Commands.slash("coinflip", "Flip a coin!"),
                Commands.slash("daylist", "Add your current daylist and track your moods.")
                        .addOption(OptionType.STRING, "daylist", "The daylist")
                        .addOption(OptionType.ATTACHMENT, "file", "Image of the daylist."),
                Commands.slash("emote", "Retrieve your favourite 7TV emotes."),
                Commands.slash("minecraft", "Get the status of any Minecraft server.")
                        .addOption(OptionType.STRING, "server", "The URL of the server.", true),
                Commands.slash("undodaylist", "Undo your most recent daylist."),
                Commands.slash("yen", "Gets the current conversion rate of $1 AUD to JPY.")
        ).queue();

        return api;
    }
}
