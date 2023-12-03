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
    // const testUrl =
    //   "https://media.discordapp.net/attachments/1180513347892953138/1180678848648593570/image.png?ex=657e4bdd&is=656bd6dd&hm=b6321b7bd7938880aac9e969c915f8c2593bd219391e860f68da438b1c326dcd&=&format=webp&quality=lossless";
    const testPath = "downloaded_image.png";
    const imageUrl = interaction.options.getAttachment("file").url;

    // Download the image using axios
    axios({
      method: "get",
      url: imageUrl,
      responseType: "stream",
    })
      .then((response) => {
        response.data.pipe(fs.createWriteStream(testPath));

        response.data.on("end", () => {
          // Image downloaded, now perform OCR
          performOCR(testPath);
        });
      })
      .catch((error) => {
        console.error(`Error downloading image: ${error.message}`);
      });

    function performOCR(imagePath) {
      Tesseract.recognize(imagePath, "eng", {
        logger: (info) => console.log(info),
      })
        .then(({ data: { text } }) => {
          console.log(`Text: ${text}`);

          // Delete the downloaded image after processing
          deleteImage(imagePath);
        })
        .catch((error) => {
          console.error(`Error performing OCR: ${error.message}`);

          // Delete the downloaded image in case of an error
          deleteImage(imagePath);
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
