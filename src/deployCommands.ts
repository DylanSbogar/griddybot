import "dotenv/config";
import { REST, Routes } from "discord.js";
import { commands } from "./commands/index.js";

const { CLIENT_ID, TOKEN } = process.env;

const commandsData = Object.values(commands).map((command) => command.data);

const rest = new REST({ version: "10" }).setToken(TOKEN as string);

type DeployCommandsProps = {
  guildId: string;
};

export async function deployCommands({ guildId }: DeployCommandsProps) {
  try {
    console.log(
      `Started refreshing ${commandsData.length} application commands.`
    );

    await rest.put(
      Routes.applicationCommands(CLIENT_ID as string),
      {
        body: commandsData,
      }
    );

    console.log(
      `Successfully reloaded ${commandsData.length} application commands.`
    );
  } catch (err) {
    console.error(err);
  }
}
