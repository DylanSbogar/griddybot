const { SlashCommandBuilder } = require("discord.js");
const fs = require("fs");
const path = require("path");

const folderPath = __dirname;

// Check if a file exists at filePath.
function fileExists(filePath) {
  try {
    fs.accessSync(filePath, fs.constants.F_OK);
    return true;
  } catch (err) {
    return false;
  }
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName("score")
    .setDescription("Returns the leaderboard for /drink.")
    .addStringOption((option) =>
      option
        .setName("year")
        .setDescription("The year you wish to retrieve drink scores for.")
        .setRequired(false)
    ),

  async execute(interaction) {
    // Get the year inputted, or the current year for legacy purposes.
    const year =
      interaction.options.getString("year") ?? new Date().getFullYear();

    // Generate the fileName and filePath.
    const fileName = `drink-${year}.json`;
    const filePath = path.join(folderPath, fileName);

    // Read the drink-20XX.json file and reply with the scoreboard.
    readFile(filePath);

    async function readFile(filePath) {
      try {
        // Check if the file exists
        if (!fileExists(filePath)) {
          interaction.reply(`No data available for the year ${year}.`);
          return;
        }

        // Read the drink-20XX.json file.
        const jsonString = await fs.promises.readFile(filePath, "utf-8");
        const data = JSON.parse(jsonString);
        const users = [...data.users];

        let result = "";
        let highestTotal = -Infinity;
        let prevTotal = Infinity;
        let rank = 1;

        // Sort users from highest to lowest score.
        users.sort((a, b) => {
          highestTotal = Math.max(highestTotal, a.total, b.total);
          if (a.total === b.total) {
            return a.id.localeCompare(b.id);
          } else {
            return b.total - a.total;
          }
        });

        users.forEach((user, index) => {
          // Set a rank for each user.
          if (user.total !== prevTotal) {
            rank = index + 1;
            prevTotal = user.total;
          }

          // Generate the scoreboard string.
          result += `#${rank}: ${user.name} - ${user.total} drink${
            user.total > 1 ? "s" : ""
          } ${rank === 1 ? "🏆" : ""}\n`;
        });

        interaction.reply(result);
      } catch (err) {
        console.error(`Error: ${err}`);
        interaction.reply(`An error occurred while processing your request.`);
      }
    }
  },
};
