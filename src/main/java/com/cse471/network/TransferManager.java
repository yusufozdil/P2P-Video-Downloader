package com.cse471.network;

import com.cse471.file.FileInfo;
import com.cse471.file.FileManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class TransferManager {
    private final FileManager fileManager;
    private final int port;
    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
    private Thread serverThread;
    private boolean running = false;

    // Yapıcı Metot: Dosya yöneticisini ve hangi portta sunucu açılacağını belirler.
    public TransferManager(FileManager fileManager, int port) {
        this.fileManager = fileManager;
        this.port = port;
    }

    // Sunucuyu Başlatır: Arka planda gelen TCP isteklerini dinlemeye başlar.
    public void startServer() {
        running = true;
        serverThread = new Thread(this::listenLoop, "Transfer-Server");
        serverThread.start();
        System.out.println("Transfer Server listening on TCP port " + port);
    }

    // Sunucuyu Durdurur: Dinlemeyi keser ve tüm aktif işlemleri kapatır.
    public void stop() {
        running = false;
        try {
            executor.shutdown();
        } catch (Exception e) {
        }
    }

    // Dinleme Döngüsü: Sürekli olarak yeni bağlantı kabul eder (accept) ve işlenmek
    // üzere threade atar.
    private void listenLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
            while (running) {
                Socket client = serverSocket.accept();
                executor.submit(() -> handleClient(client));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // İstemci Yöneticisi: Gelen bağlantının ne istediğini (Dosya Listesi, Chunk,
    // Relay) anlar ve yönlendirir.
    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            byte command = in.readByte();
            if (command == 0x01) { // GET_FILE_LIST
                sendFileList(out);
            } else if (command == 0x02) { // GET_CHUNK
                handleChunkRequest(in, out);
            } else if (command == 0x03) { // RELAY_REQUEST
                handleRelayRequest(in, out);
            }

        } catch (IOException e) {
        } finally {
            try {
                if (!socket.isClosed())
                    socket.close();
            } catch (IOException e) {
            }
        }
    }

    // Relay (Köprü) İsteği: Bu sunucuyu aracı olarak kullanıp başka bir hedefle
    // bağlantı kurar.
    private void handleRelayRequest(DataInputStream in, DataOutputStream out) {
        Socket targetSocket = null;
        try {
            System.out.println("DEBUG: Handling Relay Request...");
            // Read Target Info
            byte[] ipBytes = new byte[4];
            in.readFully(ipBytes);
            InetAddress targetIp = InetAddress.getByAddress(ipBytes);
            int targetPort = in.readInt();

            System.out.println("DEBUG: Relay Target: " + targetIp + ":" + targetPort);

            // Connect to Target
            System.out.println("DEBUG: Connecting to target...");
            targetSocket = new Socket(targetIp, targetPort);
            targetSocket.setSoTimeout(10000); // 10s timeout
            System.out.println("DEBUG: Connected to target.");

            // Send OK to Client
            out.writeByte(0x00); // Success
            out.flush();
            System.out.println("DEBUG: Sent OK to client. Starting bridge.");

            // Bridge Connections
            Socket finalTargetSocket = targetSocket;
            java.util.concurrent.Future<?> f1 = executor.submit(() -> {
                try {
                    copyStream(in, finalTargetSocket.getOutputStream());
                } catch (IOException e) {
                    System.out.println("DEBUG: Relay Pipe 1 Error: " + e.getMessage());
                }
            });
            java.util.concurrent.Future<?> f2 = executor.submit(() -> {
                try {
                    copyStream(finalTargetSocket.getInputStream(), out);
                } catch (IOException e) {
                    System.out.println("DEBUG: Relay Pipe 2 Error: " + e.getMessage());
                }
            });

            try {
                f1.get();
                f2.get();
            } catch (Exception e) {
                // Interrupted or ExecutionException
            }
            System.out.println("DEBUG: Relay session finished.");

        } catch (Exception e) {
            System.err.println("Relay Error: " + e.getMessage());
            e.printStackTrace();
            try {
                // Try to send error to client if possible
                out.writeByte(0xFF);
            } catch (IOException ignored) {
            }
        } finally {
            try {
                if (targetSocket != null)
                    targetSocket.close();
            } catch (IOException e) {
            }
        }
    }

    // Akış Kopyalayıcı: Bir yerden gelen veriyi (InputStream) diğer tarafa
    // (OutputStream) aynen aktarır. Relay için kullanılır.
    private void copyStream(InputStream in, OutputStream out) {
        // Helper to pipe streams
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            // Stream closed
        }
    }

    // Dosya Listesi Gönder: Paylaşımdaki dosyaların isim, boyut ve hash bilgilerini
    // karşı tarafa yollar.
    private void sendFileList(DataOutputStream out) throws IOException {
        List<FileInfo> files = fileManager.getLocalFileList();
        out.writeInt(files.size());
        for (FileInfo info : files) {
            out.writeUTF(info.getFileName());
            out.writeLong(info.getFileSize());
            out.writeUTF(info.getHash());
        }
        out.flush();
    }

    // Chunk (Parça) Gönder: İstenen dosyanın belirli bir parçasını okur ve karşı
    // tarafa gönderir.
    private void handleChunkRequest(DataInputStream in, DataOutputStream out) throws IOException {
        String hash = in.readUTF();
        int chunkIndex = in.readInt();

        byte[] data;
        try {
            data = fileManager.getChunk(hash, chunkIndex);
        } catch (Exception e) {
            data = new byte[0];
        }

        out.writeInt(data.length);
        if (data.length > 0) {
            out.write(data);
        }
        out.flush();
    }

    // --- Client Side Methods ---

    // [İstemci] Dosya Listesi İste: Hedef Peer'a bağlanıp paylaştığı dosyaları
    // sorar.
    public List<FileInfo> requestFileList(PeerInfo peer) {
        List<FileInfo> result = new ArrayList<>();

        // Determine Connection Target (Direct or Relay)
        InetAddress targetIp = peer.getRelayAddress() != null ? peer.getRelayAddress() : peer.getAddress();
        int targetPort = peer.getCommandPort(); // Default to Target's Port

        if (peer.getRelayAddress() != null) {
            // Find the Relay Peer to get its REAL listening port
            PeerInfo relayPeer = PeerManager.getInstance().getPeerByIp(peer.getRelayAddress());
            if (relayPeer != null) {
                targetPort = relayPeer.getCommandPort();
            }
        }

        try (Socket socket = new Socket(targetIp, targetPort);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            if (peer.getRelayAddress() != null) {
                // Handshake Relay
                out.writeByte(0x03); // RELAY_REQUEST
                out.write(peer.getAddress().getAddress()); // Final Dest IP
                out.writeInt(peer.getCommandPort()); // Final Dest Port : IMPORTANT -> This is where we tell Relay where
                                                     // to go
                out.flush();

                byte status = in.readByte();
                if (status != 0x00)
                    throw new IOException("Relay Refused");
            }

            out.writeByte(0x01); // GET_FILE_LIST
            out.flush();

            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                String name = in.readUTF();
                long size = in.readLong();
                String hash = in.readUTF();
                result.add(new FileInfo(name, size, hash));
            }

        } catch (IOException e) {
            System.err.println("RequestFileList failed to " + peer.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    // [İstemci] Chunk İste: Hedef Peer'a bağlanıp belirli bir dosyanın belirli bir
    // parçasını ister.
    public byte[] requestChunk(PeerInfo peer, String fileHash, int chunkIndex) {
        InetAddress targetIp = peer.getRelayAddress() != null ? peer.getRelayAddress() : peer.getAddress();
        int targetPort = peer.getCommandPort();

        if (peer.getRelayAddress() != null) {
            PeerInfo relayPeer = PeerManager.getInstance().getPeerByIp(peer.getRelayAddress());
            if (relayPeer != null) {
                targetPort = relayPeer.getCommandPort();
            }
        }

        try (Socket socket = new Socket(targetIp, targetPort);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            if (peer.getRelayAddress() != null) {
                // Handshake Relay
                out.writeByte(0x03); // RELAY_REQUEST
                out.write(peer.getAddress().getAddress()); // Final Dest IP
                out.writeInt(peer.getCommandPort()); // Final Dest Port
                out.flush();

                byte status = in.readByte();
                if (status != 0x00)
                    throw new IOException("Relay Refused");
            }

            out.writeByte(0x02); // GET_CHUNK
            out.writeUTF(fileHash);
            out.writeInt(chunkIndex);
            out.flush();

            int length = in.readInt();
            if (length > 0) {
                byte[] buffer = new byte[length];
                in.readFully(buffer);
                return buffer;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
