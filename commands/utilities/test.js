const { SlashCommandBuilder } = require("discord.js");

module.exports = {
  data: new SlashCommandBuilder()
    .setName("test")
    .setDescription("testing bot reading slash commands"),
  async execute(interaction) {
    await interaction.reply("New Response!");
  },
};
