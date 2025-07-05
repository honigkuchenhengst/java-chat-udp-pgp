package packet;
import routing.RoutingManager;
import udpSocket.UdpSender;
import udpSocket.UdpReceiver;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

public class ChatApp extends Thread{
    private RoutingManager routingManager;
    private DatagramSocket chatSocket;
    private int chatPort;

    public ChatApp(RoutingManager routingManager, int chatPort) throws SocketException {
        this.routingManager = routingManager;
        this.chatSocket = new DatagramSocket(chatPort);
        this.chatPort = chatPort;
    }

    public void run(){
        Scanner scanner = new Scanner(System.in);

        // 1. Nutzer-Infos eingeben
        System.out.print("Dein Name: ");
        String username = scanner.nextLine();

//        System.out.print("Deine Portnummer (zum Empfangen): ");
//        int localPort = Integer.parseInt(scanner.nextLine());

        System.out.print("Ziel-IP: ");
        String destIp = scanner.nextLine();

        System.out.print("Ziel-Port (Routing): ");
        int destPort = Integer.parseInt(scanner.nextLine());

        // 2. Starte Receiver in eigenem Thread
        UdpReceiver receiver = new UdpReceiver(chatSocket, 4);
        receiver.start();

        // 3. Eingabe-Schleife für Senden
        int messageId = 0;

        while (true) {
            String messageText = scanner.nextLine();

            if (messageText.trim().isEmpty()) continue;

            // Payload bauen
            MessagePayload payload = new MessagePayload(
                    messageId++, 1, 1, "[" + username + "]: " + messageText
            );

            // Checksumme berechnen
            int checksum = calculateChecksum(payload.serialize());

            // Header bauen
            PacketHeader header = null;
            try {
                header = new PacketHeader(
                        InetAddress.getLocalHost(), chatPort,
                        InetAddress.getByName(destIp), destPort,
                        PacketType.MESSAGE,
                        payload.serialize().length,
                        checksum
                );
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

            // Packet bauen & verschicken
            Packet packet = new Packet(header, payload);
            try {
                routingManager.sendMessageTo(chatSocket, InetAddress.getByName(destIp), destPort, packet);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }
    // Einfache Checksumme als Summe der Bytes
    private static int calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum & 0xFFFF; // passt in 2 Byte
    }
}
