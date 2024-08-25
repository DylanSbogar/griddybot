import { ChatInputCommandInteraction, SlashCommandBuilder } from "discord.js";

export const data = new SlashCommandBuilder()
  .setName("ding")
  .setDescription("Description of the Command.");
export async function execute(interaction: ChatInputCommandInteraction) {
  try {
    await interaction.reply("Pong!");
  } catch (error) {
    console.error(error);
  }
}