package com.dylansbogar.griddybot.utils;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

@Service
public class InstagramService {

    /**
     * Downloads instagram reel using yt-dlp and provides the file object for the downloaded video
     *
     * @param instagramUrl the original Instagram reel URL
     * @return the file object for the downloaded video
     */
    public File downloadMedia(String instagramUrl) {
        try {
            Process process = new ProcessBuilder("yt-dlp", "-f bv+ba", "--no-progress", instagramUrl)
                    .redirectErrorStream(true)
                    .start();

            int exitCode = process.waitFor();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines()
                        .filter(line -> line.startsWith("[Merger] Merging formats into"))
                        .findFirst()
                        .orElse(null);
            }

            if (exitCode != 0 || output == null) {
                System.err.println("yt-dlp exited with code " + exitCode + " for URL: " + instagramUrl);
                return null;
            }

            output = output.replace("[Merger] Merging formats into ", "").replace("\"", "");

            return new File(output);

        } catch (Exception e) {
            System.err.println("Failed to run yt-dlp: " + e.getMessage());
            return null;
        }
    }
}
