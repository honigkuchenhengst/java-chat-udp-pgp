package packet;

import routing.RoutingManager;
import udpSocket.UdpReceiver;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.zip.CRC32;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import chunking.FileChunk;
import chunking.FileChunkManager;

import static packet.PacketType.*;

import java.io.IOException;

public class ChatApp extends Thread {
    private final RoutingManager routingManager;
    private final DatagramSocket chatSocket;
    private final int chatPort;
    private String activeChatPartnerAddress; // Speichert die IP:Port des Chatpartners als String
    private int messageIdCounter = 0;
    private String ownAddress; // Die eigene Adresse für die Anzeige in Nachrichten
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private InetAddress partnerIp;
    private int partnerPort;

    private String pendingMessage = null;
    private boolean closeAfterSend = false;
    private String pendingFilePath = null;
    private boolean sendFileAfterConnect = false;
    private int fileIdCounter = 0;
    private final FileChunkManager fileChunkManager = new FileChunkManager();

    public ChatApp(RoutingManager routingManager, int chatPort) throws SocketException, UnknownHostException {
        this.routingManager = routingManager;
        this.chatSocket = new DatagramSocket(chatPort);
        this.chatPort = chatPort;
        this.activeChatPartnerAddress = null;
        this.partnerIp = null;
        this.partnerPort = -1;
        // Speichere die eigene Adresse für die Nachrichten-Signatur
        this.ownAddress = InetAddress.getLocalHost().getHostAddress() + ":" + this.chatPort;
    }

