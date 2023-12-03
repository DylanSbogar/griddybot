const { SlashCommandBuilder } = require("discord.js");
const Tesseract = require("tesseract.js");
const axios = require("axios");
const fs = require("fs");

// Move performOCR and deleteImage outside of the execute function
async function performOCR(interaction, imagePath) {
  try {
    const { data: { text } } = await Tesseract.recognize(imagePath, "eng");

    console.log(`Text: ${text}`);

    // Delete the downloaded image after processing
    deleteImage(imagePath);

    // Update the interaction with the OCR result
    await interaction.followUp({ content: text });
  } catch (error) {
    console.error(`Error performing OCR: ${error.message}`);

    // Delete the downloaded image in case of an error
    deleteImage(imagePath);

    // Update the interaction with an error message
    await interaction.followUp("Error performing OCR. Please try again.");
  }
}

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

      console.log("hasAttachment", hasAttachment);
      console.log("daylistName", daylistName);

      if (hasAttachment == null && daylistName == null) {
        // Neither option provided
        await interaction.editReply(
          "Please provide either an attachment or custom text."
        );
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
        // No attachment provided, just use custom text
        await interaction.followUp(daylistName);
      }
    } catch (error) {
      console.error("Error in execute:", error);
      await interaction.followUp("An unexpected error occurred. Please check the logs.");
    }
  },
};
