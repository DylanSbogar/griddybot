const { SlashCommandBuilder, AttachmentBuilder } = require("discord.js");
const path = require('path');
const fs = require('fs');
const fetch = require('node-fetch'); // Make sure you have this package installed

// Utility function to create a delay using Promises
function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName('lookatthis')
    .setDescription('squeeoonk')
  ,
  async execute(interaction) {
    var subfolder = 'lookatthis';
    // Define the path to the subfolder
    const imageFolder = path.resolve(__dirname, '../..', 'images', subfolder);

    // Check if the specified subfolder exists
    if (fs.existsSync(imageFolder)) {
      // Handle the 'chooseimage' command by sending a random image from the specified subfolder
      await sendRandomImage(interaction, imageFolder);
    } else {
      // Print the full file path in the error message
      console.log(`Specified subfolder not found. Attempted path: ${imageFolder}`);
      // Send a message indicating that the specified subfolder does not exist
      await interaction.reply(`The specified subfolder "${subfolder}" does not exist. Upload something first pls.`);
    }
  }
};

// Function to handle the logic of sending a random image from the specified directory
async function sendRandomImage(interaction, imageFolder) {
  try {
    // Read the contents of the specified directory
    const files = fs.readdirSync(imageFolder);

    // Check if there are any files in the directory
    if (files.length > 0) {
      // Select a random image file
      const randomImageFile = files[Math.floor(Math.random() * files.length)];

      // Create an AttachmentBuilder instance with the random image file
      const imagePath = path.join(imageFolder, randomImageFile);
      const imageAttachment = new AttachmentBuilder(imagePath);

      // Send the random image file as a message attachment
      await interaction.reply({ files: [imageAttachment] });
    } else {
      // Send a message indicating that the folder is empty
      await interaction.reply('No images found in the specified folder.');
    }
  } catch (error) {
    console.error('Error handling random image:', error);
    await interaction.reply('An error occurred while processing the command.');
  }
}