const {
  SlashCommandBuilder,
  ApplicationCommandPermissionType,
} = require("discord.js");
const Tesseract = require("tesseract.js");
const axios = require("axios");
const fs = require("fs");
const path = require("path");

const folderPath = __dirname;
const jsonFile = path.join(folderPath, "daylist.json");

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
function mergeAndUpdate(interaction, userId, split) {
  try {
    // Read existing content from JSON.
    readJsonFile((data) => {
      const existing = data.users.find((user) => user[userId]) || {};
      const existingAll = data.allWords;

      // Track the new words being written for the user.
      const userLog = {};
      const newAllWords = {};

      // Merge split with existing user words, updating counts if words already exist.
      const existingUserWords = existing[userId] || {};
      const userWords = split.reduce(
        (result, word) => {
          const lowercase = word.toLowerCase();
          result[lowercase] = (result[lowercase] || 0) + 1;
          userLog[lowercase] = result[lowercase]; // Track added words and their count.
          return result;
        },
        { ...existingUserWords }
      );

      // Sort userWords by count in descending order
      const sortedUserWords = Object.entries(userWords).sort(
        (a, b) => b[1] - a[1]
      );
      const sortedUserWordsObject = Object.fromEntries(sortedUserWords);

      // Track new words collectively for all users.
      const allUserWords = new Set(
        data.users.reduce((allWords, user) => {
          const words = user[Object.keys(user)[0]];
          return allWords.concat(Object.keys(words));
        }, [])
      );

      const allWords = split.reduce(
        (result, word) => {
          const lowercase = word.toLowerCase();
          if (!existingAll[lowercase]) {
            if (!result[lowercase]) {
              result[lowercase] = 0;
            }
            result[lowercase]++;
            newAllWords[lowercase] = result[lowercase]; // Track added words and their count.
          }
          return result;
        },
        { ...existingAll }
      );

      // Extract existing keys from objectsList
      let existingKeys = existingAll.map((obj) => Object.keys(obj)[0]);

      // Filter out strings that have a corresponding key in existingKeys
      const newAllStrings = split.filter((str) => !existingKeys.includes(str));

      // Update user words in the JSON data.
      data.users = data.users.filter((user) => Object.keys(user)[0] !== userId);
      data.users.push({ [userId]: sortedUserWordsObject });

      // Update all words in the JSON data.
      Object.entries(newAllWords).forEach(([word, count]) => {
        const existingIndex = data.allWords.findIndex(
          (entry) => Object.keys(entry)[0] === word
        );

        if (existingIndex !== -1) {
          // Word already exists, increment the count.
          data.allWords[existingIndex][word] += count;
        } else {
          // Word doesn't exist, add a new entry.
          data.allWords.push({ [word]: count });
        }
      });

      // Write the updated data out to JSON.
      writeJsonFile(data, () => {
        if (Object.keys(userLog).length > 0) {
          // Update the reply with new words for the user and new words for all.
          const replyContent = [
            `New words for you: ${Object.entries(userLog)
              .map(([word, count]) => `${word}`)
              .join(", ")}`,
            `New words for all: ${newAllStrings.join(", ")}`,
          ]
            .filter(Boolean)
            .join("\n");

          interaction.followUp({
            content: replyContent,
          });
        } else {
          // No new words found for the user, update the reply accordingly.
          interaction.followUp({ content: "No new words found." });
        }
      });
    });
  } catch (parseErr) {
    console.error(`Error parsing JSON: ${parseErr}`);
  }
}

// Perform the Optical Character Recognition.
async function performOCR(interaction, imagePath, daylistName) {
  try {
    const {
      data: { text },
    } = await Tesseract.recognize(imagePath, "eng");

    // Split the string by spaces and newlines, in the case of mobile screenshots.
    let splitText = text
      .split(/[\s\n]+/)
      .map((str) => str.trim())
      .filter(Boolean);

    mergeAndUpdate(interaction, interaction.user.id, splitText);

    // Delete the downloaded image after processing.
    deleteImage(imagePath);
  } catch (error) {
    console.error(`Error performing OCR: ${error.message}`);

    // Delete the downloaded image in case of an error.
    deleteImage(imagePath);

    // Update the interaction with an error message.
    await interaction.followUp("Error performing OCR. Please try again.");
  }
}

// Function to delete an image
function deleteImage(imagePath) {
  fs.unlink(imagePath, (err) => {
    if (err) {
      console.error(`Error deleting image: ${err}`);
    } else {
      console.log(`Image deleted successfully.`);
    }
  });
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName("daylist")
    .setDescription("Testing bot reading slash commands")
    .addStringOption((option) =>
      option
        .setName("daylist")
        .setDescription("The name of the daylist.")
        .setRequired(false)
    )
    .addAttachmentOption((option) =>
      option
        .setName("file")
        .setDescription("The file you wish to upload")
        .setRequired(false)
    ),
  async execute(interaction) {
    try {
      // Acknowledge the interaction immediately
      await interaction.deferReply();

      const hasAttachment = interaction.options.get("file") || null;
      const daylistName = interaction.options.getString("daylist") || null;

      if (hasAttachment == null && daylistName == null) {
        // Neither option provided
        await interaction.editReply({
          content: "Please provide either an attachment or custom text.",
          ephemeral: true,
        });
        return;
      }

      if (hasAttachment != null) {
        const imagePath = "downloaded_image.png";
        const imageUrl = interaction.options.getAttachment("file").url;

        // Download the image using axios
        await axios({
          method: "get",
          url: imageUrl,
          responseType: "stream",
        })
          .then((response) => {
            response.data.pipe(fs.createWriteStream(imagePath));

            response.data.on("end", () => {
              // Image downloaded, now perform OCR
              performOCR(interaction, imagePath, daylistName);
            });
          })
          .catch((error) => {
            console.error(`Error downloading image: ${error.message}`);
            // Reply with an error message
            interaction.followUp("Error downloading image. Please try again.");
          });
      } else if (daylistName != null) {
        // Split daylistName by blank spaces.
        const splitDaylistName = daylistName.split(" ");
        // Pass the user ID to the mergeAndUpdate function
        mergeAndUpdate(interaction, interaction.user.id, splitDaylistName);
      }
    } catch (error) {
      console.error("Error in execute:", error);
      await interaction.followUp(
        "An unexpected error occurred. Please check the logs."
      );
    }
  },
};
