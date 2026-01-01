package com.cse471.network;

import java.net.InetAddress;
import java.util.Objects;

public class PeerInfo {
    private String id;
    private InetAddress address;
    private int commandPort; // TCP port for file requests
    private InetAddress relayAddress; // Gateway IP if peer is behind a different subnet

    public PeerInfo(String id, InetAddress address, int commandPort) {
        this.id = id;
        this.address = address;
        this.commandPort = commandPort;
        this.relayAddress = null;
    }

    public String getId() {
        return id;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getCommandPort() {
        return commandPort;
    }

    public InetAddress getRelayAddress() {
        return relayAddress;
    }

    public void setRelayAddress(InetAddress relayAddress) {
        this.relayAddress = relayAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return Objects.equals(id, peerInfo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        String base = id + "@" + address.getHostAddress() + ":" + commandPort;
        if (relayAddress != null) {
            return base + " (via " + relayAddress.getHostAddress() + ")";
        }
        return base;
    }
}
