package com.cse471.network;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class PeerManager {
    private static final PeerManager instance = new PeerManager();
    private final ConcurrentHashMap<String, PeerInfo> peers = new ConcurrentHashMap<>();

    private PeerManager() {
    }

    public static PeerManager getInstance() {
        return instance;
    }

    public boolean addPeer(PeerInfo peer) {
        PeerInfo previous = peers.put(peer.getId(), peer);
        return previous == null;
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
