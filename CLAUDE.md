# Griddybot

A Spring Boot Discord bot using JDA. Runs on a Raspberry Pi.

## Architecture

- **BotConfig** — wires JDA, registers all listeners and slash commands
- **MessageListener** — handles all incoming messages (not slash commands)
- Slash commands each have their own listener class under `commands/`
- Services live under `utils/`
- Database entities and repositories under `entities/` and `repositories/`

## Environment Variables

All env vars are read as static fields at startup (required for the Pi environment).

| Variable | Used by |
|---|---|
| `BOT_TOKEN` | BotConfig |
| `OPENROUTER_API_KEY` | OpenRouterService, ConversationService |
| `GOOGLE_API_KEY` | GoogleSearchService |
| `GOOGLE_SEARCH_ENGINE_ID` | GoogleSearchService |
| `EXCHANGE_RATE_API_KEY` | YenService |
| `POSTGRES_USERNAME` / `POSTGRES_PASSWORD` / `DATABASE_NAME` | application.yaml |
| `CHANNEL_ID` | BotConfig (OzBargain deal posting + reminders) |

## AI (OpenRouter)

- Model defaults to `minimax/minimax-m2.7`, changeable at runtime via `/model`
- Mention the bot (`@griddybot <prompt>`) to send a prompt
- Uses tool calling — the model decides when to call `web_search` via the Google Custom Search JSON API (100 free queries/day)
- Per-channel conversation history is kept in memory (resets on restart)
- History auto-compacts at 21 messages: the first 20 are summarised by `meta-llama/llama-3.2-3b-instruct` and stored as a rolling summary injected as a system message on every request

## Message Triggers (MessageListener)

| Trigger | Response |
|---|---|
| Message starts with `https://www.ozbargain.com.au/node/` | Repost check + embed |
| `gm` / `gn` | Echoes back |
| `thanks griddy` | "No worries <3" |
| `i love <x>` | "I love x charlie / I love x!!!" |
| `@griddybot <prompt>` | AI response via OpenRouter |

## Slash Commands

| Command | Description |
|---|---|
| `/coinflip` | Heads or tails |
| `/daylist <daylist>` | Log a Spotify daylist and track mood |
| `/undodaylist` | Remove most recent daylist entry |
| `/emote <name>` | Retrieve a 7TV emote |
| `/minecraft <server>` | Get status of a Minecraft server |
| `/yen` | AUD → JPY conversion rate |
| `/remindme <date> <message>` | Set a reminder (checked daily at 9am) |
| `/model <model_id>` | Switch the OpenRouter model at runtime |

## Database (PostgreSQL)

Used for: daylists, daylist descriptions, emotes, exchange rates, reminders, OzBargain deal history (repost detection).
