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
    private Thread serverThread;
    private boolean running = false;

    public TransferManager(FileManager fileManager, int port) {
        this.fileManager = fileManager;
        this.port = port;
    }

    public void startServer() {
        running = true;
        serverThread = new Thread(this::listenLoop, "Transfer-Server");
        serverThread.start();
        System.out.println("Transfer Server listening on TCP port " + port);
    }

    public void stop() {
        running = false;
        try {
        } catch (Exception e) {
        }
    }

    private void listenLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
            while (running) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            byte command = in.readByte();
            if (command == 0x01) { // GET_FILE_LIST
                sendFileList(out);
            } else if (command == 0x02) { // GET_CHUNK
                handleChunkRequest(in, out);
            }

        } catch (IOException e) {
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

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

    private void handleChunkRequest(DataInputStream in, DataOutputStream out) throws IOException {
        String hash = in.readUTF();
        int chunkIndex = in.readInt();

        byte[] data;
        try {
            data = fileManager.getChunk(hash, chunkIndex);
        } catch (Exception e) {
            data = new byte[0]; // Not found or error
        }

        out.writeInt(data.length);
        if (data.length > 0) {
            out.write(data);
        }
        out.flush();
    }

    // --- Client Side Methods ---

    public List<FileInfo> requestFileList(PeerInfo peer) {
        List<FileInfo> result = new ArrayList<>();
        try (Socket socket = new Socket(peer.getAddress(), peer.getCommandPort());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

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

    public byte[] requestChunk(PeerInfo peer, String fileHash, int chunkIndex) {
        try (Socket socket = new Socket(peer.getAddress(), peer.getCommandPort());
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

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
