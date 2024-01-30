const { SlashCommandBuilder } = require("discord.js");
const path = require("path");
const fs = require("fs");
const { ChartJSNodeCanvas } = require("chartjs-node-canvas");

// This function will return MessageAttachment object from discord.js
// Pass as much parameter as you need
const generateCanva = async (labels, datas, username) => {
  const renderer = new ChartJSNodeCanvas({
    width: 1280,
    height: 720,
    backgroundColour: "white",
  });
  const image = await renderer.renderToBuffer({
    type: "bar", // Show a bar chart
    data: {
      labels: labels,
      datasets: [
        {
          label: "Daylist word occurrences",
          data: datas,
          backgroundColor: ["rgb(255,99,132)"],
        },
      ],
    },
    options: {
      plugins: {
        title: {
          display: true,
          text: `${username}'s Daylist History`,
        },
      },
    },
  });
  return image;
};

const folderPath = __dirname;
const filePath = path.join(folderPath, "daylist.json");

// Function to read content from a JSON file
function readJsonFile(callback) {
  fs.readFile(filePath, "utf8", (err, data) => {
    if (err) {
      console.error(`Error reading the JSON file: ${err}`);
      return;
    }
    const jsonData = JSON.parse(data);
    callback(jsonData);
  });
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName("mood")
    .setDescription("Returns a graph of a user's daylist history.")
    .addUserOption((option) =>
      option
        .setName("user")
        .setDescription("The user you whose history you wish to search.")
    ),

  async execute(interaction) {
    const user = interaction.options.getUser("user") ?? interaction.user;

    // Read existing content from JSON.
    readJsonFile(async (data) => {
      const userStats = data.users.find((userData) => userData[user.id]) || {};

      // Extract the labels and values from the json data.
      const labels = Object.keys(userStats[user.id]);
      const values = Object.values(userStats[user.id]);

      // Generate your graph & get the picture as response
      const attachment = await generateCanva(labels, values, user.username);

      interaction.reply({ files: [attachment] });
    });
  },
};
