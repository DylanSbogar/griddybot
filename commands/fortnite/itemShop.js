const { SlashCommandBuilder } = require("discord.js");
const { fortniteApiUrl, griddyIcon } = require("../../config.json");
const { EmbedBuilder } = require("@discordjs/builders");

function customURIEncode(str) {
  return encodeURIComponent(str).replace(/[!'()*]/g, function (c) {
    return "%" + c.charCodeAt(0).toString(16);
  });
}
module.exports = {
  data: new SlashCommandBuilder()
    .setName("itemshop")
    .setDescription("Search for an item's status in the Fortnite Item Shop.")
    .addStringOption((option) =>
      option
        .setName("item")
        .setDescription("The item you wish to check the status of.")
        .setRequired(true)
    ),

  async execute(interaction) {
    try {
      // Use dynamic import to import node-fetch
      const { default: fetch } = await import("node-fetch");

      // Grab the item and encode it.
      const item = interaction.options.getString("item");
      const encodedItem = customURIEncode(item);

      // Retrieve the Item information from the API.
      const request = fortniteApiUrl + encodedItem;
      const response = await fetch(request);

      // Check if the request was successful (status code 2xx)
      if (!response.ok) {
        throw new Error(`HTTP error! Status: ${response.status}`);
      }

      // Parse the data into JSON, extract the final element of 'shopHistory'.
      const dataJSON = await response.json();
      const lastAppearanceUTC = dataJSON.data.shopHistory.pop();

      // Create new dates in local time for both now, and the last appearance.
      const today = new Date();
      const lastAppearanceLocal = new Date(lastAppearanceUTC);

      // Set the house-minute-second values of both the dates to 0, so they can be easily compared.
      today.setUTCHours(0, 0, 0, 0);
      lastAppearanceLocal.setUTCHours(0, 0, 0, 0);

      // Build an embed using EmbedBuilder, and the received data.
      const embed = new EmbedBuilder()
        .setColor(0x0099ff)
        .setTitle(`${dataJSON.data.name} Status`)
        .setThumbnail(dataJSON.data.images.icon)
        .setAuthor({
          name: "Griddybot",
          iconUrl: griddyIcon,
        })
        .addFields(
          { name: "Name", value: dataJSON.data.name, inline: true },
          { name: "Type", value: dataJSON.data.type.value },
          {
            name: "Status",
            value:
              lastAppearanceLocal.toString() === today.toString() ? "✅" : "❌",
            inline: true,
          }
        )
        .addFields({
          name: "Last Appearance",
          value: `${lastAppearanceLocal.toLocaleDateString()} (${
            (today - lastAppearanceLocal) / (1000 * 3600 * 24)
          } day(s) ago)`,
        });

      // Send out the embed.
      interaction.reply({ embeds: [embed] });
    } catch (error) {
      console.error(`Error: ${error}`);
      interaction.reply({
        content: "There was an error while executing this command!",
        ephemeral: true,
      });
    }
  },
};
