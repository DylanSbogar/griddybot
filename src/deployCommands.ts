import "dotenv/config";
import {
  REST,
  RESTPostAPIApplicationCommandsJSONBody,
  Routes,
} from "discord.js";
import { readdirSync } from "fs";
import path from "path";
import { commands } from "./commands";

const { CLIENT_ID, TOKEN } = process.env;

const commandsData = Object.values(commands).map((command) => command.data);

const rest = new REST({ version: '10' }).setToken(TOKEN as string);

type DeployCommandsProps = {
  guildId: string;
};

export async function deployCommands({guildId}: DeployCommandsProps) {
  try {
    console.log(`Started refreshing application (/) commands.`);

    await rest.put(
      Routes.applicationGuildCommands(CLIENT_ID as string, guildId),
      {
        body: commandsData,
      }
    );

    console.log(`Successfully reloaded application (/) commands.`);
  } catch (err) {
    console.error(err);
  }
}
