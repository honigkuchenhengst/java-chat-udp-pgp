package packet;

import udpSocket.UdpReceiver;
import udpSocket.UdpSender;

import java.net.InetAddress;
import java.util.Scanner;

public class Main {

    public static int calculateChecksum(byte[] payloadBytes) {
        int checksum = 0;
        for (byte b : payloadBytes) {
            checksum += Byte.toUnsignedInt(b);
        }
        return checksum & 0xFFFF; // 16-bit Checksum
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java packet.Main <sender|receiver> [port]");
            return;
        }

        String mode = args[0];

        if (mode.equalsIgnoreCase("receiver")) {
            int port = args.length >= 2 ? Integer.parseInt(args[1]) : 5000;
            //UdpReceiver receiver = new UdpReceiver(port, 2);
            //receiver.start();
        } else if (mode.equalsIgnoreCase("sender")) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Ziel-IP: ");
            String destIpStr = scanner.nextLine();
            System.out.print("Ziel-Port: ");
            int destPort = Integer.parseInt(scanner.nextLine());

            System.out.println("Nachricht (exit zum Beenden):");
            int messageId = 1;
            while (true) {
                String messageText = scanner.nextLine();
                if (messageText.equalsIgnoreCase("exit")) break;

                // 1. Payload bauen
                MessagePayload payload = new MessagePayload(messageId, 1, 1, messageText);
                byte[] payloadBytes = payload.serialize();

                // 2. Checksumme und Länge berechnen
                int checksum = calculateChecksum(payloadBytes);
                int totalLength = PacketHeader.HEADER_SIZE + payloadBytes.length;

                // 3. Header bauen
                InetAddress localIp = InetAddress.getLocalHost();
                int sourcePort = 0; // wird von DatagramSocket gewählt
                PacketHeader header = new PacketHeader(localIp, sourcePort,
                        InetAddress.getByName(destIpStr), destPort,
                        PacketType.MESSAGE, totalLength, checksum);

                // 4. Packet bauen
                Packet packet = new Packet(header, payload);

                // 5. Senden
                UdpSender.sendPacket(packet, destIpStr, destPort);

                messageId++;
            }
        } else {
            System.out.println("Unbekannter Modus: " + mode);
        }
    }
}
