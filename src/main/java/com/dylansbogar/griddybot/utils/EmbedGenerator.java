package com.dylansbogar.griddybot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

public class EmbedGenerator {
    private static final String GRIDDY_ICON_URL =
            "https://cdn-0.skin-tracker.com/images/fnskins/icon/fortnite-get-griddy-emote.png?ezimgfmt=rs:180x180/rscb10/ngcb10/notWebP";

    private static final int EMBED_DESC_LIMIT = 4096;
    private static final String TRUNCATION_NOTE = "\n\n_…(article truncated for length)_\n";

    public EmbedBuilder generateEmbed(String title, String description, String thumbnailURL,
                                      List<MessageEmbed.Field> fields) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(title);
        embed.setThumbnail(thumbnailURL);
        embed.setAuthor("Griddy", GRIDDY_ICON_URL);
        embed.setDescription(description);

        for (MessageEmbed.Field field: fields) {
            embed.addField(field);
        }

        return embed;
    }

    private String fitWithinLimit(String article, String standings) {
        String separator = "\n\n";
        int available = EMBED_DESC_LIMIT - standings.length() - separator.length();
        String body = article;
        if (body.length() > available) {
            int cut = Math.max(0, available - TRUNCATION_NOTE.length());
            body = body.substring(0, cut) + TRUNCATION_NOTE;
        }
        return body + separator + standings;
    }
}
