const { SlashCommandBuilder } = require("discord.js");

// Utility function to create a delay using Promises
function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName("coinflip")
    .setDescription("Flip a coin!"),
  async execute(interaction) {
    if (!interaction.isCommand()) return;

    if (interaction.commandName === "coinflip") {
      const coinMessage = await interaction.reply("Flipping the coin...");
      const results = ["Heads", "Tails"];

      // Determine a random number of times the coin will flip.
      const flips = Math.floor(Math.random() * (15 - 5 + 1)) + 5;

      // Simulate animation by changing the message quickly
      for (let i = 0; i < flips; i++) {
        await wait(250); // Adjust the delay as needed
        await coinMessage.edit(
          `The coin is flipping... **${
            results[Math.floor(Math.random() * results.length)]
          }**`
        );
      }

      await wait(500);
      const result = results[Math.floor(Math.random() * results.length)];
      await coinMessage.edit(`🪙 The coin landed on: **${result}** 🪙`);
    }
  },
};
