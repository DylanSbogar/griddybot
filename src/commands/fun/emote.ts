import {
  AttachmentBuilder,
  ChatInputCommandInteraction,
  SlashCommandBuilder,
} from "discord.js";
import { GraphQLClient, gql } from "graphql-request";
import fetch from "node-fetch";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

// Define the GraphQL query using the gql template literal
const query = gql`
  query SearchEmotes(
    $query: String!
    $page: Int
    $sort: Sort
    $limit: Int
    $filter: EmoteSearchFilter
  ) {
    emotes(
      query: $query
      page: $page
      sort: $sort
      limit: $limit
      filter: $filter
    ) {
      count
      items {
        id
        name
        state
        trending
        owner {
          id
          username
          display_name
          style {
            color
            paint_id
            __typename
          }
          __typename
        }
        flags
        host {
          url
          files {
            name
            format
            width
            height
            __typename
          }
          __typename
        }
        __typename
      }
      __typename
    }
  }
`;

// Shoutout GPT for these interfaces
interface Emote {
  id: string;
  name: string;
  state: string;
  trending: boolean;
  owner: {
    id: string;
    username: string;
    display_name: string;
    style: {
      color: string;
      paint_id: string;
      __typename: string;
    };
    __typename: string;
  };
  flags: string[];
  host: {
    url: string;
    files: {
      name: string;
      format: string;
      width: number;
      height: number;
      __typename: string;
    }[];
    __typename: string;
  };
  __typename: string;
}

interface EmoteSearchResult {
  count: number;
  items: Emote[];
  __typename: string;
}

interface GraphQLResponse {
  emotes: EmoteSearchResult;
}

const endpoint = `https://7tv.io/v3/gql`;

const graphQLClient = new GraphQLClient(endpoint, {
  method: `GET`,
  jsonSerializer: {
    parse: JSON.parse,
    stringify: JSON.stringify,
  },
});

export const data = new SlashCommandBuilder()
  .setName("emote")
  .setDescription("Retrieve your favourite 7tv emotes")
  .addStringOption((option) =>
    option
      .setName("emotename")
      .setDescription("The name of the emote.")
      .setRequired(true)
  );

async function downloadImage(
  url: string,
  filePath: string,
  interaction: ChatInputCommandInteraction,
  extension: string
) {
  try {
    const response = await fetch(`${url}${extension}`);
    const emotePath = `${filePath}${extension}`;

    // 404 with .gif indicates the emote is not animated. Try again with .png
    if (response.status === 404 && extension === ".gif") {
      await downloadImage(url, filePath, interaction, `.png`);
    }

    if (response.ok) {
      const buffer = await response.buffer();
      fs.writeFileSync(emotePath, buffer);

      if (!interaction.replied && !interaction.deferred) {
        await interaction.reply(`${url}${extension}`);
      }
    }
  } catch (error) {
    console.error("Error downloading image:", error);
    if (!interaction.replied && !interaction.deferred) {
      await interaction.reply(`An error occurred while downloading the image.`);
    }
  }
}

export async function execute(
  interaction: ChatInputCommandInteraction
): Promise<void> {
  let emote: string | null = interaction.options.getString("emotename");

  if (!emote) return;

  emote = emote?.toLowerCase();
  const emotesFolder = path.join(
    fileURLToPath(import.meta.url),
    "../../..",
    "images",
    "emotes"
  );

  // Create images/emotes if it doesn't already exist.
  if (!fs.existsSync(emotesFolder)) {
    fs.mkdirSync(emotesFolder, { recursive: true });
  }

  // Set up variables for the gql query.
  const variables = {
    query: emote,
    page: 1,
    sort: null,
    limit: 1,
    filter: null,
  };

  let replySent = false;

  try {
    let foundFile: boolean = false;
    const possibleExtensions = [".gif", ".png"];

    // Try both file extensions for emotes.
    for (const ext of possibleExtensions) {
      const emotePath = path.join(emotesFolder, `${emote}${ext}`);

      // If a match is found, return the image from local files.
      if (fs.existsSync(emotePath)) {
        const attachment = new AttachmentBuilder(emotePath);
        if (!replySent) {
          await interaction.reply({ files: [attachment] });
          replySent = true;
        }
        foundFile = true;
        break;
      }
    }

    // If no match was found, download it from 7tv.
    if (!foundFile) {
      const responseData = await graphQLClient.request<GraphQLResponse>(
        query,
        variables
      );

      const items = responseData.emotes.items;

      if (items.length > 0) {
        const match = items[0];
        const url = match.host.url;
        const files = match.host.files;

        if (files && files.length > 0) {
          // Grab the last file in the list, as it'll be the largest resolution.
          const filename = files[files.length - 1].name
            .toString()
            .split(".")[0];

          // Start with .gif as we first assume its animated. If not it'll fallback to .png.
          const link = `https:${url}/${filename}`;
          const filePath = path.join(emotesFolder, emote);

          await downloadImage(link, filePath, interaction, `.gif`);
          replySent = true;
        } else {
          if (!replySent) {
            await interaction.reply(`No files found for the ${emote} emote.`);
            replySent = true;
          }
        }
      } else {
        if (!replySent) {
          await interaction.reply(`No emotes found for ${emote}.`);
          replySent = true;
        }
      }
    }
  } catch (err) {
    console.error(err);
    if (!replySent) {
      await interaction.reply(
        `There was an error retrieving the ${emote} emote. Please try again later. (May be rate limited)`
      );
      replySent = true;
    }
  }
}
