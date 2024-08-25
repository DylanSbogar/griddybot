# griddybot

## How to Setup

Firstly, you will need to install the required modules. This can be done with `npm install` in the root repo directory. You will also need your own bot for local testing purposes, a guide for this can be found <a href="">here</a>.

Next, you will require a `.env` file which will house the following things:

- `TOKEN`: Your Bot token. This can be retrieved from the <a href="https://discord.com/developers/applications">Discord Developer Portal</a>. If you select your Application, and select `Bot`, and underneath the `Username` field you should see a button to `Reset Token`.

- `CLIENT_ID`: This is the Application ID of the Bot. This can be found in the `General Information` section of the Developer Portal

- `GUILD_ID`: This is the Server ID you wish to test the bot on. This can be obtained via right-clicking on the server of your choice, and selecting `Copy Server ID`. Note that you will need Developer Settings to be enabled.

- `EXCHANGERATE_API_KEY`:

You can use the `TEMPLATE.env` file as a base, simply rename it to `.env` and leave it in the `src` folder where it currently resides.

## Running the Application

Once you have completed the setup, you can simply run `npm start` from the root folder. This will also automatically deploy your commands, and report back any compilation errors.

## Adding New Commands

### Slash Commands

All of the Slash Commands are located within the `Commands` folder, with there being sub-folders to help categorise them. To create a new Command, simply create a new `.ts` file either in a pre-existing folder, or a new one of your choice.

From here, you'll want to start with the following snippet:

```
import { SlashCommandBuilder } from "discord.js";

export const data = new SlashCommandBuilder()
  .setName("commandName")
  .setDescription("Description of the Command.");

export async function execute(interaction: ChatInputCommandInteraction) {
  try {
    await interaction.reply("Response!");
  } catch (error) {
    console.error(error);
  }
}

```

If you have created a new command file, you'll need to update the `index.ts` file located in `/src`. Simply import your new file and add it to the exported `commands` object.

You'll want to obviously change the name and description of the commands to suit your needs, however this will allow you to type in the Slash Command, and have it reply with a response message. From here you may want to consult the <a href="https://discord.js.org/#/docs/discord.js/main/general/welcome">discord.js documentation</a> or <a href="https://discordjs.guide/">discord.js guides</a> for some inspiration on what to create or how to create your ideas.

### Text Commands

These are Commands that aren't executed with a / like most normal Commands. Instead they are triggered generally by certain things being typed, which can be found within `messageCreate.ts`.
