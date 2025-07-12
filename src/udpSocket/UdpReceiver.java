package udpSocket;

import packet.ChatApp;
import packet.MessagePayload;

import packet.Packet;
import packet.PacketHeader;
import packet.ChatApp;
import routing.RoutingManager;
import java.util.zip.CRC32;
import java.util.Arrays;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpReceiver {

    private final DatagramSocket socket;
    private final ExecutorService executorService;
    private final RoutingManager routingManager;
    private final ChatApp chatApp;

    public UdpReceiver(DatagramSocket socket, int threadPoolSize, RoutingManager routingManager, ChatApp chatApp) {
        this.socket = socket;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.routingManager = routingManager;
        this.chatApp = chatApp;
    }

    public void start() {
        System.out.println("UDP Receiver gestartet auf Port " + socket.getLocalPort());

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                    executorService.submit(() -> handlePacket(data, packet.getAddress().getHostAddress(), packet.getPort()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void handlePacket(byte[] data, String senderIP, int senderPort) {
        try {
            Packet receivedPacket = Packet.deserialize(data);
            PacketHeader header = receivedPacket.getHeader();

            // checksum check over payload
            byte[] payloadBytes = Arrays.copyOfRange(data, PacketHeader.HEADER_SIZE, data.length);
            CRC32 crc32 = new CRC32();
            crc32.update(payloadBytes);
            int computedChecksum = (int) crc32.getValue();

            if (computedChecksum != header.getChecksum()) {
                System.out.println("Checksum mismatch: expected " + header.getChecksum() +
                        " but got " + computedChecksum + ". Dropping packet.");
                return; // ignore packet when checksum does not match
            } else {
                System.out.println("Checksum OK for packet from " + senderIP + ":" + senderPort);
            }

            InetAddress localAddress = InetAddress.getByName("192.168.56.1");
            int localPort = socket.getLocalPort();

            boolean isForMe =
                    header.getDestIp().equals(localAddress) &&
                            header.getDestPort() + 1 == localPort;

            if (isForMe) {

                chatApp.onPacketReceived(receivedPacket);

            } else {
                // Weiterleiten
                System.out.println("Paket nicht für mich – Weiterleitung an " +
                        header.getDestIp().getHostAddress() + ":" + header.getDestPort());
                routingManager.forwardPacket(socket, receivedPacket);
            }

        } catch (Exception e) {
            System.out.println("Fehler beim Deserialisieren oder Weiterleiten: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
