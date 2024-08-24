import { Client } from "discord.js";
import { deployCommands } from "../deployCommands";

export default (client: Client): void => {
  client.on("guildCreate", async (guild) => {
    await deployCommands({ guildId: guild.id });
  });
};