    @Override
    public void run() {
        // Starte Receiver...
        UdpReceiver receiver = new UdpReceiver(chatSocket, 4, routingManager, this);
        receiver.start();

        System.out.println("Chat-Anwendung gestartet auf " + ownAddress);
        printHelp();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            String prompt = activeChatPartnerAddress != null ? "[" + activeChatPartnerAddress + "] > " : "> ";
            System.out.print(prompt);

            String input = scanner.nextLine();
            if (input.trim().isEmpty()) continue;

            if (input.startsWith("/")) {
                handleCommand(input);
            } else {
                handleMessageInput(input);
            }
        }
    }

    private void handleCommand(String commandInput) {
        String[] parts = commandInput.trim().split("\\s+", 3);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "/chat":
                if (parts.length < 2) {
                    System.out.println("FEHLER: Bitte eine Adresse angeben. Verwendung: /chat <IP:Port>");
                } else {
                    initiateHandshake(parts[1]);
                }
                break;
            case "/msg":
                if (parts.length < 3) {
                    System.out.println("FEHLER: Verwendung: /msg <IP:Port> <Nachricht>");
                } else {
                    if (connectionState != ConnectionState.CONNECTED || !parts[1].equals(activeChatPartnerAddress)) {

                        pendingMessage = parts[2];
                        closeAfterSend = true;
                        initiateHandshake(parts[1]);
                    } else {
                        sendMessage(parts[1], parts[2]);
                        try {
                            sendControlPacket(FIN, partnerIp, partnerPort);
                            connectionState = ConnectionState.FIN_WAIT;
                        } catch (Exception e) {
                            // ignore
                        }

                    }
                }
                break;
            case "/file":
                if (parts.length < 3) {
                    System.out.println("FEHLER: Verwendung: /file <IP:Port> <Pfad>");
                } else {
                    if (connectionState != ConnectionState.CONNECTED || !parts[1].equals(activeChatPartnerAddress)) {
                        pendingFilePath = parts[2];
                        sendFileAfterConnect = true;
                        initiateHandshake(parts[1]);
                    } else {
                        sendFile(parts[1], parts[2]);
                    }
                }
                break;
            case "/list":
                routingManager.printKnownNodes(); // Ruft die neue, einfache Methode auf
                break;
            case "/connect":
                if (parts.length < 2) {
                    System.out.println("FEHLER: Verwendung: /connect <IP:Port>");
                } else {
                    String[] addrParts = parts[1].split(":");
                    if (addrParts.length != 2) {
                        System.out.println("FEHLER: Ungültiges Adressformat. Erwartet: IP:Port");
                        break;
                    }
                    try {
                        InetAddress ip = InetAddress.getByName(addrParts[0]);
                        int port = Integer.parseInt(addrParts[1]);
                        routingManager.connect(ip, port);
                    } catch (UnknownHostException e) {
                        System.out.println("FEHLER: Host '" + addrParts[0] + "' ist unbekannt.");
                    } catch (NumberFormatException e) {
                        System.out.println("FEHLER: Ungültiger Port in Adresse '"+ parts[1] +"'.");
                    }
                }
                break;
            case "/disconnect":
                if (parts.length < 2) {
                    System.out.println("FEHLER: Verwendung: /disconnect <IP:Port>");
                } else {
                    String[] addrParts = parts[1].split(":");
                    if (addrParts.length != 2) {
                        System.out.println("FEHLER: Ungültiges Adressformat. Erwartet: IP:Port");
                        break;
                    }
                    try {
                        InetAddress ip = InetAddress.getByName(addrParts[0]);
                        int port = Integer.parseInt(addrParts[1]);
                        routingManager.disconnect(ip, port);
                    } catch (UnknownHostException e) {
                        System.out.println("FEHLER: Host '" + addrParts[0] + "' ist unbekannt.");
                    } catch (NumberFormatException e) {
                        System.out.println("FEHLER: Ungültiger Port in Adresse '"+ parts[1] +"'.");
                    }
                }
                break;
            case "/quit":
                if (connectionState == ConnectionState.CONNECTED) {
                    try {
                        sendControlPacket(FIN, partnerIp, partnerPort);
                        connectionState = ConnectionState.FIN_WAIT;
                    } catch (Exception e) {
                        // ignore
                    }
                }
                System.out.println("Anwendung wird beendet...");
                chatSocket.close();
                System.exit(0);
                break;
            case "/help":
                printHelp();
                break;
            default:
                System.out.println("Unbekannter Befehl. Nutze /help für eine Übersicht.");
                break;
        }
    }

    private void handleMessageInput(String messageText) {
        if (activeChatPartnerAddress != null) {
            if (connectionState == ConnectionState.CONNECTED) {
                sendMessage(activeChatPartnerAddress, messageText);
            } else {
                System.out.println("Keine aktive Verbindung. Bitte warte auf den Handshake.");
            }
        } else {
            System.out.println("Kein aktiver Chatpartner. Nutze '/chat <IP:Port>' oder '/msg <IP:Port> <Nachricht>'.");
        }
    }

    public void handshake(String destinationAddress, PacketType packetType) {
        try {
            String[] addrParts = destinationAddress.split(":");
            if (addrParts.length != 2) {
                System.out.println("FEHLER: Ungültiges Adressformat. Erwartet: IP:Port");
                return;
            }
            InetAddress destIp = InetAddress.getByName(addrParts[0]);
            int destPort = Integer.parseInt(addrParts[1]);

            EmptyPayload emptyPayload = new EmptyPayload();
            int checksum = calculateChecksum(emptyPayload.serialize());

            // Header bauen
            PacketHeader header = new PacketHeader(
                    InetAddress.getLocalHost(), this.chatPort,
                    destIp, destPort,
                    packetType,
                    emptyPayload.serialize().length,
                    checksum
            );

            // Packet bauen & über den RoutingManager verschicken
            Packet packet = new Packet(header, emptyPayload);
            routingManager.sendMessageTo(chatSocket, destIp, destPort, packet);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sendet eine Nachricht an eine Ziel-Adresse (IP:Port).
     */
    private void sendMessage(String destinationAddress, String messageText) {
        try {
            // Parse die Ziel-Adresse
            String[] addrParts = destinationAddress.split(":");
            if (addrParts.length != 2) {
                System.out.println("FEHLER: Ungültiges Adressformat. Erwartet: IP:Port");
                return;
            }
            InetAddress destIp = InetAddress.getByName(addrParts[0]);
            int destPort = Integer.parseInt(addrParts[1]);

            if (connectionState != ConnectionState.CONNECTED) {
                System.out.println("Keine Verbindung. Fuehre zuerst /chat fuer den Handshake aus.");
                return;
            }

            // Payload bauen (ohne Namen)
            MessagePayload payload = new MessagePayload(
                    messageIdCounter++, 1, 1, "[" + this.ownAddress + "]: " + messageText
            );

            // Checksumme berechnen
            int checksum = calculateChecksum(payload.serialize());

            // Header bauen
            PacketHeader header = new PacketHeader(
                    InetAddress.getLocalHost(), this.chatPort,
                    destIp, destPort,
                    PacketType.MESSAGE,
                    payload.serialize().length,
                    checksum
            );

            // Packet bauen & über den RoutingManager verschicken
            Packet packet = new Packet(header, payload);
            routingManager.sendMessageTo(chatSocket, destIp, destPort, packet);

        } catch (UnknownHostException e) {
            System.out.println("FEHLER: Host '" + destinationAddress + "' ist unbekannt.");
        } catch (NumberFormatException e) {
            System.out.println("FEHLER: Ungültiger Port in Adresse '" + destinationAddress + "'.");
        }
    }

    private void sendFile(String destinationAddress, String path) {
        try {
            String[] addrParts = destinationAddress.split(":");
            if (addrParts.length != 2) {
                System.out.println("FEHLER: Ungültiges Adressformat. Erwartet: IP:Port");
                return;
            }
            InetAddress destIp = InetAddress.getByName(addrParts[0]);
            int destPort = Integer.parseInt(addrParts[1]);

            if (connectionState != ConnectionState.CONNECTED) {
                System.out.println("Keine Verbindung. Fuehre zuerst /chat fuer den Handshake aus.");
                return;
            }

            File file = new File(path);
            if (!file.exists()) {
                System.out.println("Datei nicht gefunden: " + path);
                return;
            }

            byte[] fileData = Files.readAllBytes(file.toPath());

            int fileId = fileIdCounter++;
            int mtu = 1000;
            int firstChunkSize = mtu - 40;
            int otherChunkSize = mtu - 10;

            int totalChunks = 1 + (fileData.length > firstChunkSize ?
                    (int) Math.ceil((fileData.length - firstChunkSize) / (double) otherChunkSize) : 0);

            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunkData;
                String fname = null;
                if (i == 0) {
                    int len = Math.min(firstChunkSize, fileData.length);
                    chunkData = new byte[len];
                    System.arraycopy(fileData, 0, chunkData, 0, len);
                    offset += len;
                    fname = file.getName();
                } else {
                    int len = Math.min(otherChunkSize, fileData.length - offset);
                    chunkData = new byte[len];
                    System.arraycopy(fileData, offset, chunkData, 0, len);
                    offset += len;
                }

                FilePayload payload = new FilePayload(fileId, i, totalChunks, fname, chunkData);
                int checksum = calculateChecksum(payload.serialize());

                PacketHeader header = new PacketHeader(
                        InetAddress.getLocalHost(), this.chatPort,
                        destIp, destPort,
                        PacketType.FILE,
                        payload.serialize().length,
                        checksum
                );
                Packet packet = new Packet(header, payload);
                routingManager.sendMessageTo(chatSocket, destIp, destPort, packet);
            }

        } catch (Exception e) {
            System.out.println("FEHLER beim Senden der Datei: " + e.getMessage());
        }
    }

    private void printHelp() {
        System.out.println("--- Verfügbare Befehle ---");
        System.out.println("/chat <IP:Port>    - Startet einen interaktiven Chat.");
        System.out.println("/msg <IP:Port> <text> - Sendet eine einzelne Nachricht.");
        System.out.println("/file <IP:Port> <pfad> - Sendet eine Datei.");
        System.out.println("/connect <IP:Port>   - Verbindet sich mit einem Nachbarn.");
        System.out.println("/disconnect <IP:Port> - Trennt die Verbindung zu einem Nachbarn.");
        System.out.println("/list              - Zeigt alle erreichbaren Teilnehmer an.");
        System.out.println("/help              - Zeigt diese Hilfe an.");
        System.out.println("/quit              - Beendet die Anwendung.");
        System.out.println("--------------------------");
    }

    private static int calculateChecksum(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return (int) crc32.getValue();
    }

    private void initiateHandshake(String address) {
        try {
            String[] parts = address.split(":");
            partnerIp = InetAddress.getByName(parts[0]);
            partnerPort = Integer.parseInt(parts[1]);
            activeChatPartnerAddress = address;
            sendControlPacket(SYN, partnerIp, partnerPort);
            connectionState = ConnectionState.SYN_SENT;
            System.out.println("Handshake gestartet mit " + address);
        } catch (Exception e) {
            System.out.println("FEHLER beim Handshake: " + e.getMessage());
        }
    }

    private void sendControlPacket(PacketType type, InetAddress ip, int port) throws IOException {
        EmptyPayload payload = new EmptyPayload();
        int checksum = calculateChecksum(payload.serialize());
        PacketHeader header = new PacketHeader(
                InetAddress.getLocalHost(), this.chatPort,
                ip, port,
                type,
                0,
                checksum
        );
        Packet p = new Packet(header, payload);
        routingManager.sendMessageTo(chatSocket, ip, port, p);
    }

    private void sendPendingIfReady() {
        if (pendingMessage != null) {
            sendMessage(activeChatPartnerAddress, pendingMessage);
            pendingMessage = null;
            if (closeAfterSend) {
                try {
                    sendControlPacket(FIN, partnerIp, partnerPort);
                    connectionState = ConnectionState.FIN_WAIT;
                } catch (Exception e) {
                    // ignore
                }
                closeAfterSend = false;
            }
        }
        if (pendingFilePath != null) {
            sendFile(activeChatPartnerAddress, pendingFilePath);
            pendingFilePath = null;
            sendFileAfterConnect = false;
        }
    }

    public synchronized void onPacketReceived(Packet packet) {
        PacketHeader header = packet.getHeader();
        String source = header.getSourceIp().getHostAddress() + ":" + header.getSourcePort();
        switch (header.getType()) {
            case SYN:
                if (connectionState == ConnectionState.DISCONNECTED) {
                    partnerIp = header.getSourceIp();
                    partnerPort = header.getSourcePort();
                    activeChatPartnerAddress = source;
                    try {
                        sendControlPacket(SYN_ACK, partnerIp, partnerPort);
                        connectionState = ConnectionState.SYN_RECEIVED;
                    } catch (IOException e) {
                        // ignore
                    }
                    System.out.println("SYN empfangen von " + source);
                }
                break;
            case SYN_ACK:
                if (connectionState == ConnectionState.SYN_SENT) {
                    try {
                        sendControlPacket(ACK, partnerIp, partnerPort);
                        connectionState = ConnectionState.CONNECTED;
                        sendPendingIfReady();

                    } catch (IOException e) {
                        // ignore
                    }
                    System.out.println("Verbindung hergestellt mit " + activeChatPartnerAddress);
                }
                break;
            case ACK:
                if (connectionState == ConnectionState.SYN_RECEIVED) {
                    connectionState = ConnectionState.CONNECTED;

                    sendPendingIfReady();

                    System.out.println("Verbindung hergestellt mit " + activeChatPartnerAddress);
                }
                break;
            case FIN:
                if (activeChatPartnerAddress != null && activeChatPartnerAddress.equals(source)) {
                    try {
                        sendControlPacket(FIN_ACK, partnerIp, partnerPort);
                    } catch (IOException e) {
                        // ignore
                    }
                    connectionState = ConnectionState.DISCONNECTED;
                    activeChatPartnerAddress = null;
                    System.out.println("Verbindung von " + source + " geschlossen.");
                }
                break;
            case FIN_ACK:
                if (connectionState == ConnectionState.FIN_WAIT) {
                    connectionState = ConnectionState.DISCONNECTED;
                    activeChatPartnerAddress = null;
                    System.out.println("Verbindung geschlossen.");
                }
                break;
            case MESSAGE:
                if (packet.getPayload() instanceof MessagePayload) {
                    MessagePayload mp = (MessagePayload) packet.getPayload();
                    System.out.println("Nachricht empfangen: " + mp.getMessageText());
                }
                break;
            case FILE:
                if (packet.getPayload() instanceof FilePayload) {
                    FilePayload fp = (FilePayload) packet.getPayload();
                    FileChunk chunk = new FileChunk(fp.getFileId(), fp.getChunkNumber(), fp.getTotalChunks(), fp.getData(),
                            fp.getFileName() != null ? fp.getFileName().getBytes() : new byte[30]);
                    fileChunkManager.addChunk(chunk);
                    if (fileChunkManager.isComplete(fp.getFileId())) {
                        try {
                            byte[] fdata = fileChunkManager.assembleFile(fp.getFileId());
                            String name = fileChunkManager.getFileName(fp.getFileId());
                            if (name == null || name.isEmpty()) name = "received.bin";
                            FileOutputStream fos = new FileOutputStream("recv_" + name);
                            fos.write(fdata);
                            fos.close();
                            System.out.println("Datei empfangen und gespeichert als recv_" + name);
                        } catch (IOException e) {
                            System.out.println("Fehler beim Speichern der Datei: " + e.getMessage());
                        }
                    }
                }
                break;
            default:
                break;
        }
    }
}