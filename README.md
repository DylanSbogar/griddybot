# griddybot :)

## Daily games

The bot stores copied daily-game results in PostgreSQL. It recognizes a registered game by its name in the first three nonempty lines or its play link anywhere in the result. Wordle, Connections, Valodle, Stardewdle, Narutodle, and Gregdle are registered automatically.

- `/daily result:<copied result>` saves your result and adds that game to your personal daily list if needed. A second result for the same game on the same Sydney date replaces it.
- `/brag [name:<game>]` posts today's saved result for one game, or all of today's results if no name is given. Saved results remain shareable after a game is unregistered.
- `/listalldailies` shows all registered games and their links.
- `/registerdaily name:<game> url:<https link> resultexample:<copied result>` adds another game and announces it to the channel. The example must mention its name near the top or contain its play link. Re-registering an inactive game with the same name, regardless of case, reconnects its saved results.
- `/unregisterdaily name:<game>` hides a game from the catalog and daily lists without deleting its saved results. It announces the change to the channel.
- `/addmydaily name:<game>` and `/removemydaily name:<game>` change your personal daily list.
- `/dailies` or `/d` shows links for games on your list without a saved result today.
- `/randomdaily` picks one of those unfinished games.

The day is based on when the result is submitted in `Australia/Sydney`. Puzzle numbers are stored when they can be read from the result, but they do not determine the date. A late submission therefore counts toward the day it was posted.
