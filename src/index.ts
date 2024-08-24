import "dotenv/config";
import { Client, GatewayIntentBits } from "discord.js";
import ready from "./listeners/ready";
import guildCreate from "./listeners/guildCreate";
import interactionCreate from "./listeners/interactionCreate";

const { TOKEN } = process.env;

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.DirectMessages,
    GatewayIntentBits.MessageContent,
  ],
});

ready(client);

guildCreate(client);

interactionCreate(client);

client.login(TOKEN as string);
