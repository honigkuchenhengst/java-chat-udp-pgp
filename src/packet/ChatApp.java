package packet;

import chunking.MessageChunk;
import routing.RoutingManager;
import udpSocket.UdpReceiver;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.zip.CRC32;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import chunking.FileChunk;
import chunking.FileChunkManager;

import static packet.PacketType.*;

import java.io.IOException;
import chunking.MessageChunkManager;

public class ChatApp extends Thread {
    private final RoutingManager routingManager;
    private final DatagramSocket chatSocket;
    private final int chatPort;
    private String activeChatPartnerAddress; // Speichert die IP:Port des Chatpartners als String
    private String ownAddress; // Die eigene Adresse für die Anzeige in Nachrichten
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;
    private InetAddress partnerIp;
    private int partnerPort;

    private String pendingMessage = null;
    private boolean closeAfterSend = false;
    private String pendingFilePath = null;
    private boolean sendFileAfterConnect = false;
    private int idCounter = 0;
    private final FileChunkManager fileChunkManager = new FileChunkManager();
    private final java.util.Map<Integer, Integer> ackMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Integer, Integer> expectedChunkMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final MessageChunkManager messageChunkManager = new MessageChunkManager();
    private final java.util.Map<Integer, Integer> expectedMsgChunkMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final String ownIP;

    public ChatApp(RoutingManager routingManager, int chatPort, String ownAddress) throws SocketException, UnknownHostException {
        this.routingManager = routingManager;
        this.chatSocket = new DatagramSocket(chatPort);
        this.chatPort = chatPort;
        this.activeChatPartnerAddress = null;
        this.partnerIp = null;
        this.partnerPort = -1;
        this.ownIP = ownAddress;
        // Speichere die eigene Adresse für die Nachrichten-Signatur
        //this.ownAddress = InetAddress.getLocalHost().getHostAddress() + ":" + this.chatPort;
        this.ownAddress = InetAddress.getByName(ownIP) + ":" + this.chatPort;
    }

