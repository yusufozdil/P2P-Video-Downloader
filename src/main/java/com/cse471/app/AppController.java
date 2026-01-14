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
        this.tcpPort = 0;
    }

    // Singleton Erişimi: Uygulama kontrolcüsünün tekil örneğini döner.
    public static synchronized AppController getInstance() {
        if (instance == null) {
            instance = new AppController();
        }
        return instance;
    }

    // Başlatma: GUI ve Dosya Yöneticisini hazırlar. Headless modda varsayılan
    // klasörleri ayarlar.
    public void initialize(MainFrame frame) {
        this.mainFrame = frame;
        this.fileManager = new FileManager();

        // In headless mode, default to /data directory
        if (frame == null) {
            File defaultRoot = new File("/data");
            if (defaultRoot.exists()) {
                setRootFolder(defaultRoot);
                fileManager.setBufferFolder(new File(defaultRoot, "downloads"));
                System.out.println("Headless Mode: Root folder set to /data");
            }
        }
    }

    // Ağı Başlat: Rastgele bir port seçip Discovery ve Transfer servislerini ayağa
    // kaldırır.
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

    // Ağı Durdur: Tüm servisleri kapatır ve kaynakları serbest bırakır.
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
            if (mainFrame != null) {
            }
        }
    }

    public void setBufferFolder(File folder) {
        if (fileManager != null) {
            fileManager.setBufferFolder(folder);
        }
    }

    // Dosya Ara (Basit): Verilen kelimeyi hem yerelde hem ağda arar.
    public void searchFiles(String query) {
        searchFiles(query, "");
    }

    // Dosya Ara (Gelişmiş): Hariç tutma filtresi ile arama yapar ve sonucu GUI'ye
    // yansıtır.
    public void searchFiles(String query, String exclusionPattern) {
        if (transferManager == null)
            return;

        new Thread(() -> {
            java.util.LinkedHashMap<String, FileInfo> uniqueFiles = new java.util.LinkedHashMap<>();

            // 1. Local Files
            fileManager.getLocalFileList().stream()
                    .filter(f -> f.getFileName().toLowerCase().contains(query.toLowerCase()))
                    .filter(f -> !matchesExclusion(f.getFileName(), exclusionPattern)) // Exclusion Filter
                    .forEach(f -> uniqueFiles.put(f.getHash(), f));

            // 2. Remote Files
            for (com.cse471.network.PeerInfo peer : com.cse471.network.PeerManager.getInstance().getAllPeers()) {
                java.util.List<FileInfo> remoteFiles = transferManager.requestFileList(peer);
                if (remoteFiles != null) {
                    remoteFiles.stream()
                            .filter(f -> f.getFileName().toLowerCase().contains(query.toLowerCase()))
                            .filter(f -> !matchesExclusion(f.getFileName(), exclusionPattern)) // Exclusion Filter
                            .forEach(f -> uniqueFiles.putIfAbsent(f.getHash(), f));
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

    // Bloklayan Arama: Sonuçları liste olarak döner (Bot modu için).
    public java.util.List<FileInfo> searchFilesBlocking(String query) {
        if (transferManager == null)
            return new java.util.ArrayList<>();

        java.util.LinkedHashMap<String, FileInfo> uniqueFiles = new java.util.LinkedHashMap<>();

        // 1. Local Files
        fileManager.getLocalFileList().stream()
                .filter(f -> f.getFileName().toLowerCase().contains(query.toLowerCase()))
                .forEach(f -> uniqueFiles.put(f.getHash(), f));

        // 2. Remote Files
        for (com.cse471.network.PeerInfo peer : com.cse471.network.PeerManager.getInstance().getAllPeers()) {
            java.util.List<FileInfo> remoteFiles = transferManager.requestFileList(peer);
            if (remoteFiles != null) {
                remoteFiles.stream()
                        .filter(f -> f.getFileName().toLowerCase().contains(query.toLowerCase()))
                        .forEach(f -> uniqueFiles.putIfAbsent(f.getHash(), f));
            }
        }
        return new java.util.ArrayList<>(uniqueFiles.values());
    }

    // Helper for Exclusion Logic
    private boolean matchesExclusion(String filename, String pattern) {
        if (pattern == null || pattern.trim().isEmpty())
            return false;

        String[] patterns = pattern.split(";");
        for (String p : patterns) {
            p = p.trim();
            if (p.isEmpty())
                continue;

            // Convert glob patterns to regex
            String regex = p.replace(".", "\\.").replace("*", ".*").replace("?", ".");
            if (filename.matches("(?i)" + regex)) {
                return true;
            }
        }
        return false;
    }

    // Video Oynat: İndirmeyi başlatır ve oynatma isteği yollar.
    public void playVideo(FileInfo fileInfo) {
        startDownload(fileInfo, true);
    }

    // İndirmeyi Başlat: Dosya kaynaklarını bulur, yük dengeleme yapar ve dinamik
    // bufferlama ile indirir.
    public void startDownload(FileInfo fileInfo, boolean playVideo) {
        if (transferManager == null || fileManager.getBufferFolder() == null) {
            String msg = "Network not started or Buffer Folder not set.";
            if (mainFrame != null)
                JOptionPane.showMessageDialog(mainFrame, msg);
            else
                System.err.println(msg);
            return;
        }

        // Start Download Thread
        new Thread(() -> {
            try {
                // 1. Find sources for this file
                java.util.List<com.cse471.network.PeerInfo> sources = new java.util.ArrayList<>();
                for (com.cse471.network.PeerInfo peer : com.cse471.network.PeerManager.getInstance().getAllPeers()) {
                    java.util.List<FileInfo> files = transferManager.requestFileList(peer);
                    if (files != null && files.stream().anyMatch(f -> f.getHash().equals(fileInfo.getHash()))) {
                        sources.add(peer);
                    }
                }

                if (sources.isEmpty()) {
                    String msg = "No sources found for file: " + fileInfo.getFileName();
                    if (mainFrame != null) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mainFrame, msg));
                    } else {
                        System.out.println(msg);
                    }
                    return;
                }

                File targetFile = new File(fileManager.getBufferFolder(), fileInfo.getFileName());
                int totalChunks = fileInfo.getTotalChunks(256 * 1024);

                if (mainFrame != null) {
                    SwingUtilities.invokeLater(() -> {
                        mainFrame.log("Starting download: " + fileInfo.getFileName());
                        mainFrame.addActiveStream(fileInfo.getFileName(), "Finding Sources...", "0%", "Starting");
                    });
                } else {
                    System.out.println("Bot: Starting download -> " + fileInfo.getFileName());
                }

                // 2. Playback Trigger
                int bufferNeeded = 4;
                long startTime = System.currentTimeMillis();

                // Dynamic Buffering Variables
                double avgLatency = 0;
                int packetLossCount = 0;
                final double ALPHA = 0.2; // Moving Average Weight

                for (int i = 0; i < totalChunks; i++) {
                    long chunkStart = System.currentTimeMillis();

                    // Simple Load Balance: Round Robin
                    com.cse471.network.PeerInfo source = sources.get(i % sources.size());

                    byte[] data = transferManager.requestChunk(source, fileInfo.getHash(), i);
                    long duration = System.currentTimeMillis() - chunkStart;

                    // Dynamic Buffering Logic
                    if (data != null) {
                        // Update Exponential Moving Average of Latency
                        if (i == 0)
                            avgLatency = duration;
                        else
                            avgLatency = (ALPHA * duration) + ((1.0 - ALPHA) * avgLatency);

                        // If network is slow (Latency > 500ms), increase buffer
                        if (avgLatency > 500) {
                            bufferNeeded = Math.min(totalChunks, bufferNeeded + 1);
                            System.out.println("Network Slow (Lat: " + (int) avgLatency + "ms)! Increasing Buffer to: "
                                    + bufferNeeded);
                        }
                    } else {
                        // Packet Loss Detected
                        packetLossCount++;
                        // Penalize: Significantly increase buffer requirements on loss
                        bufferNeeded = Math.min(totalChunks, bufferNeeded + 2);
                        System.err.println("Packet Loss Detected! Increasing Buffer to: " + bufferNeeded);
                    }

                    if (data != null && data.length > 0) {
                        fileManager.writeChunk(fileInfo.getFileName(), i, data);

                        if (mainFrame != null) {
                            final int currentChunk = i;
                            final int total = totalChunks;
                            SwingUtilities.invokeLater(() -> {
                                // Calculate percentage
                                int percent = (int) ((currentChunk + 1) * 100.0 / total);
                                mainFrame.addActiveStream(fileInfo.getFileName(), source.getId(),
                                        percent + "% (Chunk " + currentChunk + ")", "Downloading");
                            });

                            // Start Player if buffered enough AND playVideo is requested
                            if (playVideo && i == bufferNeeded) {
                                SwingUtilities.invokeLater(() -> {
                                    mainFrame.getStreamPlayer().play(targetFile.getAbsolutePath());
                                    mainFrame.log(">>> Starting Playback: " + fileInfo.getFileName());
                                    mainFrame.addActiveStream(fileInfo.getFileName(), "Local Player", "Buffer Ready",
                                            "Playing");
                                });
                            }
                        } else {
                            // Headless Log
                            System.out.println("Bot: Downloaded Chunk " + i + " from " + source.getId());
                        }
                    } else {
                        System.err.println("Failed to download chunk " + i);
                    }
                }

                if (mainFrame != null) {
                    SwingUtilities.invokeLater(() -> {
                        mainFrame.log("Download Complete: " + fileInfo.getFileName());
                        mainFrame.addActiveStream(fileInfo.getFileName(), "All Sources", "100%", "Completed");
                    });
                } else {
                    System.out.println("Bot: Download Complete -> " + fileInfo.getFileName());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
