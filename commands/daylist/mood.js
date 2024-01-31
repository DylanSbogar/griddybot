const { SlashCommandBuilder } = require("discord.js");
const path = require("path");
const fs = require("fs");
const { ChartJSNodeCanvas } = require("chartjs-node-canvas");

// This function will return MessageAttachment object from discord.js
// Pass as much parameter as you need
const generateCanva = async (labels, datas, username) => {
  const gradientColours = generateGradientColours(
    [0, 255, 0],
    [255, 0, 0],
    datas.length
  );

  const renderer = new ChartJSNodeCanvas({
    width: 1280,
    height: 720,
    backgroundColour: "rgb(48,51,56)",
  });
  const image = await renderer.renderToBuffer({
    type: "bar", // Show a bar chart
    data: {
      labels: labels,
      datasets: [
        {
          label: "Daylist word occurrences",
          data: datas,
          backgroundColor: gradientColours,
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

// Function to generate gradient colors based on the length of the data
function generateGradientColours(startColor, endColor, length) {
  const gradientColors = [];

  for (let i = 0; i < length; i++) {
    // Interpolate between startColor and endColor
    const progress = i / (length - 1);
    const interpolatedColor = interpolateColor(startColor, endColor, progress);
    gradientColors.push(`rgba(${interpolatedColor.join(",")})`);
  }

  return gradientColors;
}

// Function to interpolate between two colors
function interpolateColor(startColor, endColor, progress) {
  return startColor.map((channel, index) => {
    const channelDiff = endColor[index] - channel;
    return Math.round(channel + channelDiff * progress);
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
    )
    .addIntegerOption((option) =>
      option
        .setName("length")
        .setDescription("The top x amount of results you wish to retrieve.")
        .setRequired(false)
        .setMinValue(1)
    ),

  async execute(interaction) {
    const user = interaction.options.getUser("user") ?? interaction.user;
    const length = interaction.options.getInteger("length") ?? 5;

    // Read existing content from JSON.
    readJsonFile(async (data) => {
      // Find the userStats for the given user ID or default to an empty object
      const userStats = data.users.find((userData) => userData[user.id]) || {};

      // Extract the userStats for the specific user ID '231700435071664128'
      const specificUserStats = userStats[user.id] || {};

      // Convert the specificUserStats to an array of key-value pairs
      const userStatsArray = Object.entries(specificUserStats);

      // Take a slice of the array to keep only the first x items
      const slicedStats = userStatsArray.slice(0, length);

      // Extract the labels and values from the slicedStats
      const labels = slicedStats.map(([key]) => key);
      const values = slicedStats.map(([_, value]) => value);

      // Generate your graph & get the picture as a response
      const attachment = await generateCanva(labels, values, user.username);

      interaction.reply({ files: [attachment] });
    });
  },
};
