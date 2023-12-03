# griddybot

## How to Setup

Firstly, you will need to install the required modules. This can be done with `npm install` in the root repo directory. You will also need your own bot for local testing purposes, a guide for this can be found <a href="">here</a>.

Next, you will require a `config.json` file which will house the following things:

- `token`: Your Bot token. This can be retrieved from the <a href="https://discord.com/developers/applications">Discord Developer Portal</a>. If you select your Application, and select `Bot`, and underneath the `Username` field you should see a button to `Reset Token`.

- `clientId`: This is the Application ID of the Bot. This can be found in the `General Information` section of the Developer Portal

- `guildId`: This is the Server ID you wish to test the bot on. This can be obtained via right-clicking on the server of your choice, and selecting `Copy Server ID`. Note that you will need Developer Settings to be enabled.

Once you have all these, the `config.json` file should be created/placed in the root directory of the repo.

## Running the Application

Once you have completed the setup, you'll need to deploy the commands. This can be done by executing `node ./deployCommands.js` from the root directory. Once this has completed, you can start the Application with `node .`.

## Adding New Commands

### Slash Commands

All of the Slash Commands are located within the `Commands` folder, with there being sub-folders to help categorise them. To create a new Command, simply create a new `.js` file either in a pre-existing folder, or a new one of your choice.

From here, you'll want to start with the following snippet:

```
const { SlashCommandBuilder } = require("discord.js");

module.exports = {
    data: new SlashCommandBuilder()
        .setName("commandName")
        .setDescription("Description of the command"),

        async execute(interaction) {
            await interaction.reply("Response!");
    }
}
```

You'll want to obviously change the name and description of the commands to suit your needs, however this will allow you to type in the Slash Command, and have it reply with a response message. From here you may want to consult the <a href="https://discord.js.org/#/docs/discord.js/main/general/welcome">discord.js documentation</a> or <a href="https://discordjs.guide/">discord.js guides</a> for some inspiration on what to create or how to create your ideas.

### Text Commands

These are Commands that aren't executed with a / like most normal Commands. Instead they are triggered generally by certain things being typed, which can be found within the `messageCreate` event in `index.js`.
