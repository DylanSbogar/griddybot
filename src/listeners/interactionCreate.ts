import { Client } from "discord.js";
import { commands } from "src/commands/index.js";

export default (client: Client): void => {
  client.on("interactionCreate", async (interaction) => {
    if (!interaction.isChatInputCommand()) {
      return;
    }
    const { commandName } = interaction;
    if (commands[commandName as keyof typeof commands]) {
      commands[commandName as keyof typeof commands].execute(interaction);
    }
  });
};
