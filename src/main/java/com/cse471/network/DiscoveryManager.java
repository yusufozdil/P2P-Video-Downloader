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

        if (length < 8)
            return;

        // Parsing Protocol: [Flags (1)] [TTL (1)] [Port (2)] [IP (4)] [PeerID (Var)]
        int ttl = data[1] & 0xFF;
        int remotePort = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        // Extract IP from payload (Bytes 4,5,6,7)
        byte[] ipBytes = new byte[4];
        System.arraycopy(data, 4, ipBytes, 0, 4);

        InetAddress remoteAddress;
        try {
            remoteAddress = InetAddress.getByAddress(ipBytes);
        } catch (UnknownHostException e) {
            return; // Invalid IP
        }

        String remoteId = new String(data, 8, length - 8, StandardCharsets.UTF_8);

        // Ignore self
        if (remoteId.equals(myPeerId))
            return;

        // Register Peer using the IP from payload (True Origin)
        PeerInfo info = new PeerInfo(remoteId, remoteAddress, remotePort);
        if (PeerManager.getInstance().addPeer(info)) {
            System.out.println(
                    "Discovered Peer: " + remoteId + " at " + remoteAddress.getHostAddress() + ":" + remotePort);
        }

        // Forwarding Logic (Limited Scope Flooding)
        if (ttl > 0) {
            data[1] = (byte) (ttl - 1);
            forwardPacket(data, length);
        }
    }

    private void sendDiscovery(DatagramSocket socket, int ttl) {
        try {
            InetAddress myIp = getLocalIpAddress();
            if (myIp == null)
                return;

            byte[] payload = buildPayload(ttl, myTcpPort, myIp, myPeerId);

            // Broadcast Logic
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
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void forwardPacket(byte[] data, int length) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcast = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, length, broadcast, DISCOVERY_PORT);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] buildPayload(int ttl, int port, InetAddress ip, String id) {
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] ipBytes = ip.getAddress(); // 4 bytes for IPv4

        byte[] payload = new byte[8 + idBytes.length]; // 1+1+2+4 + ID

        payload[0] = 0x01; // Flags
        payload[1] = (byte) ttl;
        payload[2] = (byte) ((port >> 8) & 0xFF);
        payload[3] = (byte) (port & 0xFF);
        System.arraycopy(ipBytes, 0, payload, 4, 4); // Put IP
        System.arraycopy(idBytes, 0, payload, 8, idBytes.length);

        return payload;
    }

    private InetAddress getLocalIpAddress() {
        // Method 1: Best Way - Connect to a public DNS (doesn't actually send packets)
        // This asks the OS: "If I wanted to reach the internet, which IP would I use?"
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            InetAddress ip = socket.getLocalAddress();
            if (ip != null && !ip.isAnyLocalAddress() && !ip.isLoopbackAddress()) {
                return ip;
            }
        } catch (Exception e) {
            // Ignore and try fallback
            System.err.println("Method 1 failed: " + e.getMessage());
        }

        // Method 2: Fallback - Filtered Interface Enumeration
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                String name = iface.getDisplayName().toLowerCase();

                // Skip obviously wrong interfaces
                if (iface.isLoopback() || !iface.isUp() ||
                        name.contains("docker") ||
                        name.contains("vbox") ||
                        name.contains("vmnet") ||
                        name.contains("virtual") ||
                        name.contains("wsl")) {
                    continue;
                }

                for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
                    InetAddress ip = addr.getAddress();
                    if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) {
                        return ip;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
