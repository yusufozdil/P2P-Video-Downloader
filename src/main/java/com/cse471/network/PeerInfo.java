package com.cse471.network;

import java.net.InetAddress;
import java.util.Objects;

public class PeerInfo {
    private String id;
    private InetAddress address;
    private int commandPort; // TCP port for file requests

    public PeerInfo(String id, InetAddress address, int commandPort) {
        this.id = id;
        this.address = address;
        this.commandPort = commandPort;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return Objects.equals(id, peerInfo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id + "@" + address.getHostAddress() + ":" + commandPort;
    }
}
