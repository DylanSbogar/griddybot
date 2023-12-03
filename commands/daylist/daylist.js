const { SlashCommandBuilder } = require("discord.js");
const Tesseract = require("tesseract.js");
const axios = require("axios");
const fs = require("fs");

const jsonFile = "daylist.json";

// Function to read content from a JSON file
function readJsonFile(callback) {
  fs.readFile(jsonFile, "utf8", (err, data) => {
    if (err) {
      console.error(`Error reading the JSON file: ${err}`);
      return;
    }
    callback(data);
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

function mergeAndUpdate(interaction, split) {
  console.log(split);

  try {
    // Read existing content in from JSON.
    readJsonFile((data) => {
      const existing = JSON.parse(data);

      // Track the new words being written.
      const log = [];

      // Merge split with existing, excluding duplicates,
      const updated = split.reduce((result, word) => {
        const lowercase = word.toLowerCase();

        if (
          !existing.some(
            (existingWord) => existingWord.toLowerCase() === lowercase
          )
        ) {
          result.push(word);
          log.push(word); // Track added words.
        }
        return result;
      }, existing);

      // Write the updated array out to JSON.
      writeJsonFile(updated, () => {
        if (log.length > 0) {
          // Write the updated array out to JSON.
          writeJsonFile(updated, () => {
            // Update the reply with new words.
            interaction.followUp({ content: `New words found: ${log}` });
          });
        } else {
          // No new words found, update the reply accordingly.
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

    console.log(`Text: ${text}`);
    console.log(`splitText: ${splitText}`);

    mergeAndUpdate(interaction, splitText);

    // Delete the downloaded image after processing
    deleteImage(imagePath);
  } catch (error) {
    console.error(`Error performing OCR: ${error.message}`);

    // Delete the downloaded image in case of an error
    deleteImage(imagePath);

    // Update the interaction with an error message
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
        mergeAndUpdate(interaction, splitDaylistName);
      }
    } catch (error) {
      console.error("Error in execute:", error);
      await interaction.followUp(
        "An unexpected error occurred. Please check the logs."
      );
    }
  },
};
