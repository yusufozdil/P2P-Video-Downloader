package com.cse471.file;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class FileManager {
    private static final int CHUNK_SIZE = 256 * 1024; // 256 KB
    private File rootFolder;
    private File bufferFolder;
    private final ConcurrentHashMap<String, FileInfo> localFiles = new ConcurrentHashMap<>(); // Key: Hash
    private final ConcurrentHashMap<String, Path> localFilePaths = new ConcurrentHashMap<>(); // Key: Hash -> Absolute
                                                                                              // Path

    public FileManager() {
    }

    // Kök Klasör Ayarla: Paylaşıma açılacak ana klasörü belirler ve içindekileri
    // tarar.
    public void setRootFolder(File folder) {
        this.rootFolder = folder;
        if (folder != null)
            scanRootFolder();
    }

    // Buffer Klasörü Ayarla: İndirilen dosyaların (ve parçaların) nereye
    // kaydedileceğini belirler.
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

    // Klasörü Tara (Index): Kök klasördeki tüm dosyaları gezer, Hash'lerini
    // hesaplar ve listeye ekler.
    public void scanRootFolder() {
        if (rootFolder == null || !rootFolder.exists())
            return;
        localFiles.clear();
        localFilePaths.clear();

        try (Stream<Path> paths = Files.walk(rootFolder.toPath())) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            String name = path.getFileName().toString();
                            if (name.startsWith("."))
                                return;

                            String hash = computeSha256(path);
                            FileInfo info = new FileInfo(name, Files.size(path), hash);
                            localFiles.put(hash, info); // Key by Hash
                            localFilePaths.put(hash, path.toAbsolutePath()); // Store full path
                            System.out.println("Indexed: " + name + " [" + hash.substring(0, 8) + "...] at " + path);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Yerel Dosya Listesi: Bu bilgisayarda paylaşıma açık olan dosyaların listesini
    // döner.
    public List<FileInfo> getLocalFileList() {
        return new ArrayList<>(localFiles.values());
    }

    // Hash ile Bul: Hash koduna göre dosya bilgisini getirir.
    public FileInfo getFileInfoByHash(String hash) {
        return localFiles.get(hash);
    }

    // Read a specific chunk from a file
    // Parça Oku (Read Chunk): İstenen dosyanın belirli bir parçasını diskten okur.
    public byte[] getChunk(String fileHash, int chunkIndex) throws IOException {
        FileInfo info = localFiles.get(fileHash);
        Path path = localFilePaths.get(fileHash);

        if (info == null || path == null)
            throw new FileNotFoundException("File not found in catalog");

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
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

    // Parça Yaz (Write Chunk): Ağdan indirilen bir veri parçasını diske, doğru
    // konumuna yazar.
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

    // SHA-256 Hesapla: Dosyanın içeriğine göre benzersiz "Parmak İzi"ni (Hash)
    // oluşturur.
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
