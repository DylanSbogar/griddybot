import {
  SlashCommandBuilder,
  ChatInputCommandInteraction,
  MessageFlags,
} from "discord.js";
import path from "path";
import fs from "fs";
import fetch from "node-fetch";

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const topic = "lookatthis";

module.exports = {
  data: new SlashCommandBuilder()
    .setName("upload" + topic)
    .setDescription("hey man check this out")
    .addAttachmentOption((option) =>
      option.setName("image").setDescription("The image to save").setRequired(true)
    ),
  async execute(interaction: ChatInputCommandInteraction): Promise<void> {
    const imageAttachment = interaction.options.getAttachment("image");
    const subfolder = topic;

    const baseImagesFolder = path.join(__dirname, "../..", "images");

    let filePath: string;
    if (subfolder) {
      const subfolderPath = path.join(baseImagesFolder, subfolder);
      fs.mkdirSync(subfolderPath, { recursive: true });
      filePath = path.join(subfolderPath, imageAttachment!.name!);
    } else {
      fs.mkdirSync(baseImagesFolder, { recursive: true });
      filePath = path.join(baseImagesFolder, imageAttachment!.name!);
    }

    try {
      const response = await fetch(imageAttachment!.url!);
      const buffer = await response.buffer();
      fs.writeFileSync(filePath, buffer);

      await interaction.reply({
        content: `Image saved :)`,
        flags: MessageFlags.Ephemeral,
      });
    } catch (error) {
      console.error("Error saving the image:", error);
      await interaction.reply({
        content: "An error occurred while saving the image.",
        flags: MessageFlags.Ephemeral,
      });
    }
  },
};
