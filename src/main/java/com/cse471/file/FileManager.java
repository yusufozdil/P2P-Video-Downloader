package com.cse471.file;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileManager {
    private static final int CHUNK_SIZE = 256 * 1024; // 256 KB
    private File rootFolder;
    private File bufferFolder;
    private final ConcurrentHashMap<String, FileInfo> localFiles = new ConcurrentHashMap<>(); // Key: Hash

    public FileManager() {
    }

    public void setRootFolder(File folder) {
        this.rootFolder = folder;
        if (folder != null)
            scanRootFolder();
    }

    public void setBufferFolder(File folder) {
        this.bufferFolder = folder;
        if (!folder.exists())
            folder.mkdirs();
    }

    public File getRootFolder() {
        return rootFolder;
    }

    public File getBufferFolder() {
        return bufferFolder;
    }

    public void scanRootFolder() {
        if (rootFolder == null || !rootFolder.exists())
            return;
        localFiles.clear();

        try (Stream<Path> paths = Files.walk(rootFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            // Check exclusion filter (Bonus) - Simple implementation
                            String name = path.getFileName().toString();
                            if (name.startsWith("."))
                                return; // hidden files

                            String hash = computeSha256(path);
                            FileInfo info = new FileInfo(name, Files.size(path), hash);
                            localFiles.put(hash, info); // Key by Hash to detect duplicates
                            System.out.println("Indexed: " + name + " [" + hash.substring(0, 8) + "...]");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<FileInfo> getLocalFileList() {
        return new ArrayList<>(localFiles.values());
    }

    public FileInfo getFileInfoByHash(String hash) {
        return localFiles.get(hash);
    }

    // Read a specific chunk from a file
    public byte[] getChunk(String fileHash, int chunkIndex) throws IOException {
        FileInfo info = localFiles.get(fileHash);
        if (info == null)
            throw new FileNotFoundException("File not found in catalog");

        File file = new File(rootFolder, info.getFileName()); // Assuming flat directory for simplicity
        // Ideally we store full path or search recursively again

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long offset = (long) chunkIndex * CHUNK_SIZE;
            if (offset >= info.getFileSize())
                return new byte[0];

            raf.seek(offset);
            int bytesToRead = (int) Math.min(CHUNK_SIZE, info.getFileSize() - offset);
            byte[] buffer = new byte[bytesToRead];
            raf.readFully(buffer);
            return buffer;
        }
    }

    // Write a chunk to buffer (for downloading) behavior
    // This creates a sparse file initially? Or separate chunk files?
    // Project says: "Since playback starts... reassemble all chunks".
    // Better to write into the final file at the correct offset (RandomAccessFile).
    public synchronized void writeChunk(String fileName, int chunkIndex, byte[] data) throws IOException {
        if (bufferFolder == null)
            throw new IOException("Buffer folder not set");
        File target = new File(bufferFolder, fileName);

        try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
            long offset = (long) chunkIndex * CHUNK_SIZE;
            raf.seek(offset);
            raf.write(data);
        }
    }

    private String computeSha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
