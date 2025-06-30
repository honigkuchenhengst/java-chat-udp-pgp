package packet;
import udpSocket.UdpSender;
import udpSocket.UdpReceiver;

import java.net.InetAddress;
import java.util.Scanner;

public class ChatApp {

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        // 1. Nutzer-Infos eingeben
        System.out.print("Dein Name: ");
        String username = scanner.nextLine();

        System.out.print("Deine Portnummer (zum Empfangen): ");
        int localPort = Integer.parseInt(scanner.nextLine());

        System.out.print("Ziel-IP: ");
        String destIp = scanner.nextLine();

        System.out.print("Ziel-Port: ");
        int destPort = Integer.parseInt(scanner.nextLine());

        // 2. Starte Receiver in eigenem Thread
        UdpReceiver receiver = new UdpReceiver(localPort, 1);
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
            PacketHeader header = new PacketHeader(
                    InetAddress.getLocalHost(), localPort,
                    InetAddress.getByName(destIp), destPort,
                    PacketType.MESSAGE,
                    payload.serialize().length,
                    checksum
            );

            // Packet bauen & verschicken
            Packet packet = new Packet(header, payload);
            UdpSender.sendPacket(packet, destIp, destPort);
        }
    }

    // Einfache Checksumme als Summe der Bytes (kannst du ersetzen)
    private static int calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum & 0xFFFF; // passt in 2 Byte
    }
}
