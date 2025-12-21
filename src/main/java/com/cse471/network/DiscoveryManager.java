package com.cse471.network;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscoveryManager {
    private static final int DISCOVERY_PORT = 50000;
    private static final int DISCOVERY_INTERVAL_MS = 5000;
    private static final int INITIAL_TTL = 3; // Hop limit

    private final String myPeerId;
    private final int myTcpPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;
    private Thread announcerThread;

    public DiscoveryManager(String myPeerId, int myTcpPort) {
        this.myPeerId = myPeerId;
        this.myTcpPort = myTcpPort;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            listenerThread = new Thread(this::listenLoop, "Discovery-Listener");
            announcerThread = new Thread(this::announceLoop, "Discovery-Announcer");
            listenerThread.start();
            announcerThread.start();
            System.out.println("Discovery Manager started. ID: " + myPeerId + ", TCP: " + myTcpPort);
        }
    }

    public void stop() {
        running.set(false);
        if (listenerThread != null)
            listenerThread.interrupt();
        if (announcerThread != null)
            announcerThread.interrupt();
    }

    private void announceLoop() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            while (running.get()) {
                sendDiscovery(socket, INITIAL_TTL);
                Thread.sleep(DISCOVERY_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            // Stop gracefully
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void listenLoop() {
        byte[] buffer = new byte[1024];
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));

            while (running.get()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                processPacket(packet, socket); // Pass socket to allow forwarding
            }
        } catch (IOException e) {
            if (running.get())
                e.printStackTrace();
        }
    }

    private void processPacket(DatagramPacket packet, DatagramSocket serverSocket) {
        byte[] data = packet.getData();
        int length = packet.getLength();

        if (length < 4)
            return; // Too short

        // Parsing Protocol: [Flags (1)] [TTL (1)] [Port (2)] [PeerID (Var)]
        // byte flags = data[0]; // Currently unused
        int ttl = data[1] & 0xFF;
        int remotePort = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        String remoteId = new String(data, 4, length - 4, StandardCharsets.UTF_8);

        // Ignore self
        if (remoteId.equals(myPeerId))
            return;

        // Register Peer
        PeerInfo info = new PeerInfo(remoteId, packet.getAddress(), remotePort);
        PeerManager.getInstance().addPeer(info);

        // Forwarding Logic (Limited Scope Flooding)
        if (ttl > 0) {
            // Decrement TTL and Forward
            // NOTE: In a real mesh, we would re-broadcast.
            // But doing a naive re-broadcast on the same broadcast domain (LAN)
            // creates a storm. We assume this logic is for "bridging" or just demonstrating
            // TTL.
            // For this project, to avoid infinite loops on localhost, we might want to be
            // careful.
            // Requirement says: "P2P nodes should be able to UDP flood... implement limited
            // scope flooding."

            // To be safe and compliant: We only forward if we haven't seen this "Packet"
            // before?
            // But we don't have unique packet IDs.
            // Simple approach: Just decrement and send, but maybe limit frequency or check
            // if we already have the peer.
            // If we already knew the peer, maybe we don't need to flood again?

            // For now, let's strictly follow the TTL rule:
            // sendDiscoveryPacket(..., ttl - 1, ...);
            // However, since everyone is on valid broadcast IP, re-sending receiving packet
            // puts it back on wire.
            // We need a separate socket for sending to avoid binding conflict?
            // No, the serverSocket is bound to port 50000 key. We can't send FROM 50000
            // easily if bound?
            // Actually we can using the same socket.

            // Logic disabled to prevent local broadcast storm in single-subnet/localhost
            // simulation
            // unless explicit requirement enforces it.
            // Implementation:
            forwardDiscovery(ttl - 1, remotePort, remoteId);
        }
    }

    private void sendDiscovery(DatagramSocket socket, int ttl) {
        byte[] payload = buildPayload(ttl, myTcpPort, myPeerId);

        try {
            // Try global broadcast first (works on some systems)
            // InetAddress globalBroadcast = InetAddress.getByName("255.255.255.255");
            // socket.send(new DatagramPacket(payload, payload.length, globalBroadcast,
            // DISCOVERY_PORT));

            // Iterate over all interfaces and send to their broadcast addresses
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp())
                    continue;

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        try {
                            DatagramPacket packet = new DatagramPacket(payload, payload.length, broadcast,
                                    DISCOVERY_PORT);
                            socket.send(packet);
                        } catch (Exception ignored) {
                            // Some interfaces might fail, ignore and continue
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void forwardDiscovery(int ttl, int tcpPort, String peerId) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] payload = buildPayload(ttl, tcpPort, peerId);
            InetAddress broadcast = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(payload, payload.length, broadcast, DISCOVERY_PORT);
            socket.send(packet);
            // System.out.println("Forwarding packet from " + peerId + " (TTL=" + ttl +
            // ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] buildPayload(int ttl, int port, String id) {
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[4 + idBytes.length];

        payload[0] = 0x01; // Flags (Broadcast)
        payload[1] = (byte) ttl;
        payload[2] = (byte) ((port >> 8) & 0xFF);
        payload[3] = (byte) (port & 0xFF);
        System.arraycopy(idBytes, 0, payload, 4, idBytes.length);

        return payload;
    }
}