    @Override
    public void run() {
        // Starte Receiver...
        UdpReceiver receiver = new UdpReceiver(chatSocket, 20, routingManager, this, ownIP);
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
                try {
                    handleCommand(input);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                handleMessageInput(input);
            }
        }
    }

    private void handleCommand(String commandInput) throws IOException {
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
                        sendControlPacket(FIN, partnerIp, partnerPort);
                        connectionState = ConnectionState.FIN_WAIT;


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
                    //InetAddress.getLocalHost(),
                    InetAddress.getByName(ownIP),
                    this.chatPort,
                    destIp, destPort,
                    packetType,
                    emptyPayload.serialize().length,
                    checksum
            );

            // Packet bauen & über den RoutingManager verschicken
            Packet packet = new Packet(header, emptyPayload);
            routingManager.sendMessageTo(chatSocket, destIp, destPort - 1, packet);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sendet eine Nachricht an eine Ziel-Adresse (IP:Port).
     */
    private void sendMessage(String destinationAddress, String messageText) {

        try {
            String[] addrParts = destinationAddress.split(":");
            if (addrParts.length != 2) {
                System.out.println("FEHLER: Ungültiges Adressformat. Erwartet: IP:Port");
                return;
            }
            InetAddress destIp = InetAddress.getByName(addrParts[0]);
            int destPort = Integer.parseInt(addrParts[1]) + 1; //HeaderChat

            if (connectionState != ConnectionState.CONNECTED) {
                System.out.println("Keine Verbindung. Fuehre zuerst /chat fuer den Handshake aus.");
                return;
            }



            byte[] msgData = messageText.getBytes(StandardCharsets.US_ASCII);

            int msgId = idCounter++;
            int mtu = 1000;
            int chunkSize = mtu - 10;

            int totalChunks =  (msgData.length > chunkSize ?
                    (int) Math.ceil((msgData.length - chunkSize) / (double) chunkSize) : 0) + 1;

            java.util.List<Packet> packets = new java.util.ArrayList<>();
            int offset = 0;
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunkData;
                    int len = Math.min(chunkSize, msgData.length - offset);
                    chunkData = new byte[len];
                    System.arraycopy(msgData, offset, chunkData, 0, len);
                    offset += len;


                MessagePayload payload = new MessagePayload(msgId, i, totalChunks, new String(chunkData, StandardCharsets.US_ASCII));
                int checksum = calculateChecksum(payload.serialize());
                PacketHeader header = new PacketHeader(
                        //InetAddress.getLocalHost(),
                        InetAddress.getByName(ownIP)
                        , this.chatPort,
                        destIp, destPort,
                        MESSAGE,
                        payload.serialize().length,
                        checksum
                );
                packets.add(new Packet(header, payload));
            }

            ackMap.put(msgId, 0);
            int base = 0;
            int nextSeq = 0;
            long[] lastSend = new long[totalChunks];
            long timeout = 1000;

            while (base < totalChunks) {
                System.out.println("Base: " + base + "\ntotChunk: " + totalChunks);
                while (nextSeq < base + GoBackNConfig.WINDOW_SIZE && nextSeq < totalChunks) {
                    routingManager.sendMessageTo(chatSocket, destIp, destPort - 1, packets.get(nextSeq));
                    lastSend[nextSeq] = System.currentTimeMillis();
                    nextSeq++;
                }

                synchronized (ackMap) {
                    try {
                        long wait = timeout - (System.currentTimeMillis() - lastSend[base]);
                        if (wait > 0 && ackMap.get(msgId) <= base) {
                            ackMap.wait(wait);
                        }
                    } catch (InterruptedException ignored) {}

                    int acked = ackMap.get(msgId);
                    if (acked > base) {
                        base = acked;
                    } else {
                        nextSeq = base;
                    }
                }
            }
            ackMap.remove(msgId);

        } catch (Exception e) {
            System.out.println("FEHLER beim Senden der Datei: " + e.getMessage());
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
            int destPort = Integer.parseInt(addrParts[1]) + 1; //HeaderChat

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

            int fileId = idCounter++;
            int mtu = 1000;
            int firstChunkSize = mtu - 40;
            int otherChunkSize = mtu - 10;

            int totalChunks =  (fileData.length > firstChunkSize ?
                    (int) Math.ceil((fileData.length - firstChunkSize) / (double) otherChunkSize) : 0) + 1;

            java.util.List<Packet> packets = new java.util.ArrayList<>();
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
                        //InetAddress.getLocalHost(),
                        InetAddress.getByName(ownIP)
                        , this.chatPort,
                        destIp, destPort,
                        PacketType.FILE,
                        payload.serialize().length,
                        checksum
                );
                packets.add(new Packet(header, payload));
            }

            ackMap.put(fileId, 0);
            int base = 0;
            int nextSeq = 0;
            long[] lastSend = new long[totalChunks];
            long timeout = 1000;

            while (base < totalChunks) {
                while (nextSeq < base + GoBackNConfig.WINDOW_SIZE && nextSeq < totalChunks) {
                    routingManager.sendMessageTo(chatSocket, destIp, destPort - 1, packets.get(nextSeq));
                    lastSend[nextSeq] = System.currentTimeMillis();
                    nextSeq++;
                }
                synchronized (ackMap) {
                    try {
                        long wait = timeout - (System.currentTimeMillis() - lastSend[base]);
                        if (wait > 0 && ackMap.get(fileId) <= base + GoBackNConfig.WINDOW_SIZE) {
                            System.out.println("Ich warte jetzt");
                            ackMap.wait(wait);
                        }
                    } catch (InterruptedException ignored) {}

                    int acked = ackMap.get(fileId);
                    if (acked > base) {
                        base = acked;
                    } else {
                        nextSeq = base;
                    }
                }
            }
            ackMap.remove(fileId);
            sendControlPacket(FIN, destIp, destPort);
            connectionState = ConnectionState.FIN_WAIT;

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
            partnerPort = Integer.parseInt(parts[1]) + 1; //HeaderChat
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
                //InetAddress.getLocalHost(),
                InetAddress.getByName(ownIP)
                , this.chatPort,
                ip, port,
                type,
                0,
                checksum
        );
        Packet p = new Packet(header, payload);
        routingManager.sendMessageTo(chatSocket, ip, port - 1, p);
    }

    private void sendAckPacket(InetAddress ip, int port, int fileId, int ackNumber) throws IOException {
        AckPayload payload = new AckPayload(fileId, ackNumber);
        int checksum = calculateChecksum(payload.serialize());
        PacketHeader header = new PacketHeader(
                //InetAddress.getLocalHost(),
                InetAddress.getByName(ownIP), this.chatPort,
                ip, port,
                PacketType.DATA_ACK,
                payload.serialize().length,
                checksum
        );
        Packet p = new Packet(header, payload);
        routingManager.sendMessageTo(chatSocket, ip, port - 1, p);
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

    public void onPacketReceived(Packet packet) {
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
                    MessageChunk chunk = new MessageChunk(mp.getMessageId(), mp.getChunkNumber(), mp.getTotalChunks(), mp.getTextBytes());
                    messageChunkManager.addChunk(chunk);
                    int expected = expectedMsgChunkMap.getOrDefault(mp.getMessageId(), 0);
                    if (mp.getChunkNumber() == expected) {
                        expectedMsgChunkMap.put(mp.getMessageId(), expected + 1);
                    }
                    int nextExpected = expectedMsgChunkMap.getOrDefault(mp.getMessageId(), 0);
                    try {
                        sendAckPacket(header.getSourceIp(), header.getSourcePort(), mp.getMessageId(), nextExpected);
                    } catch (IOException e) {
                        // ignore
                    }
                    if (messageChunkManager.isComplete(mp.getMessageId())) {
                        byte[] mdata = messageChunkManager.assembleMessage(mp.getMessageId());
                        System.out.println("Nachricht empfangen: " + new String(mdata, StandardCharsets.US_ASCII));
                    }
                }
                break;
            case FILE:
                if (packet.getPayload() instanceof FilePayload) {
                    FilePayload fp = (FilePayload) packet.getPayload();
                    FileChunk chunk = new FileChunk(fp.getFileId(), fp.getChunkNumber(), fp.getTotalChunks(), fp.getData(),
                            fp.getFileName() != null ? fp.getFileName().getBytes() : new byte[30]);
                    fileChunkManager.addChunk(chunk);
                    int expected = expectedChunkMap.getOrDefault(fp.getFileId(), 0);
                    if (fp.getChunkNumber() == expected) {
                        expectedChunkMap.put(fp.getFileId(), expected + 1);
                    }
                    int nextExpected = expectedChunkMap.getOrDefault(fp.getFileId(), 0);
                    try {
                        sendAckPacket(header.getSourceIp(), header.getSourcePort(), fp.getFileId(), nextExpected);
                    } catch (IOException e) {
                        // ignore
                    }
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
            case DATA_ACK:
                System.out.println("ACK ACK ACK");
                if (packet.getPayload() instanceof AckPayload) {
                    AckPayload ap = (AckPayload) packet.getPayload();
                    System.out.println("AckNummer: " + ap.getAckNumber() + "\nMessageID: " + ap.getId());
                    synchronized (ackMap) {
                        ackMap.put(ap.getId(), ap.getAckNumber());
                        ackMap.notifyAll();
                    }
                }
                break;
            default:
                break;
        }
    }
}