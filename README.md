# griddybot

## Social Credit System

A weekly bot job that evaluates each user's Discord activity in `CHANNEL_ID`
over the past 7 days and assigns/removes "social credit" points based on what
an LLM thinks of their messages. Every Monday at 9am the bot posts a recap
embed to the channel showing each active user's updated total, the weekly
delta, and a one-sentence reason.

- Messages are **not** logged in real time — on Monday morning the bot fetches
  the week's history straight from Discord via JDA's `getIterableHistory()`.
- One LLM call per active user per week + one "newspaper editor" call that
  writes the recap article (cheap — fractions of a cent).
- Running totals live in the `social_credit` Postgres table.
- A `/leaderboard` slash command shows the current standings on demand.

### Who gets evaluated

Only the 5 regulars listed in
[`UserConstants.REGULAR_USER_IDS`](src/main/java/com/dylansbogar/griddybot/utils/UserConstants.java)
are tracked: Matt, Dylan, Duc, Brodey, Dean. Messages from anyone else
(guests, lurkers, ex-members) are ignored entirely.

**Alt accounts** (declared in `UserConstants.ACCOUNT_ALIASES`) are
attributed to the canonical user. Messages from Matt's alt account are
merged into Matt's weekly evaluation, count toward his activity, and roll
into his single scoreline. The alt never appears as its own row in the
leaderboard or article.

To target a specific person in a future feature, reference them by name from
`UserConstants` (e.g. `UserConstants.DUC_ID`) rather than hardcoding the
snowflake.

### Inactivity tracking

Regulars who barely participate get a small penalty, intended as a playful
call-out for "not being part of the fun." Computed each week:

- **Median** message count is calculated across regulars who posted at least
  once. Median (not mean) so one mega-poster doesn't drag the bar up.
- **Quiet** (`-10`): posted less than 30% of the median. Gets a normal LLM
  evaluation **plus** the penalty, and the editor is told they were quiet.
- **Absent** (`-20`): posted nothing at all. Gets a synthetic
  "WHERE WAS DUC?" headline in the article.
- **Activity floor**: nobody is flagged unless the median is at least 50
  messages. Avoids punishing genuinely slow weeks for the whole group.

Inactivity is checked in **production runs only**. Debug runs skip it (the
friend testing in a private channel doesn't have the production roster
posting there, so every regular would be marked absent every run).

### Reaction tracking

Discord reactions on a user's messages are tiered and surfaced to the LLM so
genuinely popular moments earn rewards and headlines. The LLM does all the
scoring — there's no deterministic bonus — so a cruel viral message won't be
rewarded just because it was popular.

| Tier          | Threshold | What it means                                                       |
|---------------|-----------|---------------------------------------------------------------------|
| `GOOD`        | 3+        | Modest popularity — small positive nudge + brief highlight          |
| `BETTER`      | 4+        | Notably popular — meaningful weight + dedicated highlight           |
| `LEGENDARY`   | 5+        | Major moment — strong positive delta + top-billed headline          |
| `SELF-REACT`  | Any tier  | Author reacted to their own message — cringe, overrides all tiers   |

The `SELF-REACT` override fires whenever the author appears in their own
message's reaction user list. Discord doesn't bundle reaction users with the
initial message fetch, so detecting this requires one extra REST call per
reaction type on each top-reacted message (short-circuited on first match).
~30–100 extra REST calls per weekly run, a few seconds of added latency —
fine for a once-a-week job.

- "Reactions" means effective Discord reactions: total count **minus**
  Griddybot's own reactions (`MessageReaction.isSelf()`). This stops the
  bot's bully-reactions from looking like popularity.
- 7TV / BetterTTV inline emotes are NOT reactions and don't count.
- Top-reacted messages are always promoted to highlights, guaranteeing the
  editor writes about them in the newspaper article.

#### Server emoji glossary

