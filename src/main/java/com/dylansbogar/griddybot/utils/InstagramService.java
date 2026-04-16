package com.dylansbogar.griddybot.utils;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class InstagramService {

    /**
     * Resolves an Instagram reel URL to a direct media URL using yt-dlp --get-url.
     * No download occurs — yt-dlp just prints the CDN URL and exits.
     *
     * @param instagramUrl the original Instagram reel URL
     * @return a direct media URL, or null if yt-dlp failed
     */
    public String getMediaUrl(String instagramUrl) {
        try {
            Process process = new ProcessBuilder("yt-dlp", "--get-url", instagramUrl)
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines()
                        .filter(line -> line.startsWith("http"))
                        .findFirst()
                        .orElse(null);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || output == null) {
                System.err.println("yt-dlp exited with code " + exitCode + " for URL: " + instagramUrl);
                return null;
            }

            return String.format("[Here's your reel!](%s)", output);

        } catch (Exception e) {
            System.err.println("Failed to run yt-dlp: " + e.getMessage());
            return null;
        }
    }
}
