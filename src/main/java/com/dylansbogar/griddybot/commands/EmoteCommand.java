package com.dylansbogar.griddybot.commands;

import com.dylansbogar.griddybot.entities.Emote;
import com.dylansbogar.griddybot.repositories.EmoteRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

public class EmoteCommand extends ListenerAdapter {
    private final EmoteRepository emoteRepo;

    // 7TV's public GraphQL API — a single endpoint you POST a query + variables to.
    private static final String SEVENTV_GQL = "https://7tv.io/v3/gql";
    private static final String SEVENTV_QUERY =
            "query($query: String!, $filter: EmoteSearchFilter) { emotes(query: $query, limit: 1, filter: $filter) { items { id name } } }";

    // CDN templates. New emotes come from 7TV; legacy cached rows are BetterTTV.
    private static final String SEVENTV_CDN = "https://cdn.7tv.app/emote/%s/4x.webp";
    private static final String BTTV_CDN = "https://cdn.betterttv.net/emote/%s/3x.webp";
    private static final String SEVENTV_EMOTE_URL_REGEX = "^https://7tv\\.app/emotes/([A-Za-z0-9]+)$";

    // Marker stored against emotes fetched from 7TV. Legacy rows have a null source.
    private static final String SOURCE_7TV = "7TV";

    public EmoteCommand(EmoteRepository emoteRepo) {
        this.emoteRepo = emoteRepo;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("emote")) {
            event.deferReply().queue(); // Defer the reply whilst we fetch the emote.

            // Retrieve the input options.
            OptionMapping emoteIn = event.getOption("emote");
            OptionMapping refetchIn = event.getOption("refetch");
            OptionMapping overrideIn = event.getOption("override");

            if (emoteIn == null) {
                event.getHook().sendMessage("Please enter an emote name.").queue();
                return;
            }

            String emote = emoteIn.getAsString().toLowerCase();
            boolean refetch = refetchIn != null && refetchIn.getAsBoolean();

            // Attempt to fetch the emote's id from the database.
            Optional<Emote> storedEmote = emoteRepo.findByName(emote);
            String overrideUrl = overrideIn != null ? overrideIn.getAsString() : null;
            boolean override = overrideUrl != null && !overrideUrl.isBlank();

            if (storedEmote.isEmpty() || refetch || override) {
                fetchEmote(emote, event, overrideUrl);
            } else {
                event.getHook().sendMessage(buildUrl(storedEmote.get())).queue();
            }
        }
    }

    private void fetchEmote(String emote, SlashCommandInteractionEvent event, String overrideUrl) {
        try {
            // If the user intends to override an emote (existing or otherwise)
            if (overrideUrl != null) {
                if (!overrideUrl.matches(SEVENTV_EMOTE_URL_REGEX)) {
                    event.getHook().sendMessage("Invalid 7TV emote URL.").queue();
                    return;
                }

                // Extract emoteId from the url.
                String emoteId = overrideUrl.replaceFirst(SEVENTV_EMOTE_URL_REGEX, "$1");

                Emote newEmote = Emote.builder()
                        .name(emote)
                        .emoteId(emoteId)
                        .source(SOURCE_7TV)
                        .build();
                emoteRepo.save(newEmote);

                event.getHook().sendMessage(buildUrl(newEmote)).queue();
                return;
            }

            HttpClient client = HttpClient.newHttpClient();

            // A GraphQL request is a JSON body carrying the query string plus the user's
            // input as separate variables (never string-concatenated into the query).
            String payload = new JSONObject()
                    .put("query", SEVENTV_QUERY)
                    .put("variables", new JSONObject()
                            .put("query", emote)
                            .put("filter", new JSONObject().put("exact_match", true)))
                    .toString();

            // Create a HttpRequest with the POST method.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SEVENTV_GQL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            // Send the request, and receive its response.
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                event.getHook().sendMessage(String.format("There was an error fetching the %s emote", emote)).queue();
                return;
            }

            // GraphQL returns HTTP 200 even for query errors, placing them in an "errors" array
            // alongside a null "data" — so confirm data is present before reading it.
            JSONObject responseBody = new JSONObject(response.body());
            if (!responseBody.has("data") || responseBody.isNull("data")) {
                event.getHook().sendMessage(String.format("There was an error fetching the %s emote", emote)).queue();
                return;
            }

            JSONArray items = responseBody.getJSONObject("data").getJSONObject("emotes").getJSONArray("items");

            // If an emote was not found, notify the user.
            if (items.isEmpty()) {
                event.getHook().sendMessage(String.format("No emote found with the name %s", emote)).queue();
                return;
            }

            String emoteId = items.getJSONObject(0).getString("id");

            // Save the new emote to the database, tagged as 7TV so we build the right CDN URL later.
            Emote newEmote = Emote.builder()
                    .name(emote)
                    .emoteId(emoteId)
                    .source(SOURCE_7TV)
                    .build();
            emoteRepo.save(newEmote);

            event.getHook().sendMessage(String.format(SEVENTV_CDN, emoteId)).queue();
        } catch (Exception e) {
            // Catch everything (including JSONException, a RuntimeException) so the deferred
            // reply is always answered and the command never hangs on "thinking...".
            event.getHook().sendMessage(String.format("There was an error fetching the %s emote", emote)).queue();
        }
    }

    // Builds the CDN URL for a cached emote based on where its id came from.
    // Legacy rows (source == null) are BetterTTV emotes; everything new is 7TV.
    private String buildUrl(Emote emote) {
        if (SOURCE_7TV.equals(emote.getSource())) {
            return String.format(SEVENTV_CDN, emote.getEmoteId());
        }
        return String.format(BTTV_CDN, emote.getEmoteId());
    }
}
