package com.cse471.app;

import com.cse471.file.FileManager;
import com.cse471.file.FileInfo;
import com.cse471.gui.MainFrame;
import com.cse471.network.DiscoveryManager;
import com.cse471.network.TransferManager;

import javax.swing.*;
import java.io.File;
import java.util.UUID;

public class AppController {
    private static AppController instance;
    private final String peerId;
    private final int tcpPort;

    private FileManager fileManager;
    private DiscoveryManager discoveryManager;
    private TransferManager transferManager;
    private MainFrame mainFrame;

    private AppController() {
        this.peerId = "Peer-" + UUID.randomUUID().toString().substring(0, 8);
        this.tcpPort = 0; // 0 = ephemeral, but we might want fixed for testing. Let's use random for now?
        // Logic: ServerSocket(0) picks random. We need to save it.
    }

    public static synchronized AppController getInstance() {
        if (instance == null) {
            instance = new AppController();
        }
        return instance;
    }

    public void initialize(MainFrame frame) {
        this.mainFrame = frame;
        this.fileManager = new FileManager();

        // In headless mode, default to /data directory (mounted visually in Docker)
        if (frame == null) {
            File defaultRoot = new File("/data");
            if (defaultRoot.exists()) {
                setRootFolder(defaultRoot);
                System.out.println("Headless Mode: Root folder set to /data");
            }
        }
    }

    public void startNetwork() {
        try {
            if (transferManager == null) {
                // Generate a random port between 6000-7000
                int assignedPort = 6000 + (int) (Math.random() * 1000);
                transferManager = new TransferManager(fileManager, assignedPort);
                transferManager.startServer();

                discoveryManager = new DiscoveryManager(peerId, assignedPort);
                discoveryManager.start();

                System.out.println("Network Started. ID: " + peerId + " Port: " + assignedPort);
                if (mainFrame != null) {
                    JOptionPane.showMessageDialog(mainFrame, "Connected to P2P Network\nID: " + peerId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (mainFrame != null) {
                JOptionPane.showMessageDialog(mainFrame, "Error starting network: " + e.getMessage());
            }
        }
    }

    public void stopNetwork() {
        if (discoveryManager != null)
            discoveryManager.stop();
        if (transferManager != null)
            transferManager.stop();
        transferManager = null;
        discoveryManager = null;
        if (mainFrame != null) {
            JOptionPane.showMessageDialog(mainFrame, "Disconnected from Network.");
        }
    }

    public void setRootFolder(File folder) {
        if (fileManager != null) {
            fileManager.setRootFolder(folder);
            // Update GUI list?
            if (mainFrame != null) {
                // mainFrame.updateLocalFiles(fileManager.getLocalFileList());
            }
        }
    }

    public void setBufferFolder(File folder) {
        if (fileManager != null) {
            fileManager.setBufferFolder(folder);
        }
    }

    public void searchFiles(String query) {
        if (transferManager == null)
            return;

        new Thread(() -> {
            java.util.LinkedHashMap<String, FileInfo> uniqueFiles = new java.util.LinkedHashMap<>();

            // 1. Local Files
            fileManager.getLocalFileList().stream()
                    .filter(f -> f.getFileName().toLowerCase().contains(query.toLowerCase()))
                    .forEach(f -> uniqueFiles.put(f.getHash(), f)); // Key by Hash

            // 2. Remote Files
            for (com.cse471.network.PeerInfo peer : com.cse471.network.PeerManager.getInstance().getAllPeers()) {
                java.util.List<FileInfo> remoteFiles = transferManager.requestFileList(peer);
                if (remoteFiles != null) {
                    remoteFiles.stream()
                            .filter(f -> f.getFileName().toLowerCase().contains(query.toLowerCase()))
                            .forEach(f -> uniqueFiles.putIfAbsent(f.getHash(), f)); // Only add if not exists
                }
            }

            // Convert back to List
            java.util.List<FileInfo> allFiles = new java.util.ArrayList<>(uniqueFiles.values());

            // Update GUI on EDT
            SwingUtilities.invokeLater(() -> {
                if (mainFrame != null) {
                    mainFrame.updateAvailableFiles(allFiles);
                }
            });
        }).start();
    }

    public void playVideo(FileInfo fileInfo) {
        if (transferManager == null || fileManager.getBufferFolder() == null) {
            JOptionPane.showMessageDialog(mainFrame, "Network not started or Buffer Folder not set.");
            return;
        }

        // Start Download Thread
        new Thread(() -> {
            try {
                // 1. Find sources for this file
                java.util.List<com.cse471.network.PeerInfo> sources = new java.util.ArrayList<>();
                for (com.cse471.network.PeerInfo peer : com.cse471.network.PeerManager.getInstance().getAllPeers()) {
                    java.util.List<FileInfo> files = transferManager.requestFileList(peer);
                    if (files.stream().anyMatch(f -> f.getHash().equals(fileInfo.getHash()))) {
                        sources.add(peer);
                    }
                }

                if (sources.isEmpty()) {
                    if (mainFrame != null) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame,
                                "No sources found for file: " + fileInfo.getFileName()));
                    } else {
                        System.out.println("No sources found for file: " + fileInfo.getFileName());
                    }
                    return;
                }

                File targetFile = new File(fileManager.getBufferFolder(), fileInfo.getFileName());
                int totalChunks = fileInfo.getTotalChunks(256 * 1024);

                if (mainFrame != null) {
                    SwingUtilities.invokeLater(() -> mainFrame
                            .addActiveStream("Starting: " + fileInfo.getFileName() + " (" + totalChunks + " chunks)"));
                }

                // 2. Playback Trigger
                // Dynamic Buffering (Bonus)
                // Start conservatively (4 chunks ~ 1MB).
                // If download is slow ( > 500ms per chunk), increase buffer requirement.
                int bufferNeeded = 4;
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < totalChunks; i++) {
                    long chunkStart = System.currentTimeMillis();

                    // Simple Load Balance: Round Robin
                    com.cse471.network.PeerInfo source = sources.get(i % sources.size());

                    byte[] data = transferManager.requestChunk(source, fileInfo.getHash(), i);
                    long duration = System.currentTimeMillis() - chunkStart;

                    // Dynamic Adjustment
                    if (duration > 500) {
                        bufferNeeded++; // Network slow, buffer more
                    }

                    if (data != null && data.length > 0) {
                        fileManager.writeChunk(fileInfo.getFileName(), i, data);

                        final int currentChunk = i;
                        if (mainFrame != null) {
                            SwingUtilities
                                    .invokeLater(() -> mainFrame.addActiveStream("Downloaded Chunk " + currentChunk
                                            + " from " + source.getId() + " (" + duration + "ms)"));

                            // Start Player if buffered enough
                            if (i == bufferNeeded) {
                                SwingUtilities.invokeLater(() -> {
                                    mainFrame.getStreamPlayer().play(targetFile.getAbsolutePath());
                                    mainFrame.addActiveStream(
                                            ">>> Playing: " + fileInfo.getFileName() + " (Buffer reached "
                                                    + currentChunk
                                                    + ")");
                                });
                            }
                        }
                    } else {
                        System.err.println("Failed to download chunk " + i);
                    }
                }

                if (mainFrame != null) {
                    SwingUtilities
                            .invokeLater(
                                    () -> mainFrame.addActiveStream("Download Complete: " + fileInfo.getFileName()));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
