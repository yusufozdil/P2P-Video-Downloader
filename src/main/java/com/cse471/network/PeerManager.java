package com.cse471.network;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class PeerManager {
    private static final PeerManager instance = new PeerManager();
    private final ConcurrentHashMap<String, PeerInfo> peers = new ConcurrentHashMap<>();

    private PeerManager() {
    }

    // Singleton Erişimi: Uygulama genelinde tek bir PeerManager örneği döndürür.
    public static PeerManager getInstance() {
        return instance;
    }

    // Peer Ekle: Yeni bir Peer keşfedildiğinde listeye ekler. Doğrudan bağlantı
    // varsa Relay olanı yoksayar.
    public boolean addPeer(PeerInfo peer) {
        PeerInfo existing = peers.get(peer.getId());
        if (existing != null) {
            // Priority Rule: Don't downgrade from Direct to Relayed
            if (existing.getRelayAddress() == null && peer.getRelayAddress() != null) {
                // Ignore the update if it forces a relay when we have direct
                return false;
            }
        }
        PeerInfo previous = peers.put(peer.getId(), peer);
        return previous == null;
    }

    // Peer Sil: Bağlantısı kopan veya kapanan Peer'ı listeden çıkarır.
    public void removePeer(String peerId) {
        peers.remove(peerId);
        System.out.println("Peer Removed: " + peerId);
    }

    // Peer Getir: ID'si verilen Peer'ın detaylarını döner.
    public PeerInfo getPeer(String peerId) {
        return peers.get(peerId);
    }

    // Tüm Peerlar: Aktif olan tüm Peerların listesini döner.
    public Collection<PeerInfo> getAllPeers() {
        return peers.values();
    }

    // IP ile Bul: IP adresi bilinen bir Peer'ın ID ve Port bilgilerini bulur.
    public PeerInfo getPeerByIp(java.net.InetAddress ip) {
        for (PeerInfo peer : peers.values()) {
            if (peer.getAddress().equals(ip)) {
                return peer;
            }
        }
        return null;
    }
}
