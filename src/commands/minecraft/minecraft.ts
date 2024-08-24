import {
  SlashCommandBuilder,
  EmbedBuilder,
  ChatInputCommandInteraction,
} from "discord.js";
import fetch from "node-fetch";

const MINECRAFT_URL: string = "https://api.mcsrvstat.us/2/";
const MINECRAFT_ICON_URL: string = "https://api.mcsrvstat.us/icon/";
const GRIDDY_ICON_URL: string =
  "https://cdn-0.skin-tracker.com/images/fnskins/icon/fortnite-get-griddy-emote.png?ezimgfmt=rs:180x180/rscb10/ngcb10/notWebP";

interface MinecraftResponse {
  online: boolean;
  players: {
    online: number;
    list?: string[];
  };
}

export const data = new SlashCommandBuilder()
  .setName("minecraft")
  .setDescription("Gets the status of any given Minecraft Server.")
  .addStringOption((option) =>
    option
      .setName("address")
      .setDescription("The address of the server.")
      .setRequired(true)
  );

export async function execute(
  interaction: ChatInputCommandInteraction
): Promise<void> {
  // Grab the server address from the command details.
  const address: string | null = interaction.options.getString("address");
  if (!address) {
    await interaction.reply("You must provide a server address.");
    return;
  }

  const iconRequest: string = `${MINECRAFT_ICON_URL}${address}`;
  const serverRequest: string = `${MINECRAFT_URL}${address}`;

  // Fetch the server status/information from the API.
  const response = await fetch(serverRequest);

  if (response.ok) {
    // Parse the response into a JSON object.
    const data: MinecraftResponse =
      (await response.json()) as MinecraftResponse;

    // Set initial variable values.
    let serverStatus: string = "Offline ❌";
    let playerCount: string = "0";
    let playerList: string[] = [];

    // If the server is online, update the aforementioned variables.
    if (data.online) {
      serverStatus = "Online ✅";
      playerCount = data.players.online.toString();
      if (data.players.list) {
        playerList = data.players.list;
      }
    }

    // Build an embed using EmbedBuilder and the parsed data.
    const embed = new EmbedBuilder()
      .setColor(0x0099ff)
      .setTitle("Minecraft Server Status")
      .setAuthor({
        name: "GriddyBot",
        iconURL: GRIDDY_ICON_URL,
      })
      .setDescription(address)
      .setThumbnail(iconRequest)
      .addFields(
        {
          name: "Status",
          value: serverStatus,
          inline: true,
        },
        {
          name: "Online",
          value: playerCount,
          inline: true,
        }
      )
      .setTimestamp();

    // Add a field to display the playerList, only if it exists.
    if (playerList.length > 0) {
      embed.addFields({
        name: "Players",
        value: playerList.join(", "),
      });
    }

    // Send out the embed.
    await interaction.reply({ embeds: [embed] });
  } else {
    await interaction.reply("Failed to fetch server status.");
  }
}
