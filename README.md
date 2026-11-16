# griddybot :)

## Daily games

The bot stores copied daily-game results in PostgreSQL. It recognizes a registered game by its name in the first three nonempty lines or its play link anywhere in the result. Wordle, Connections, Valodle, Stardewdle, Narutodle, and Gregdle are registered automatically.

- `/daily result:<copied result>` saves your result. A second result for the same game on the same Sydney date replaces it.
- `/listdailies` shows all registered games and their links.
- `/registerdaily name:<game> url:<https link> resultexample:<copied result>` adds another game. The example must mention its name near the top or contain its play link. The game is then available to everyone.
- `/adddaily name:<game>` and `/removedaily name:<game>` change your personal daily list.
- `/dailies` shows links for games on your list without a saved result today.
- `/randomdaily` picks one of those unfinished games.

The day is based on when the result is submitted in `Australia/Sydney`. Puzzle numbers are stored when they can be read from the result, but they do not determine the date. A late submission therefore counts toward the day it was posted.
