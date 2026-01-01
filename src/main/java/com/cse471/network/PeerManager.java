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

    public PeerInfo getPeerByIp(java.net.InetAddress ip) {
        for (PeerInfo peer : peers.values()) {
            if (peer.getAddress().equals(ip)) {
                return peer;
            }
        }
        return null;
    }
}
