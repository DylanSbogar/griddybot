package com.dylansbogar.griddybot.utils;

import java.util.List;
import java.util.Map;

// Central registry of user IDs for the bot. Named constants here so any
// future feature that targets a specific person can reference them by name
// instead of repeating raw snowflakes everywhere.
public final class UserConstants {
    // The 5 humans in the regular friend group.
    public static final String MATT_ID    = "187817424337240064";
    public static final String DYLAN_ID   = "231700435071664128";
    public static final String DUC_ID     = "175880304525836288";
    public static final String BRODEY_ID  = "95482653167333376";
    public static final String DEAN_ID    = "234541539190243328";

    // Matt's alt account. Not a separate person — kept here for the bully
    // features that target both of his accounts.
    public static final String OTHER_MATT_ID = "1265821669985878208";

    // Maps alt/alias account IDs to their canonical user ID. The social
    // credit system attributes messages from an alias to the canonical user
    // — same score line, same headlines, same evaluation. The alias never
    // appears in the recap or leaderboard as a separate person.
    public static final Map<String, String> ACCOUNT_ALIASES = Map.of(
            OTHER_MATT_ID, MATT_ID
    );

    // Returns the canonical user ID for a given snowflake. If the input is
    // a known alias, returns the primary account; otherwise returns input
    // unchanged.
    public static String canonicalUserId(String userId) {
        return ACCOUNT_ALIASES.getOrDefault(userId, userId);
    }

    // Users targeted by the "bully" features in MessageListener.
    public static final List<String> BULLY_IDS = List.of(MATT_ID, OTHER_MATT_ID);

    // Users evaluated more strictly by the social credit system.
    public static final List<String> STERN_BIAS_IDS = List.of(MATT_ID);

    // The 5 regulars whose Discord activity feeds the social credit system.
    // Anyone outside this list (guests, lurkers, ex-members) is ignored by
    // the system entirely — their messages don't get evaluated and they
    // never appear in the recap or leaderboard.
    public static final List<String> REGULAR_USER_IDS = List.of(
            MATT_ID, DYLAN_ID, DUC_ID, BRODEY_ID, DEAN_ID
    );

    private UserConstants() {}
}
