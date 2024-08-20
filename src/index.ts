import 'dotenv/config'
import { Client, GatewayIntentBits } from "discord.js";
import ready from "./listeners/ready";
import deployGlobalCommands from './deployCommands';

const { TOKEN } = process.env;

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

deployGlobalCommands();

ready(client);

client.login(TOKEN as string);