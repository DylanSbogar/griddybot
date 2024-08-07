const fs = require("node:fs");
const path = require("node:path");
const { Client, Collection, Events, GatewayIntentBits } = require("discord.js");
const { token } = require("./config.json");
const cron = require("cron");

// Create a new client instance.
const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

// Function to fetch currency conversion - scheduled to run every day at 10:30am
const scheduledMessage = new cron.CronJob("00 30 10 * * *", () => {
  const guild = client.guilds.cache.get("687820446724521984");
  const channel = guild.channels.cache.get("791510841446236181");

  try {
    fetch(
      `https://v6.exchangerate-api.com/v6/${exchangeApiKey}/pair/AUD/JPY`
    ).then((response) => {
      const data = response.json();
      const rate = data.conversion_rate;
      const lastUpdateUtc = data.time_last_update_utc;
      const lastUpdateAest = moment(lastUpdateUtc)
        .tz("Australia/Sydney")
        .format("MMMM Do YYYY, h:mma");

      channel.send(
        `The current conversion wate of 1 AUD to JPY is: ¥${rate}, as of ${lastUpdateAest} AEST.`
      );
    });
  } catch (error) {
    console.error(error);
    channel.send("Sorry, I could not fetch the conversion rate at this time.");
  }
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
  scheduledMessage.start();
});

// Log into Discord with your client's token.
client.login(token);

// Detect a message being sent in the channel, and handle it appropriately if necessary.
client.on("messageCreate", async (message) => {
  const ozBargain = "https://www.ozbargain.com.au/node/";
  const gnRegex = /\b(?:GM|GN)\b/i;
  const gnRegexMatch = gnRegex.exec(message.content);
  const thanksRegex = /\b(thanks griddy)\b/i;
  const thanksRegexMatch = thanksRegex.exec(message.content);
  const loveRegex = /^i love (.*)/i;

  // Ignore messages sent from griddybot.
  if (message.author.bot) return;

  if (message.content.startsWith(ozBargain)) {
    message.channel.send("Thanks just bought");
  } else if (gnRegexMatch) {
    // Repeat the first match of the regex back, in case a user says "gm gn" for example.
    message.channel.send(gnRegexMatch[0]);
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
