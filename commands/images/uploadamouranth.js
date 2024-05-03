const { SlashCommandBuilder, MessageFlags } = require("discord.js");
const path = require('path');
const fs = require('fs');
const fetch = require('node-fetch'); // Make sure you have this package installed

// Utility function to create a delay using Promises
function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const topic = 'amouranth';

module.exports = {
  data: new SlashCommandBuilder()
    .setName('upload'.concat(topic))
    .setDescription('GRRRRRRRRRRR WOOF WOOF BARK BARK ARF BARK GRRRR')
    .addAttachmentOption(option =>
      option
        .setName('image')
        .setDescription('yummy')
        .setRequired(true)),
  async execute(interaction) {
    const { commandName } = interaction;
    // Get the image attachment option
    const imageAttachment = interaction.options.getAttachment('image');

    // Get the subfolder option
    const subfolder = topic;

    // Define the base images folder path
    const baseImagesFolder = path.join(__dirname, '../..', 'images');

    // Define the file path based on the subfolder option
    let filePath;
    if (subfolder) {
      // Define the subfolder path
      const subfolderPath = path.join(baseImagesFolder, subfolder);

      // Create the subfolder if it doesn't exist 
      fs.mkdirSync(subfolderPath, { recursive: true });

      // Define the file path in the subfolder
      filePath = path.join(subfolderPath, imageAttachment.name);
    } else {
      // Create the images folder if it doesn't exist
      fs.mkdirSync(baseImagesFolder, { recursive: true });

      // Define the file path in the images folder
      filePath = path.join(baseImagesFolder, imageAttachment.name);
    }

    // Download and save the image locally
    try {
      const response = await fetch(imageAttachment.url);
      const buffer = await response.buffer();
      fs.writeFileSync(filePath, buffer);

      // Send a confirmation message to the user
      await interaction.reply({content: `Image saved :)`, flags: MessageFlags.Ephemeral});
    } catch (error) {
      console.error('Error saving the image:', error);
      await interaction.reply({content: 'An error occurred while saving the image.', flags: MessageFlags.Ephemeral});
    }
  }
};
