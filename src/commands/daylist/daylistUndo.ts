import { SlashCommandBuilder, ChatInputCommandInteraction } from "discord.js";
import fs from "fs";
import path from "path";

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

// Function to remove the latest daylist entry for a user
function remove(
  interaction: ChatInputCommandInteraction,
  userId: string
): void {
  try {
    // Read existing content from JSON
    readJsonFile((data) => {
      const usersData: any[] = data.users[userId] || [];
      const popped = usersData.pop();
      data.users[userId] = usersData;

      // Write the updated data out to JSON
      writeJsonFile(data, () => {
        const replyContent = `Removed: ${JSON.stringify(popped)}`;
        interaction.followUp({
          content: replyContent,
        });
      });
    });
  } catch (parseErr) {
    console.error(`Error parsing JSON: ${parseErr}`);
  }
}

export const data = new SlashCommandBuilder()
  .setName("undodaylist")
  .setDescription("Undoes your latest daylist");

export async function execute(
  interaction: ChatInputCommandInteraction
): Promise<void> {
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
}
