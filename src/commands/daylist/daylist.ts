import {
  SlashCommandBuilder,
  ChatInputCommandInteraction,
  Attachment,
  CommandInteraction,
} from "discord.js";
import path from "path";
import fs from "fs";
import axios from "axios";
import Tesseract from "tesseract.js";

const folderPath = __dirname;
const jsonFile = path.join(folderPath, "daylist2.json");

// Function to read content from a JSON file
function readJsonFile(callback: (data: any) => void): void {
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
function writeJsonFile(content: any, callback: () => void): void {
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
function mergeAndUpdate(
  interaction: ChatInputCommandInteraction,
  userId: string,
  split: string[]
): void {
  try {
    split = split.map((v) => v.toLowerCase());
    // Read existing content from JSON.
    readJsonFile((data) => {
      const usersData: any[] = data.users[userId] || [];

      const globalUniqueWords = new Set<string>();
      const yourUniqueWords = new Set<string>();

      // Populate the sets of unique words
      for (const user in data.users) {
        for (const daylistObj of data.users[user]) {
          for (const word of daylistObj.description) {
            globalUniqueWords.add(word);
            if (user === userId) {
              yourUniqueWords.add(word);
            }
          }
        }
      }

      // Write the new obj
      const newObj = generateDaylistObj(split);
      console.log("Daylist obj generated: " + JSON.stringify(newObj));

      usersData.push(newObj);
      data.users[userId] = usersData;

      // Write the updated data out to JSON.
      writeJsonFile(data, () => {
        const replyContent = [
          split.join(" "),
          `New words for you: ${hasNewWords(yourUniqueWords, newObj).join(
            ", "
          )}`,
          `New words for all: ${hasNewWords(globalUniqueWords, newObj).join(
            ", "
          )}`,
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

// Function to check for new words
function hasNewWords(uniqueWords: Set<string>, newObj: any): string[] {
  const newWords: string[] = [];
  for (const myWord of newObj.description) {
    if (!uniqueWords.has(myWord)) {
      newWords.push(myWord);
    }
  }
  return newWords;
}

// Function to generate the daylist object
function generateDaylistObj(split: string[]): any {
  let len = split.length;
  let time = split[len - 1];
  const days = new Set([
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday",
    "sunday",
  ]);
  // Determine if it's a two-word time (e.g. Early Morning)
  let twoWordTime = false;

  // Handle the edge case 'For this moment'
  if (time === "moment") {
    return {
      day: "",
      time: split.slice(len - 3).join(" "),
      timestamp: new Date().toISOString(),
      description: split.slice(0, len - 3),
    };
  }

  const beforeTime = split[len - 2];
  if (beforeTime === "late" || beforeTime === "early") {
    twoWordTime = true;
    time = `${beforeTime} ${time}`;
  }

  let day = twoWordTime ? split[len - 3] : split[len - 2];

  // Some daylists don't have a day
  if (!days.has(day)) {
    day = "";
    len += 1;
  }

  return {
    day: day,
    time: time,
    timestamp: new Date().toISOString(),
    description: split.slice(0, twoWordTime ? len - 3 : len - 2),
  };
}

// Perform the Optical Character Recognition.
async function performOCR(
  interaction: ChatInputCommandInteraction,
  imagePath: string,
  daylistName: string | null
): Promise<void> {
  try {
    const {
      data: { text },
    } = await Tesseract.recognize(imagePath, "eng");

    // Split the string by spaces and newlines, in the case of mobile screenshots.
    const splitText = text
      .split(/[\s\n]+/)
      .map((str) => str.trim())
      .filter(Boolean);

    mergeAndUpdate(interaction, interaction.user.id, splitText);

    // Delete the downloaded image after processing.
    deleteImage(imagePath);
  } catch (error) {
    console.error(`Error performing OCR: ${error}`);

    // Delete the downloaded image in case of an error.
    deleteImage(imagePath);

    // Update the interaction with an error message.
    await interaction.followUp("Error performing OCR. Please try again.");
  }
}

// Function to delete an image
function deleteImage(imagePath: string): void {
  fs.unlink(imagePath, (err) => {
    if (err) {
      console.error(`Error deleting image: ${err}`);
    } else {
      console.log(`Image deleted successfully.`);
    }
  });
}

export const data = new SlashCommandBuilder()
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
  );

export async function execute(
  interaction: ChatInputCommandInteraction
): Promise<void> {
  try {
    // Acknowledge the interaction immediately
    await interaction.deferReply();

    const hasAttachment: Attachment | null =
      interaction.options.getAttachment("file") || null;
    const daylistName: string | null =
      interaction.options.getString("daylist") || null;

    if (!hasAttachment && !daylistName) {
      // Neither option provided
      await interaction.editReply({
        content: "Please provide either an attachment or custom text.",
      });
      return;
    }

    if (hasAttachment) {
      const imagePath = "downloaded_image.png";
      const imageUrl: string = hasAttachment.url;

      // Download the image using axios
      try {
        const response = await axios({
          method: "get",
          url: imageUrl,
          responseType: "stream",
        });

        const writer = fs.createWriteStream(imagePath);
        response.data.pipe(writer);

        writer.on("finish", () => {
          // Image downloaded, now perform OCR
          performOCR(interaction, imagePath, daylistName);
        });

        writer.on("error", (error) => {
          console.error(`Error writing the image file: ${error.message}`);
          interaction.followUp("Error processing the image. Please try again.");
        });
      } catch (error) {
        console.error(`Error downloading image: ${error}`);
        // Reply with an error message
        await interaction.followUp(
          "Error downloading image. Please try again."
        );
      }
    } else if (daylistName) {
      // Split daylistName by blank spaces.
      const splitDaylistName: string[] = daylistName.split(" ");
      // Pass the user ID to the mergeAndUpdate function
      mergeAndUpdate(interaction, interaction.user.id, splitDaylistName);
    }
  } catch (error) {
    console.error("Error in execute:", error);
    await interaction.followUp(
      "An unexpected error occurred. Please check the logs."
    );
  }
}

// module.exports = {
//   data: new SlashCommandBuilder()
//     .setName("daylist")
//     .setDescription("Testing bot reading slash commands")
//     .addStringOption((option) =>
//       option
//         .setName("daylist")
//         .setDescription("The name of the daylist.")
//         .setRequired(false)
//     )
//     .addAttachmentOption((option) =>
//       option
//         .setName("file")
//         .setDescription("The file you wish to upload")
//         .setRequired(false)
//     ),
//   async execute(interaction: ChatInputCommandInteraction): Promise<void> {

//   },
// };
