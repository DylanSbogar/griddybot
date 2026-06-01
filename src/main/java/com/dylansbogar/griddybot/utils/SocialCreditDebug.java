package com.dylansbogar.griddybot.utils;

// ============================================================
// SOCIAL CREDIT — DEBUG CONFIGURATION
//
// Flip DEBUG_MODE to false for production.
//
// When DEBUG_MODE is true:
//   - The weekly Monday cron is DISABLED.
//   - A short-interval debug cron runs instead (DEBUG_CRON).
//   - Message lookback window is DEBUG_WINDOW_HOURS (not 7 days),
//     so testing doesn't require a week of seeded messages.
//   - Per-user evaluation details (gathered messages, full prompt,
//     raw LLM response, parsed result, errors) are written to the
//     `social_credit_debug_log` Postgres table.
//   - The Discord embed posted to the channel is prefixed with
//     [DEBUG] so it's obvious it's a test run.
//   - Run start/end and per-user summaries are printed to stdout
//     (useful for `tail -f` on the bot's log).
//
// These must be compile-time constants so they can be referenced
// from @Scheduled annotations. Rebuild after changing.
// ============================================================
public final class SocialCreditDebug {
    public static final boolean DEBUG_MODE = false;

    // Every 2 minutes. Examples:
    //   "0 */2 * * * *"  — every 2 minutes
    //   "0 */5 * * * *"  — every 5 minutes
    //   "30 * * * * *"   — at 30 seconds past every minute
    public static final String DEBUG_CRON = "0 */2 * * * *";

    // How far back to look for messages when in debug mode.
    // Keep this short so a quick test in a fresh channel still
    // returns something interesting.
    public static final int DEBUG_WINDOW_HOURS = 1;

    private SocialCreditDebug() {}
}
