package udpSocket;

import packet.MessagePayload;
import packet.Packet;
import packet.PacketHeader;
import routing.RoutingManager;

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

    public UdpReceiver(DatagramSocket socket, int threadPoolSize, RoutingManager routingManager) {
        this.socket = socket;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.routingManager = routingManager;
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

            InetAddress localAddress = socket.getLocalAddress();
            int localPort = socket.getLocalPort() - 1;

            boolean isForMe =
                    header.getDestIp().equals(localAddress) &&
                            header.getDestPort() == localPort;

            if (isForMe) {
                // Handle Packet
                if (receivedPacket.getPayload() instanceof MessagePayload) {
                    MessagePayload mp = (MessagePayload) receivedPacket.getPayload();
                    System.out.println("Nachricht empfangen: " + mp.getMessageText());
                } else {
                    System.out.println("Empfangenes Payload: " + receivedPacket.getPayload().toString());
                }
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
