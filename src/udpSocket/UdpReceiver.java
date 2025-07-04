package udpSocket;

import packet.MessagePayload;
import packet.Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpReceiver {

    private final DatagramSocket socket;
    private final ExecutorService executorService;

    public UdpReceiver(DatagramSocket socket, int threadPoolSize) {
        this.socket = socket;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
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
            //System.out.println("Empfangen von " + senderIP + ":" + senderPort);
            //System.out.println(receivedPacket.getPayload().toString());
            if (receivedPacket.getPayload() instanceof MessagePayload) {
                MessagePayload mp = (MessagePayload) receivedPacket.getPayload();
                System.out.println(mp.getMessageText());
            } else {
                System.out.println("Empfangenes Payload: " + receivedPacket.getPayload().toString());
            }


        } catch (Exception e) {
            System.out.println("Fehler beim Deserialisieren: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
