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
function mergeAndUpdate(interaction, userId, split) {
  try {
    split = split.map(v => v.toLowerCase());
    // Read existing content from JSON.
    readJsonFile((data) => {
      const usersData = data.users[userId] || [];

      var globalUniqueWords = new Set();
      var yourUniqueWords = new Set();

      // Populate the sets of unique words
      for (user in data.users) {
        for (daylistObj of data.users[user]) {
          for (word of daylistObj.description) {
            globalUniqueWords.add(word);
            if (user == userId) {
              yourUniqueWords.add(word);
            }
          }
        }
      }

      // Write the new obj
      const newObj = generateDaylistObj(split);
      console.log("Daylist obj generated: " + JSON.stringify(newObj))

      usersData.push(newObj);
      data.users[userId] = usersData;

      // Write the updated data out to JSON.
      writeJsonFile(data, () => {
        const replyContent = [
          split.join(" "),
          `New words for you: ${hasNewWords(yourUniqueWords, newObj).join(', ')}`,
          `New words for all: ${hasNewWords(globalUniqueWords, newObj).join(', ')}`,
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

function hasNewWords(uniqueWords, newObj) {
  var newWords = [];
  for (myWord of newObj.description) {
    if (!uniqueWords.has(myWord)) {
      newWords.push(myWord);
    }
  }
  return newWords;
}

function generateDaylistObj(split) {
  var len = split.length
  var time = split[len - 1];
  const days = new Set(["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]);
  // Determine if its a two word time (e.g. Early Morning)
  var twoWordTime = false;

  // Stupid edge case 'For this moment'
  if (time == "moment") {
    const daylistObj = {
      day: "",
      time: split[len - 3] + " " + split[len - 2] + " " + split[len - 1],
      timestamp: new Date().toISOString(),
      description: split.slice(0, len - 3)
    }
    return daylistObj;
  }

  var beforeTime = split[len - 2];
  if (beforeTime == "late" || beforeTime == "early") {
    twoWordTime = true;
    time = split[len - 2] + " " + split[len - 1];
  }

  if (twoWordTime) {
    var day = split[len - 3];
  } else {
    var day = split[len - 2]
  }

  // Some daylists dont have a day
  if (!days.has(day)) {
    day = ""
    len += 1;
  }

  const daylistObj = {
    day: day,
    time: time,
    timestamp: new Date().toISOString(),
    description: split.slice(0, twoWordTime ? (len - 3) : (len - 2))
  }

  return daylistObj;
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
