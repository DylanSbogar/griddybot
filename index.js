const fs = require("node:fs");
const path = require("node:path");
const { Client, Collection, Events, GatewayIntentBits } = require("discord.js");
const {
  token,
  exchangeApiKey,
  autoExchangeChannelId,
} = require("./config.json");
const cron = require("node-cron");
const moment = require("moment-timezone");

// Create a new client instance.
const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

client.commands = new Collection();

const foldersPath = path.join(__dirname, "commands");
const commandFolders = fs.readdirSync(foldersPath);

// Dynamically retrieve all command files from the commands folder, and any sub-folders therein.
for (const folder of commandFolders) {
  const commandsPath = path.join(foldersPath, folder);
  const commandFiles = fs
    .readdirSync(commandsPath)
    .filter((file) => file.endsWith(".js"));
  for (const file of commandFiles) {
    const filePath = path.join(commandsPath, file);
    const command = require(filePath);

    // Set a new item in the Collection with the key as the command name, and the value as the exported module.
    if ("data" in command && "execute" in command) {
      client.commands.set(command.data.name, command);
    } else {
      console.log(
        `[WARNING] The command at ${filePath} is missing a required "data" or "execute" property.`
      );
    }
  }
}

// When the client is ready, run this code (only once).
client.once(Events.ClientReady, (readyClient) => {
  console.log(`Ready! Logged in as ${readyClient.user.tag}`);

  // Currency Conversion daily message
  const schedule = cron.schedule(
    "30 10 * * *",
    () => {
      const channel = client.channels.cache.get(autoExchangeChannelId);

      try {
        fetch(
          `https://v6.exchangerate-api.com/v6/${exchangeApiKey}/pair/AUD/JPY`
        ).then((response) => {
          const data = response.json().then((value) => {
            const rate = value.conversion_rate;
            const lastUpdateUtc = data.time_last_update_utc;
            const lastUpdateAest = moment(lastUpdateUtc)
              .tz("Australia/Sydney")
              .format("MMMM Do YYYY, h:mma");

            // Read the previous rate from the file
            let previousRateMessage =
              "No previous conversion rate found on file.";
            if (fs.existsSync("previous_rate.txt")) {
              const previousRate = fs.readFileSync("previous_rate.txt", "utf8");
              previousRateMessage = `Previous conversion rate: ¥${previousRate} ${
                (previousRate < rate) ? ':chart::chart:' : ':sob::sob:'}`;
            }

            // Write the current rate to the file, which will overwrite the previous value
            fs.writeFileSync("previous_rate.txt", rate.toString());

            channel.send(
              `The current conversion rate of 1 AUD to JPY is: ¥${rate}, as of ${lastUpdateAest} AEST.\n${previousRateMessage}`
            );
          });
        });
      } catch (error) {
        console.error(error);
        channel.send(
          "Sorry, I could not fetch the conversion rate at this time."
        );
      }
    },
    {
      scheduled: false,
    }
  );

  schedule.start();
});

// Log into Discord with your client's token.
client.login(token);

// Detect a message being sent in the channel, and handle it appropriately if necessary.
client.on("messageCreate", async (message) => {
  const ozBargain = "https://www.ozbargain.com.au/node/";
  const thanksRegex = /\b(thanks griddy)\b/i;
  const thanksRegexMatch = thanksRegex.exec(message.content);
  const loveRegex = /^i love (.*)/i;

  // Ignore messages sent from griddybot.
  if (message.author.bot) return;

  const msgAsLowercase = message.content.toLowerCase();

  if (message.content.startsWith(ozBargain)) {
    message.channel.send("Thanks just bought");
  } else if (msgAsLowercase === "gm" || msgAsLowercase === "gn") {
    message.channel.send(msgAsLowercase);
  } else if (thanksRegexMatch) {
    message.channel.send("No worries <3");
  } else {
    const loveRegexMatch = loveRegex.exec(message.content);
    if (loveRegexMatch) {
      const lovedThing = loveRegexMatch[1];
      if (lovedThing.length > 1950) {
        message.channel.send("yikes, I dont love all that");
      } else {
        message.channel.send(
          `I love ${lovedThing} charlie\nI love ${lovedThing}!!!`
        );
      }
    }
  }
});

// Detect commands and handle them appropriately.
client.on(Events.InteractionCreate, async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  const command = interaction.client.commands.get(interaction.commandName);
  if (!command) {
    console.error(`No command matching ${interaction.commandName} was found.`);
    return;
  }

  try {
    await command.execute(interaction);
  } catch (error) {
    console.error(error);
    if (interaction.replied || interaction.deferred) {
      await interaction.followUp({
        content: "There was an error while executing this command!",
        ephemeral: true,
      });
    } else {
      await interaction.reply({
        content: "There was an error while executing this command!",
        ephemeral: true,
      });
    }
  }
});
