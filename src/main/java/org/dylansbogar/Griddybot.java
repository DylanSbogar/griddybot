package org.dylansbogar;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

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
        api.addEventListener(new TestListener());
    }
}