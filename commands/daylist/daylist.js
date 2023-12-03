const { SlashCommandBuilder } = require("discord.js");
const Tesseract = require("tesseract.js");
const axios = require("axios");
const fs = require("fs");

module.exports = {
  data: new SlashCommandBuilder()
    .setName("daylist")
    .setDescription("testing bot reading slash commands")
    .addAttachmentOption((option) =>
      option
        .setName("file")
        .setDescription("the file you wish to upload")
        .setRequired(true)
    ),
  async execute(interaction) {
    // Acknowledge the interaction immediately
    interaction.deferReply();

    const imagePath = "downloaded_image.png";
    const imageUrl = interaction.options.getAttachment("file").url;

    // Download the image using axios
    axios({
      method: "get",
      url: imageUrl,
      responseType: "stream",
    })
      .then((response) => {
        response.data.pipe(fs.createWriteStream(imagePath));

        response.data.on("end", () => {
          // Image downloaded, now perform OCR
          performOCR(imagePath);
        });
      })
      .catch((error) => {
        console.error(`Error downloading image: ${error.message}`);
        // Reply with an error message
        interaction.editReply("Error downloading image. Please try again.");
      });

    function performOCR(imagePath) {
      Tesseract.recognize(imagePath, "eng", {
        logger: (info) => console.log(info),
      })
        .then(({ data: { text } }) => {
          console.log(`Text: ${text}`);

          // Delete the downloaded image after processing
          deleteImage(imagePath);

          // Update the interaction with the OCR result
          interaction.editReply(text);
        })
        .catch((error) => {
          console.error(`Error performing OCR: ${error.message}`);

          // Delete the downloaded image in case of an error
          deleteImage(imagePath);

          // Update the interaction with an error message
          interaction.editReply("Error performing OCR. Please try again.");
        });
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
  },
};