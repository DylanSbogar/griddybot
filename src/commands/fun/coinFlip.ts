import { ChatInputCommandInteraction, SlashCommandBuilder } from "discord.js";

// Utility function to create a delay using Promises
function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export const data = new SlashCommandBuilder()
  .setName("coinflip")
  .setDescription("Flip a coin!");

export async function execute(
  interaction: ChatInputCommandInteraction
): Promise<void> {
  try {
    const coinMessage = await interaction.reply("Flipping the coin...");
    const results = ["Heads", "Tails"];

    // Determine a random number of times the coin will flip between 5 and 15.
    const flips: number = Math.floor(Math.random() * (15 - 5 + 1)) + 5;

    // Simulate animation by changing the message quickly
    for (let i = 0; i < flips; i++) {
      await wait(250);
      await coinMessage.edit(
        `The coin is flipping... **${
          results[Math.floor(Math.random() * results.length)]
        }**`
      );
    }

    await wait(500);
    const result: string = results[Math.floor(Math.random() * results.length)];
    await coinMessage.edit(`🪙 The coin landed on: **${result}** 🪙`);
  } catch (error) {
    console.error("Error in execute:", error);
    await interaction.followUp(
      "An unexpected error occurred. Please check the logs."
    );
  }
}
