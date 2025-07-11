package udpSocket;

import packet.*;

import java.net.InetAddress;

public class UdpTestMain {
    public static void main(String[] args) throws Exception {
        // Starte den Receiver
        //UdpReceiver receiver = new UdpReceiver(12345, 4);
        //receiver.start();

        // Warte kurz, damit Receiver ready ist
        Thread.sleep(1000);

        // Baue ein Test-Paket
        PacketHeader header = new PacketHeader(
                InetAddress.getByName("127.0.0.1"),
                1234,
                InetAddress.getByName("127.0.0.1"),
                12345,
                PacketType.MESSAGE,
                0,  // length ignorieren wir vorerst
                0
        );

        MessagePayload payload = new MessagePayload(
                1, 0, 1, "Hallo EchoServer"
        );

        Packet packet = new Packet(header, payload);

        // Sende an den eigenen Server
        UdpSender.sendPacket(packet, "127.0.0.1", 12345);
    }
}
