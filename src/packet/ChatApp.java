package packet;

import routing.RoutingManager;
import udpSocket.UdpReceiver;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.zip.CRC32;

public class ChatApp extends Thread {
    private final RoutingManager routingManager;
    private final DatagramSocket chatSocket;
    private final int chatPort;
    private String activeChatPartnerAddress; // Speichert die IP:Port des Chatpartners als String
    private int messageIdCounter = 0;
    private String ownAddress; // Die eigene Adresse für die Anzeige in Nachrichten

    public ChatApp(RoutingManager routingManager, int chatPort) throws SocketException, UnknownHostException {
        this.routingManager = routingManager;
        this.chatSocket = new DatagramSocket(chatPort);
        this.chatPort = chatPort;
        this.activeChatPartnerAddress = null;
        // Speichere die eigene Adresse für die Nachrichten-Signatur
        this.ownAddress = InetAddress.getLocalHost().getHostAddress() + ":" + this.chatPort;
    }

    @Override
    public void run() {
        // Starte Receiver...
        UdpReceiver receiver = new UdpReceiver(chatSocket, 4, routingManager);
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
                    activeChatPartnerAddress = parts[1];
                    System.out.println("Du sprichst jetzt mit '" + activeChatPartnerAddress + "'.");
                }
                break;
            case "/msg":
                if (parts.length < 3) {
                    System.out.println("FEHLER: Verwendung: /msg <IP:Port> <Nachricht>");
                } else {
                    sendMessage(parts[1], parts[2]);
                }
                break;
            case "/list":
                routingManager.printKnownNodes(); // Ruft die neue, einfache Methode auf
                break;
            case "/quit":
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
            sendMessage(activeChatPartnerAddress, messageText);
        } else {
            System.out.println("Kein aktiver Chatpartner. Nutze '/chat <IP:Port>' oder '/msg <IP:Port> <Nachricht>'.");
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

    private void printHelp() {
        System.out.println("--- Verfügbare Befehle ---");
        System.out.println("/chat <IP:Port>    - Startet einen interaktiven Chat.");
        System.out.println("/msg <IP:Port> <text> - Sendet eine einzelne Nachricht.");
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
}