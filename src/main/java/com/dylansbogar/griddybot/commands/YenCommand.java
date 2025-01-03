package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.utils.YenService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public class YenCommand extends ListenerAdapter {
    private final YenService yenService;

    public YenCommand(YenService yenService) {
        this.yenService = yenService;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("yen")) {
            event.deferReply().queue(); // Defer the reply whilst we fetch the current exchange rate.
            event.getHook().sendMessage(yenService.fetchExchangeRate()).queue();
        }
    }
}