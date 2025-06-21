package udpSocket;

import packet.Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpReceiver {

    private final int port;
    private final ExecutorService executorService;

    public UdpReceiver(int port, int threadPoolSize) {
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    public void start() throws SocketException {
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("UDP Receiver gestartet auf Port " + port);

        // Extra Thread fürs Empfangen
        new Thread(() -> {
            byte[] buffer = new byte[1024]; // Je nach erwarteter max Paketgröße
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // Kopiere die empfangenen Daten (Achtung: DatagramPacket nutzt denselben Puffer immer wieder!)
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
            System.out.println("Empfangen von " + senderIP + ":" + senderPort);
            System.out.println(receivedPacket.getPayload().toString());
            // Hier wäre später deine Logik

        } catch (Exception e) {
            System.out.println("Fehler beim Deserialisieren: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
