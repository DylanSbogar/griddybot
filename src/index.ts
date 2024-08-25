import "dotenv/config";
import { Client, GatewayIntentBits } from "discord.js";
import ready from "./listeners/ready.js";
import interactionCreate from "./listeners/interactionCreate.js";
import { deployCommands } from "./deployCommands.js";
import messageCreate from "./listeners/messageCreate.js";

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

messageCreate(client);

client.login(TOKEN as string);
