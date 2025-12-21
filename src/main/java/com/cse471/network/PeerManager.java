package com.cse471.network;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class PeerManager {
    private static final PeerManager instance = new PeerManager();
    private final ConcurrentHashMap<String, PeerInfo> peers = new ConcurrentHashMap<>();

    private PeerManager() {}

    public static PeerManager getInstance() {
        return instance;
    }

    public void addPeer(PeerInfo peer) {
        // Only add if not exists or update if needed
        peers.put(peer.getId(), peer);
        // System.out.println("Peer Discovered/Updated: " + peer);
    }

    public void removePeer(String peerId) {
        peers.remove(peerId);
        System.out.println("Peer Removed: " + peerId);
    }

    public PeerInfo getPeer(String peerId) {
        return peers.get(peerId);
    }

    public Collection<PeerInfo> getAllPeers() {
        return peers.values();
    }
}
