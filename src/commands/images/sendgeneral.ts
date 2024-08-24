import {
  SlashCommandBuilder,
  ChatInputCommandInteraction,
  AttachmentBuilder,
  MessageFlags,
} from "discord.js";
import path from "path";
import fs from "fs";

// Utility function to create a delay using Promises
function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const topic = "trash";

module.exports = {
  data: new SlashCommandBuilder().setName(topic).setDescription("garbage"),
  async execute(interaction: ChatInputCommandInteraction): Promise<void> {
    const subfolder = topic;
    const imageFolder = path.resolve(__dirname, "../..", "images", subfolder);

    if (fs.existsSync(imageFolder)) {
      await sendRandomImage(interaction, imageFolder);
    } else {
      console.log(
        `Specified subfolder not found. Attempted path: ${imageFolder}`
      );
      await interaction.reply({
        content: `The specified subfolder "${subfolder}" does not exist. Upload something first pls.`,
        flags: MessageFlags.Ephemeral,
      });
    }
  },
};

async function sendRandomImage(
  interaction: ChatInputCommandInteraction,
  imageFolder: string
): Promise<void> {
  try {
    const files = fs.readdirSync(imageFolder);

    if (files.length > 0) {
      const randomImageFile = files[Math.floor(Math.random() * files.length)];
      const imagePath = path.join(imageFolder, randomImageFile);
      const imageAttachment = new AttachmentBuilder(imagePath);

      await interaction.reply({ files: [imageAttachment] });
    } else {
      await interaction.reply({
        content: "No images found in the specified folder.",
        flags: MessageFlags.Ephemeral,
      });
    }
  } catch (error) {
    console.error("Error handling random image:", error);
    await interaction.reply({
      content: "An error occurred while processing the command.",
      flags: MessageFlags.Ephemeral,
    });
  }
}