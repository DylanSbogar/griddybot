package com.dylansbogar.griddybot.utils;

import com.dylansbogar.griddybot.entities.ExchangeRate;
import com.dylansbogar.griddybot.utils.ozbargain.Deal;
import com.dylansbogar.griddybot.utils.ozbargain.Item;
import com.dylansbogar.griddybot.utils.ozbargain.RssFeed;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class OzbargainService {
    private static final String URL = "https://www.ozbargain.com.au/deals/feed";

    private final Map<String, Deal> dealsMap = new HashMap<>();

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

    // bruh i overthank this hard
//    private String processDeals() {
//        Map<String, Deal> newDealsMap = fetchDeals();
//        for (Map.Entry<String, Deal> dealEntry : newDealsMap.entrySet()) {
//            // If we cache deals... else this could have been just a list lmao
////            String id = dealEntry.getKey();
//            Deal deal = dealEntry.getValue();
////
////            Deal existingDeal = dealsMap.putIfAbsent(id, deal);
////            if (existingDeal != null) {
////
////            }
//
//            long minsSinceDealPosted = Duration.between(deal.getPubDate(), ZonedDateTime.now()).toMinutes();
//        if (deal.getVotesPos() / minsSinceDealPosted  > UPVOTES_PER_MINUTE) {
//
//        }
//
//
//        }
//    }

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
}
