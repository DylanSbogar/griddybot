const { SlashCommandBuilder } = require("discord.js");
const fetch = require("node-fetch");
require("dotenv").config();

module.exports = {
  data: new SlashCommandBuilder()
    .setName("yen")
    .setDescription("Get the current conversion rate from AUD to JPY"),

  async execute(interaction) {
    if (!interaction.isCommand()) return;

    if (interaction.commandName === "yen") {
      try {
        const response = await fetch(
          `https://v6.exchangerate-api.com/v6/${process.env.EXCHANGE_API_KEY}/latest/AUD`
        );
        const data = await response.json();

        const rate = data.conversion_rates.JPY;
        await interaction.reply(
          `The current conversion rate of 1 AUD to JPY is: ¥${rate}`
        );
      } catch (error) {
        console.error(error);
        await interaction.reply(
          "Sorry, I could not fetch the conversion rate at this time."
        );
      }
    }
  },
};