The friend group's custom server emojis are registered in
[`ServerEmoji`](src/main/java/com/dylansbogar/griddybot/utils/ServerEmoji.java)
with short descriptions of how each is actually used (e.g. `:OMEGALUL:` =
genuinely funny vs `:LAUGH:` = sarcastic/dismissive). When a user's
top-reacted messages contain any of these emojis, an **EMOJI GLOSSARY**
block is prepended to the per-user prompt so the LLM can interpret 5
`:OMEGALUL:` reactions as "very funny" instead of just "five reactions of
something". Add new server emojis as enum entries.

### Testing in a private server

All knobs live at the top of
[`SocialCreditDebug.java`](src/main/java/com/dylansbogar/griddybot/utils/SocialCreditDebug.java):

| Constant             | Default           | What it does                                                       |
| -------------------- | ----------------- | ------------------------------------------------------------------ |
| `DEBUG_MODE`         | `true`            | Disables the Monday cron and enables the short-interval debug cron |
| `DEBUG_CRON`         | `"0 */2 * * * *"` | Fires the evaluation every 2 minutes while debug is on             |
| `DEBUG_WINDOW_HOURS` | `1`               | Looks back only the past hour instead of 7 days                    |

You'll also need to point `CHANNEL_ID` (env var) at your private test channel.

#### Steps

1. Confirm `DEBUG_MODE = true` in `SocialCreditDebug.java`.
2. Set `CHANNEL_ID` to your test channel's ID, plus the usual env vars
   (`BOT_TOKEN`, `OPENROUTER_API_KEY`, Postgres credentials).
3. Rebuild: `./mvnw clean package`.
4. Run the bot.
5. Post some messages in the test channel.
6. Within 2 minutes the bot will:
   - Post a `[DEBUG] Weekly Social Credit Report...` embed to the channel.
   - Print `[social-credit-debug]` summary lines to stdout.
   - Write one row per evaluated user to the `social_credit_debug_log` table.

#### Where to look for logs

**Stdout** — high-level run progress:

```
[social-credit-debug] Social credit run starting at 2026-05-21T07:30:00Z (window cutoff: 2026-05-21T06:30:00Z)
[social-credit-debug] Fetched 42 messages from channel
[social-credit-debug] Active users this window: 3
[social-credit-debug]   alice#0: delta=+12, newTotal=12
[social-credit-debug]   bob#0: delta=-3, newTotal=-3
[social-credit-debug]   matt#0: delta=-25, newTotal=-25
[social-credit-debug] Social credit run finished at 2026-05-21T07:30:04Z
```

**Postgres** — full per-user evaluation details (gathered messages, full
prompt, raw LLM response, parsed delta/reasoning, any error):

```sql
SELECT run_at, user_tag, stern_bias, message_count,
       parsed_delta, parsed_reasoning, error
FROM social_credit_debug_log
ORDER BY run_at DESC;
```

For deeper digging (the full prompt or raw model output):

```sql
SELECT prompt, llm_response
FROM social_credit_debug_log
WHERE user_tag = 'matt#0'
ORDER BY run_at DESC
LIMIT 1;
```

The newspaper editor pass (the second LLM call that writes the recap article)
is logged under `user_id = '__editor__'`:

```sql
SELECT prompt, llm_response, error
FROM social_credit_debug_log
WHERE user_id = '__editor__'
ORDER BY run_at DESC
LIMIT 1;
```

**Running totals** — the actual score table (updated by both debug and prod
runs):

```sql
SELECT user_tag, total_points, last_week_delta, last_week_reason, last_evaluated_at
FROM social_credit
ORDER BY total_points DESC;
```

#### Switching off debug

When you're happy with the behaviour:

1. Set `DEBUG_MODE = false` in `SocialCreditDebug.java`.
2. Rebuild and redeploy.
3. The Monday 9am cron takes over. The `social_credit_debug_log` table stops
   receiving new rows; you can drop or truncate it any time.
