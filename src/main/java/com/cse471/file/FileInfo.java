package com.cse471.file;

import java.io.Serializable;

public class FileInfo implements Serializable {
    private String fileName;
    private long fileSize;
    private String hash; // SHA-256
    private String peerId; // Who has this file

    // Yapıcı Metot: Dosya adı, boyutu ve hash bilgisini saklar.
    public FileInfo(String fileName, long fileSize, String hash) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.hash = hash;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getHash() {
        return hash;
    }

    // Total chunks calculation
    // Toplam Parça Sayısı: Dosyanın boyutuna göre kaç parçaya (chunk) bölüneceğini
    // hesaplar.
    public int getTotalChunks(int chunkSize) {
        return (int) Math.ceil((double) fileSize / chunkSize);
    }

    // Metin Formatı: Dosya adını ve boyutunu (KB cinsinden) yazdırır.
    @Override
    public String toString() {
        return fileName + " (" + (fileSize / 1024) + " KB)";
    }
}
