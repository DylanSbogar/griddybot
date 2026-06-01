package com.dylansbogar.griddybot.utils;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Custom emojis from the friend group's Discord server. Each entry pairs the
// Discord emoji ID with a short description of how the group actually uses
// it — these descriptions are surfaced to the LLM when interpreting message
// reactions, so the model can tell the difference between e.g. a genuine
// laugh (:OMEGALUL:) and a sarcastic one (:LAUGH:).
//
// Future features can target specific emojis by name, e.g. ServerEmoji.DUC.id.
public enum ServerEmoji {
    OMEGALUL(  "1082876655133020220", "very funny, often stupidly so — strong indicator of genuine humour"),
    PEEPOLOVE( "886179492178034728",  "wholesome appreciation and affection — genuine love for the content"),
    SADGE(     "988031909852487780",  "genuine sadness at sad news (e.g. someone passing away)"),
    SHRUGE(    "1095214022711836752", "indifference / 'I dunno' / 'who cares'"),
    DUC(       "1082878057444036678", "flirty, thirsty, or suggestive — reacting to something hot or attractive"),
    COPIUM(    "1082876650007560324", "coping with a bad situation; denial or wishful thinking"),
    LAUGH(     "1082877301521383474", "sarcastic mocking laugh — 'oh ha ha very funny' — this is NOT genuine humour, it's dismissive"),
    DESPAIRGE( "1175759649786564718", "doom-and-gloom pessimism about world/news events — not personal sadness, more 'we are doomed'"),
    LISADGE(   "988031906828394536",  "angry or mad reaction"),
    SODGE(     "1122806123880267837", "exhausted exasperation, 'I am so over this' — done with the situation, not actually sad");

    public final String id;
    public final String description;

    ServerEmoji(String id, String description) {
        this.id = id;
        this.description = description;
    }

    private static final Map<String, ServerEmoji> BY_ID = Arrays.stream(values())
            .collect(Collectors.toMap(e -> e.id, e -> e));

    public static Optional<ServerEmoji> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
