import { Client, Events, TextChannel } from "discord.js";
import { CronJob } from "cron";
import moment from "moment-timezone";
import fs from "fs";
import 'dotenv/config';

const { AUTO_EXCHANGERATE_GUILD_ID, EXCHANGERATE_API_KEY } = process.env;

export default (client: Client): void => {
  client.on(Events.ClientReady, async () => {
    if (!client.user || !client.application) {
      return;
    }

    console.log(`Ready! Logged in as ${client.user.tag}`);

    const exchangeRateJob = new CronJob(
      "30 10 * * *",
      async function () {
        const channel = client.channels.cache.get(
          AUTO_EXCHANGERATE_GUILD_ID as string
        ) as TextChannel;

        if (!channel) return;

        try {
          const response = await fetch(
            `https://v6.exchangerate-api.com/v6/${EXCHANGERATE_API_KEY}/pair/AUD/JPY`
          );
          const data = await response.json();

          // Parse incoming 'rate' in case it's treated as a string
          const rate = parseFloat(data.conversion_rate);
          const truncatedRate = rate.toFixed(2);
          const lastUpdateUtc = data.time_last_update_utc;
          const lastUpdateAest = moment(lastUpdateUtc)
            .tz("Australia/Sydney")
            .format("MMMM Do YYYY, h:mma");

          // Read the previous rate from the file
          let previousRateMessage =
            "No previous conversion rate found on file.";
          if (fs.existsSync("previous_rate.txt")) {
            const previousRate = parseFloat(
              fs.readFileSync("previous_rate.txt", "utf8")
            );
            const truncatedPrevRate = previousRate.toFixed(2);
            previousRateMessage = `Previous conversion rate: ¥${truncatedPrevRate} ${
              previousRate < rate ? ":chart::chart:" : ":sob::sob:"
            }`;
          }

          // Write the current rate to the file, which will overwrite the previous value
          fs.writeFileSync("previous_rate.txt", rate.toString());

          channel.send(
            `The current conversion rate of 1 AUD to JPY is: ¥${truncatedRate}, as of ${lastUpdateAest} AEST.\n${previousRateMessage}`
          );
        } catch (error) {
          console.error(error);
          channel.send(
            "Sorry, I could not fetch the conversion rate at this time."
          );
        }
      },
      null,
      true,
      "Australia/Sydney"
    );
  });
};
