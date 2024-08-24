import { ChatInputCommandInteraction, Client } from "discord.js";
import { commands } from "../commands";

export default (client: Client): void => {
  client.on("interactionCreate", async (interaction) => {
    if (!interaction.isCommand()) {
      return;
    }
    const { commandName } = interaction;
    if (commands[commandName as keyof typeof commands]) {
      commands[commandName as keyof typeof commands].execute(interaction as ChatInputCommandInteraction);
    }
  });
};
