const {
  SlashCommandBuilder,
  ApplicationCommandPermissionType,
} = require("discord.js");
const Tesseract = require("tesseract.js");
const axios = require("axios");
const fs = require("fs");
const path = require("path");

const folderPath = __dirname;
const jsonFile = path.join(folderPath, "daylist2.json");

// Function to read content from a JSON file
function readJsonFile(callback) {
  fs.readFile(jsonFile, "utf8", (err, data) => {
    if (err) {
      console.error(`Error reading the JSON file: ${err}`);
      return;
    }
    const jsonData = JSON.parse(data);
    callback(jsonData);
  });
}

// Function to write content to a JSON file
function writeJsonFile(content, callback) {
  fs.writeFile(jsonFile, JSON.stringify(content, null, 2), "utf8", (err) => {
    if (err) {
      console.error(`Error writing to the JSON file: ${err}`);
    } else {
      console.log("Data has been written to the JSON file.");
      callback();
    }
  });
}

// Function to merge and update user data
function remove(interaction, userId) {
  try {
    // Read existing content from JSON.
    readJsonFile((data) => {
      const usersData = data.users[userId] || [];
      var popped = usersData.pop();
      data.users[userId] = usersData;

      // Write the updated data out to JSON.
      writeJsonFile(data, () => {
        const replyContent = [
          `Removed: ${JSON.stringify(popped)}`
        ]
          .filter(Boolean)
          .join("\n");
        interaction.followUp({
          content: replyContent,
        });
      });
    });
  } catch (parseErr) {
    console.error(`Error parsing JSON: ${parseErr}`);
  }
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName("undodaylist")
    .setDescription("Undoes your latest daylist"),
  async execute(interaction) {
    try {
      // Acknowledge the interaction immediately
      await interaction.deferReply();
      remove(interaction, interaction.user.id);
      
    } catch (error) {
      console.error("Error in execute:", error);
      await interaction.followUp(
        "An unexpected error occurred. Please check the logs."
      );
    }
  },
};
