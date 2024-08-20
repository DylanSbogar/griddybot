import "dotenv/config";
import {
  REST,
  RESTPostAPIApplicationCommandsJSONBody,
  Routes,
} from "discord.js";
import { readdirSync } from "fs";
import path from "path";

const { CLIENT_ID, GUILD_ID, TOKEN } = process.env;

const commands: RESTPostAPIApplicationCommandsJSONBody[] = [];

export default async function deployGlobalCommands() {
  // Grab all the command folders from the commands directory you created earlier
  const foldersPath = path.join(__dirname, "commands");
  const commandFolders = readdirSync(foldersPath);

  for (const folder of commandFolders) {
    // Grab all the command files from the commands directory you created earlier
    const commandsPath = path.join(foldersPath, folder);
    const commandFiles = readdirSync(commandsPath).filter(
      (file) => file.endsWith(".js") || file.endsWith(".ts")
    );
    // Grab the SlashCommandBuilder#toJSON() output of each command's data for deployment
    for (const file of commandFiles) {
      const filePath = path.join(commandsPath, file);
      const command = require(filePath);
      if ("data" in command && "execute" in command) {
        commands.push(command.data.toJSON());
      } else {
        console.log(
          `[WARNING] The command at ${filePath} is missing a required "data" or "execute" property.`
        );
      }
    }
  }

  const rest = new REST({ version: "10" }).setToken(TOKEN as string);

  try {
    console.log(`Started refreshing ${commands.length} application commands.`);

    // Clear existing commands
    await rest.put(Routes.applicationGuildCommands(CLIENT_ID as string, GUILD_ID as string), {
      body: [],
    });

    // Deploy new commands
    await rest.put(
      Routes.applicationGuildCommands(CLIENT_ID as string, GUILD_ID as string),
      {
        body: commands,
      }
    );

    console.log(
      `Successfully reloaded ${commands.length} application commands.`
    );
  } catch (error) {
    console.error(error);
  }
}

// Call the function to ensure it runs when the script is executed
deployGlobalCommands();
