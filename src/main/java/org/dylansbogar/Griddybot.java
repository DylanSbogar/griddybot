package org.dylansbogar;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.dylansbogar.commands.*;

import java.util.EnumSet;

public class Griddybot {
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");

    public static void main(String[] args) throws Exception {
        JDABuilder builder = JDABuilder.createDefault(BOT_TOKEN,
                EnumSet.of(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES
                )
        );
        JDA api = builder.build();
        api.addEventListener(
                new MessageListener(),
                new CoinflipCommand(),
                new DaylistCommand(),
                new EmoteCommand(),
                new ItemShopCommand(),
                new MinecraftCommand(),
                new UndoDaylistCommand(),
                new YenCommand());

        api.updateCommands().addCommands(
                Commands.slash("coinflip", "Flip a coin!"),
                Commands.slash("daylist", "Add your current daylist and track your moods."),
                Commands.slash("emote", "Retrieve your favourite 7TV emotes."),
                Commands.slash("itemshop", "Retrieve the status of a costmetic from the Fortnite Item Shop."),
                Commands.slash("minecraft", "Get the status of any Minecraft server."),
                Commands.slash("undodaylist", "Undo your most recent daylist."),
                Commands.slash("yen", "Gets the current conversion rate of $1 AUD to JPY.")
                ).queue();
    }
}