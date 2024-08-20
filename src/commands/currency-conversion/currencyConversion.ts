
import { CommandInteraction, SlashCommandBuilder } from 'discord.js';
import 'dotenv/config'
import moment from 'moment';
const { EXCHANGERATE_API_KEY } = process.env;

module.exports = {
  data: new SlashCommandBuilder()
    .setName("yen")
    .setDescription("Get the current conversion rate from AUD to JPY"),

  async execute(interaction: CommandInteraction) {
    try {
      const response = await fetch(
        `https://v6.exchangerate-api.com/v6/${EXCHANGERATE_API_KEY as string}/pair/AUD/JPY`
      );
      const data = await response.json();

      const rate = data.conversion_rate;
      const lastUpdateUtc = data.time_last_update_utc;
      const lastUpdateAest = moment(lastUpdateUtc)
        .tz("Australia/Sydney")
        .format("MMMM Do YYYY, h:mma");

      await interaction.reply(
        `The current conversion rate of 1 AUD to JPY is: ¥${rate}, as of ${lastUpdateAest} AEST.`
      );
    } catch (error) {
      console.error(error);
      await interaction.reply(
        "Sorry, I could not fetch the conversion rate at this time."
      );
    }
  },
};
