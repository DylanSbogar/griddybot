const { SlashCommandBuilder } = require("discord.js");
const fetch = require("node-fetch");
const { exchangeApiKey } = require("../../config.json");

module.exports = {
  data: new SlashCommandBuilder()
    .setName("yen")
    .setDescription("Get the current conversion rate from AUD to JPY"),

  async execute(interaction) {
    try {
      const response = await fetch(
        `https://v6.exchangerate-api.com/v6/${exchangeApiKey}/pair/AUD/JPY`
      );
      const data = await response.json();

      const rate = data.conversion_rate;
      await interaction.reply(
          `The cuwwent convewsion wate of 1 AUD to JPY is: ¥${rate}`
        );
    } catch (error) {
      console.error(error);
      await interaction.reply(
        "Sowwy, I could not fetch the convewsion wate at this time."
      );
    }
  },
};
