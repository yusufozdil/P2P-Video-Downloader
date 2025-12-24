# P2P Video Streaming & Downloading Application
 
## Overview
This project is a decentralized **Peer-to-Peer (P2P) Video Streaming Application** written in Java. It enables multiple peers to discover each other on a local network, search for video files, and stream/download them simultaneously from multiple sources (Chunk-based transfer).

The application features a modern Swing GUI (FlatLaf), supports **multi-source downloading** for higher throughput, and includes an autonomous **Bot Mode** for simulation and testing in Docker environments.

---

## Features

- **Decentralized Discovery**: Uses UDP broadcasting with a "Limited Scope Flooding" algorithm to find peers without a central server.
- **Multi-Source Streaming**: Identifies multiple peers holding the same file (via Hash) and downloads different chunks from them in parallel.
- **Smart Video Player**: 
  - Integrated VLC Player (via VLCj) for robust codec support.
  - **Sequential Buffering**: Prioritizes downloading early chunks to allow "Play while Downloading".
  - **Software Rendering**: Optimized to avoid hardware overlay issues on macOS/Linux.
- **Modern GUI**:
  - **Themes**: Switch between Dark and Light modes instantly.
  - **Real-time Stats**: Active Streams table showing source peers, progress %, and status.
  - **Event Log**: Detailed logging of network events within the UI.
- **Bot Mode**: Headless operation support (`--bot`) for running autonomous peers in Docker containers.

---

## Prerequisites

Before running the application, ensure you have the following installed:

1.  **Java JDK 17** or higher.
2.  **Maven** (to build the project).
3.  **VLC Media Player**:
    -   **macOS**: Install VLC from [videolan.org](https://www.videolan.org/).
    -   **Windows**: Install VLC (x64).
    -   **Linux**: `sudo apt install vlc`
    -   *Note: The application relies on the system's native VLC libraries (libvlc).*

---

## Installation & Build

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/yusufozdil/P2P-Video-Downloader.git
    cd P2P-Video-Downloader
    ```

2.  **Build with Maven**:
    This will compile the code and create a "Shaded" JAR containing all dependencies.
    ```bash
    mvn clean package -DskipTests
    ```
    *The executable JAR will be located at:* `target/p2p-streaming-app-1.0-SNAPSHOT-shaded.jar`

---

## Running the Application

### 1. GUI Mode (Desktop)
To run the main application with the graphical interface:
```bash
java -jar target/p2p-streaming-app-1.0-SNAPSHOT-shaded.jar
```
*   **Search**: Enter a keyword (e.g., "video") to search the network.
*   **Play**: Double-click a file in the "Available Videos" list.
*   **Theme**: Use `View -> Toggle Theme` to change appearance.

### 2. Docker Simulation (Swarm Mode)
You can simulate a network of peers using Docker Compose. This effectively demonstrates the P2P capabilities on a single machine.

1.  **Start the Swarm**:
    ```bash
    docker-compose up --build
    ```
    *This starts 3 containers: `peer1` (Seeder), `peer2` (Seeder), and `peer3` (Leecher/Bot).*

2.  **Verify Bot Mode**:
    `peer3` is configured as a Bot. It will automatically search for files and download them from `peer1` and `peer2`. You can watch the logs:
    ```bash
    docker logs -f peer3
    ```

---

## Architecture

*   **AppController**: The central orchestrator managing the interaction between GUI, Network, and File systems.
*   **DiscoveryManager (UDP)**: Handles peer discovery using a custom protocol over UDP Port 5000 (default broadcast). Implements packet forwarding limit (TTL) to prevent network flooding.
*   **TransferManager (TCP)**: Manages direct file transfers. It acts as both a Server (handling upload requests) and a Client (requesting chunks).
*   **StreamPlayer**: Wraps the VLCj library. It utilizes a `CallbackMediaPlayerComponent` to render video frames directly to a Swing buffer, ensuring compatibility across different OS rendering pipelines.

---

## Troubleshooting

*   **VLC Not Found**: Ensure VLC Media Player is installed. On macOS, if using ARM (M1/M2/M3), ensure you have the Apple Silicon version of Java and VLC.
*   **Black Screen**: The player uses software rendering to prevent this. If issues persist, check console logs for VLC errors.
*   **Firewall**: The application needs to open a random TCP port (6000-7000) and Listen on UDP 5000. Ensure your firewall allows Java to access the local network.
