import "dotenv/config";
import { Client, GatewayIntentBits } from "discord.js";
import ready from "./listeners/ready";
import interactionCreate from "./listeners/interactionCreate";
import { deployCommands } from "./deployCommands";

const { TOKEN, GUILD_ID } = process.env;

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.DirectMessages,
    GatewayIntentBits.MessageContent,
  ],
});

deployCommands({guildId: GUILD_ID as string});

ready(client);

interactionCreate(client);

client.login(TOKEN as string);
