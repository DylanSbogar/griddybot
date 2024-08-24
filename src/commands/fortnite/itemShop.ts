import {
  EmbedBuilder,
  SlashCommandBuilder,
  ChatInputCommandInteraction,
} from "discord.js";
import fetch from "node-fetch";

const FORTNITE_API_URL: string =
  "https://fortnite-api.com/v2/cosmetics/br/search?name=";
const GRIDDY_ICON_URL: string =
  "https://cdn-0.skin-tracker.com/images/fnskins/icon/fortnite-get-griddy-emote.png?ezimgfmt=rs:180x180/rscb10/ngcb10/notWebP";

// Custom URI encode function
function customURIEncode(str: string): string {
  return encodeURIComponent(str).replace(/[!'()*]/g, (c) => {
    return "%" + c.charCodeAt(0).toString(16);
  });
}

// Define the interface for the expected response from the Fortnite API
interface FortniteItemData {
  name: string;
  type: { value: string };
  images: { icon: string };
  shopHistory: string[];
}

interface FortniteApiResponse {
  data: FortniteItemData;
}

export const data = new SlashCommandBuilder()
  .setName("itemshop")
  .setDescription("Search for an item's status in the Fortnite Item Shop.")
  .addStringOption((option) =>
    option
      .setName("item")
      .setDescription("The item you wish to check the status of.")
      .setRequired(true)
  );

export async function execute(
  interaction: ChatInputCommandInteraction
): Promise<void> {
  try {
    // Grab the item and encode it.
    const item: string | null = interaction.options.getString("item");
    if (!item) {
      await interaction.reply({
        content: "You must provide an item name.",
        ephemeral: true,
      });
      return;
    }
    const encodedItem: string = customURIEncode(item);

    // Retrieve the Item information from the API.
    const request: string = `${FORTNITE_API_URL}${encodedItem}`;

    // Check if the request was successful (status code 2xx)
    if (!(await fetch(request)).ok) {
      throw new Error(`HTTP error! Status: ${(await fetch(request)).status}`);
    }

    // Parse the data into JSON, extract the final element of 'shopHistory'.
    const dataJSON: FortniteApiResponse = (await (
      await fetch(request)
    ).json()) as FortniteApiResponse;
    const lastAppearanceUTC: string | undefined =
      dataJSON.data.shopHistory.pop();

    // Create new dates in local time for both now, and the last appearance.
    const today: Date = new Date();
    const lastAppearanceLocal: Date = lastAppearanceUTC
      ? new Date(lastAppearanceUTC)
      : new Date(0); // Default to Unix epoch if lastAppearanceUTC is undefined

    // Set the hour-minute-second values of both the dates to 0, so they can be easily compared.
    today.setUTCHours(0, 0, 0, 0);
    lastAppearanceLocal.setUTCHours(0, 0, 0, 0);

    // Calculate the number of days since the last appearance
    const daysAgo: number = Math.floor(
      (today.getTime() - lastAppearanceLocal.getTime()) / (1000 * 3600 * 24)
    );

    // Build an embed using EmbedBuilder, and the received data.
    const embed = new EmbedBuilder()
      .setColor(0x0099ff)
      .setTitle(`${dataJSON.data.name} Status`)
      .setThumbnail(dataJSON.data.images.icon)
      .setAuthor({
        name: "Griddybot",
        iconURL: GRIDDY_ICON_URL,
      })
      .addFields(
        { name: "Name", value: dataJSON.data.name, inline: true },
        { name: "Type", value: dataJSON.data.type.value },
        {
          name: "Status",
          value:
            lastAppearanceLocal.getTime() === today.getTime() ? "✅" : "❌",
          inline: true,
        }
      )
      .addFields({
        name: "Last Appearance",
        value: `${lastAppearanceLocal.toLocaleDateString()} (${daysAgo} day(s) ago)`,
      });

    // Send out the embed.
    await interaction.reply({ embeds: [embed] });
  } catch (error) {
    console.error(`Error: ${error}`);
    await interaction.reply({
      content: "There was an error while executing this command!",
      ephemeral: true,
    });
  }
}
