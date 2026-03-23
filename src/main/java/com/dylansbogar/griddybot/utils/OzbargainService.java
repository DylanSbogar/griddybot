package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.PostedDeal;
import com.dylansbogar.griddybot.repositories.DealHistoryRepository;
import com.dylansbogar.griddybot.utils.ozbargain.Deal;
import com.dylansbogar.griddybot.utils.ozbargain.Item;
import com.dylansbogar.griddybot.utils.ozbargain.RssFeed;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.jsoup.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OzbargainService {
    private static final EmbedGenerator embedGenerator = new EmbedGenerator();
    private final DealHistoryRepository dealHistoryRepository;
    private static final String URL = "https://www.ozbargain.com.au/deals/feed";

    public Map<String, Deal> fetchDeals() {
        try {
            HttpClient client = HttpClient.newHttpClient();

            // Create a HttpRequest with the GET method.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .GET()
                    .build();

            // Send the request, and receive its response.
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return parseOzBargainFeed(response.body());
        } catch (InterruptedException | IOException e) {
            // ignore
            System.out.println("shit");
            return Map.of();
        }
    }

    public static Map<String, Deal> parseOzBargainFeed(String xmlString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH);
        try {
            XmlMapper mapper = new XmlMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            RssFeed rssFeed = mapper.readValue(xmlString, RssFeed.class);
            Map<String, Deal> deals = new HashMap<>();

            if (rssFeed.channel != null && rssFeed.channel.items != null) {
                for (Item item : rssFeed.channel.items) {
                    Deal deal = new Deal();

                    String dealId = item.link != null ? item.link.replaceAll(".*/node/", "") : "";
                    deal.setId(dealId);
                    deal.setTitle(item.title);
                    deal.setLink(item.link);
                    deal.setCategory(item.category);
                    deal.setPubDate(ZonedDateTime.parse(item.pubDate, formatter));
                    deal.setExpired(item.titleMsg != null && "expired".equalsIgnoreCase(item.titleMsg.type));

                    if (item.meta != null) {
                        deal.setVotesPos(item.meta.votesPos);
                        deal.setVotesNeg(item.meta.votesNeg);
                        deal.setDealUrl(item.meta.url);
                        deal.setImageUrl(item.meta.image);
                        deal.setCommentCount(item.meta.commentCount);
                    }

                    if (!dealId.isEmpty()) {
                        deals.put(dealId, deal);
                    }
                }
            }

            return deals;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of(); // Return empty map on failure
        }
    }

    public EmbedBuilder buildOzBargainEmbed(Deal deal) {
        List<MessageEmbed.Field> fields = new ArrayList<>();
        fields.add(new MessageEmbed.Field("Upvotes", String.valueOf(deal.getVotesPos()), true));
        fields.add(new MessageEmbed.Field("Comments", String.valueOf(deal.getCommentCount()), true));
        fields.add(new MessageEmbed.Field("Link", deal.getLink(), false));

        return embedGenerator.generateEmbed(":rotating_light: Hot Bargain Alert :rotating_light:", deal.getTitle(), deal.getImageUrl(), fields);
    }

    public boolean canPostDeal(Deal deal) {
        if (dealHistoryRepository.existsById(deal.getId())) {
            return false;
        }
        dealHistoryRepository.save(new PostedDeal(deal.getId()));
        return true;
    }

    public EmbedBuilder parseOzBargainLink(String url) throws IOException {
            Document document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            // Fetch the desired elements from the HTML.
            String title = document.selectFirst("meta[property=og:title]").attr("content");

            String submittedText = document.selectFirst("div.submitted").text();
            Pattern submittedPattern = Pattern.compile("\\d{2}/\\d{2}/\\d{4}\\s*-\\s*\\d{2}:\\d{2}");
            Matcher submittedMatcher = submittedPattern.matcher(submittedText);
            String submitted = submittedMatcher.find() ? submittedMatcher.group() : "Not Found";

            return embedGenerator.generateEmbed(
                    title,
                    String.format("Posted on %s", submitted),
                    "https://static.wikia.nocookie.net/fortnite/images/8/84/Get_Griddy_-_Emote_-_Fortnite.png/revision/latest?cb=20210427105200",
                    new ArrayList<>()
            );
    }
}
