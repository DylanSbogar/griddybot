const { execute } = require("../commands/convert-aud-to-jpy");
const fetch = require("node-fetch");

jest.mock("node-fetch");

describe("convert-aud-to-jpy command", () => {
  let interaction;

  beforeEach(() => {
    interaction = {
      isCommand: jest.fn(() => true),
      commandName: "convert-aud-to-jpy",
      reply: jest.fn(),
    };
  });

  it("replies with the conversion rate when API call is successful", async () => {
    const mockApiResponse = {
      conversion_rates: {
        JPY: 80.5,
      },
    };

    fetch.mockResolvedValue({
      json: jest.fn().mockResolvedValue(mockApiResponse),
    });

    await execute(interaction);

    expect(interaction.reply).toHaveBeenCalledWith(
      "The current conversion rate of 1 AUD to JPY is: ¥80.5"
    );
  });

  it("replies with an error message when API call fails", async () => {
    fetch.mockRejectedValue(new Error("API call failed"));

    await execute(interaction);

    expect(interaction.reply).toHaveBeenCalledWith(
      "Sorry, I could not fetch the conversion rate at this time."
    );
  });
});
