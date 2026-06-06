package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.SocialCredit;
import com.dylansbogar.griddybot.repositories.SocialCreditRepository;
import com.dylansbogar.griddybot.utils.UserConstants;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class LeaderboardCommand extends ListenerAdapter {
    private final SocialCreditRepository creditRepo;

    public LeaderboardCommand(SocialCreditRepository creditRepo) {
        this.creditRepo = creditRepo;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("leaderboard")) return;

        event.deferReply().queue();

        List<SocialCredit> rows = creditRepo.findAllByOrderByTotalPointsDesc();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(":trophy: Social Credit Leaderboard");

        if (rows.isEmpty()) {
            embed.setDescription("No one has been evaluated yet. Check back after Monday.");
            event.getHook().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String[] medals = {":first_place:", ":second_place:", ":third_place:"};
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            SocialCredit r = rows.get(i);
            String medal = i < medals.length ? medals[i] + " " : "   ";
            String deltaNote = r.getLastWeekDelta() == 0
                    ? ""
                    : String.format("  _(last week %+d)_", r.getLastWeekDelta());
            body.append(String.format("%s**%s** — %,d pts%s%n",
                    medal, UserConstants.displayName(r.getUserId(), r.getUserTag()),
                    r.getTotalPoints(), deltaNote));
        }
        embed.setDescription(body.toString());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}
