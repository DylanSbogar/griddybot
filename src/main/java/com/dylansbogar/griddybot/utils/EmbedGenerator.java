package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.SocialCredit;
import com.dylansbogar.griddybot.utils.SocialCreditService.WeeklyReport;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    public EmbedBuilder buildSocialCreditEmbed(WeeklyReport report, boolean debug) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor("Griddy", null, GRIDDY_ICON_URL);
        String prefix = debug ? "[DEBUG] " : "";
        embed.setTitle(prefix + ":newspaper: The Griddy Gazette — "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")));

        String standings = buildStandingsSection(report.standings());

        if (report.article() == null || report.article().isBlank()) {
            String quietMsg = "_All quiet on the Griddy front this week. No new activity to report._";
            embed.setDescription(quietMsg + "\n\n" + standings);
            return embed;
        }

        embed.setDescription(fitWithinLimit(report.article(), standings));
        return embed;
    }

    private String buildStandingsSection(List<SocialCredit> standings) {
        if (standings == null || standings.isEmpty()) {
            return "## :trophy: Standings\n_No standings yet._";
        }
        String[] medals = {":first_place:", ":second_place:", ":third_place:"};
        StringBuilder sb = new StringBuilder("## :trophy: Standings\n");
        for (int i = 0; i < standings.size(); i++) {
            SocialCredit row = standings.get(i);
            String medal = i < medals.length ? medals[i] + " " : "   ";
            String deltaNote = row.getLastWeekDelta() == 0
                    ? ""
                    : String.format("  _(%+d)_", row.getLastWeekDelta());
            sb.append(String.format("%s**%s** — %,d pts%s%n",
                    medal, row.getUserTag(), row.getTotalPoints(), deltaNote));
        }
        return sb.toString();
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
