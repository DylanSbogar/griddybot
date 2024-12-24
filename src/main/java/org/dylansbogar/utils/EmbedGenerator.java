package org.dylansbogar.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.List;

public class EmbedGenerator {
    private static final String GRIDDY_ICON_URL =
            "https://cdn-0.skin-tracker.com/images/fnskins/icon/fortnite-get-griddy-emote.png?ezimgfmt=rs:180x180/rscb10/ngcb10/notWebP";

    public EmbedBuilder generateEmbed(String title, String description, String thumbnailURL,
                                      List<MessageEmbed.Field> fields) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(title);
        embed.setThumbnail(thumbnailURL);
        embed.setAuthor("Griddybot", GRIDDY_ICON_URL);
        embed.setDescription(description);

        for (MessageEmbed.Field field: fields) {
            embed.addField(field);
        }

        return embed;
    }
}
