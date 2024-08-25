import { Client } from "discord.js";

export default (client: Client): void => {
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
};
