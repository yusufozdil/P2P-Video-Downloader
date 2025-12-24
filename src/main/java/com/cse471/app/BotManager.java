package com.cse471.app;

import com.cse471.file.FileInfo;
import java.util.List;
import java.util.Random;

public class BotManager extends Thread {
    private final AppController controller;
    private final String[] keywords = { "video", "movie", "test", "demo", "sample" };
    private boolean running = true;
    private final Random random = new Random();

    public BotManager(AppController controller) {
        this.controller = controller;
    }

    @Override
    public void run() {
        System.out.println("🤖 Bot Mode Activated! I will search and download files autonomously.");

        while (running) {
            try {
                // 1. Sleep Phase
                int sleepSeconds = 10 + random.nextInt(20); // 10-30 seconds
                System.out.println("[BOT] 💤 Sleeping for " + sleepSeconds + "s...");
                Thread.sleep(sleepSeconds * 1000L);

                // 2. Search Phase
                String query = keywords[random.nextInt(keywords.length)];
                System.out.println("[BOT] 🔍 Searching for '" + query + "'...");
                List<FileInfo> results = controller.searchFilesBlocking(query);

                if (results.isEmpty()) {
                    System.out.println("[BOT] ❌ No results found.");
                    continue;
                }

                // 3. Evaluate Phase
                System.out.println("[BOT] 💡 Found " + results.size() + " files.");
                // Pick a random file to download
                FileInfo target = results.get(random.nextInt(results.size()));

                // Check if we already have it (Simple check via file existence is tricky if
                // path varies,
                // but Controller.startDownload handles duplicates by overwriting or ignoring
                // generally.
                // For simulation, re-downloading is actually good traffic!)

                // 4. Action Phase
                System.out.println("[BOT] 🚀 Decided to download: " + target.getFileName());
                controller.startDownload(target, false); // false = Do not play video

            } catch (InterruptedException e) {
                running = false;
                System.out.println("[BOT] 🛑 Bot stopped.");
            } catch (Exception e) {
                System.err.println("[BOT] ⚠️ Error in loop: " + e.getMessage());
            }
        }
    }
}
