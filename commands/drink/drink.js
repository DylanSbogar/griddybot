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

// Create a file at filePath with content if filePath does not exist.
function createFileIfNotExists(filePath, content) {
  if (!fileExists(filePath)) {
    fs.writeFileSync(filePath, content || "");
    console.log(`File created: ${filePath}`);
  } else {
    console.log(`File already exists: ${filePath}`);
  }
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName("drink")
    .setDescription("Adds a drink to your tally for the year.")
    .addIntegerOption((option) =>
      option
        .setName("number")
        .setDescription(
          "The number of drinks you wish to add to your total. (defaults to one)"
        )
        .setRequired(false)
        .setMinValue(1)
    ),

  async execute(interaction) {
    // Grab the number of drinks input by the user
    const number = interaction.options.getInteger("number") ?? 1;

    // Get user information for reading/writing from file.
    const userId = interaction.user.id;
    const username = interaction.user.username;

    // Get the current year for file purposes.
    const currentYear = new Date().getFullYear();

    // See if drink-20XX.json for the current year exists, if not then create it.
    const fileName = `drink-${currentYear}.json`;
    const filePath = path.join(folderPath, fileName);
    createFileIfNotExists(filePath, '{\n  "users": []\n}');

    // Increase the players total, and write it to file.
    increaseTotalById(userId.toString(), number);

    async function increaseTotalById(id, increaseAmount) {
      try {
        const data = JSON.parse(await fs.promises.readFile(filePath));
        let user = data.users.find((user) => user.id === id);

        // If the user wasn't found, create a new entry for them in drink-20XX.json.
        if (!user) {
          user = {
            id: id,
            name: username,
            total: 0,
          };
          data.users.push(user);
        }

        // Update their total and add it back into drink-20XX.json.
        user.total += increaseAmount;
        await fs.promises.writeFile(filePath, JSON.stringify(data, null, 2));

        // Reply to the user, notifying them of their new score.
        interaction.reply(
          `<@${userId}> has successfully updated their total, which is now ${user.total}`
        );
      } catch (err) {
        console.error(`Error: ${err}`);
        interaction.reply(`An error occurred while processing your request.`);
      }
    }
  },
};
