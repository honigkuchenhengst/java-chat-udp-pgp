package udpSocket;

import packet.Packet;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpSender {

    public static void sendPacket(Packet packet, String destinationIP, int destinationPort) throws IOException {
        byte[] data = packet.serialize();

        InetAddress address = InetAddress.getByName(destinationIP);
        DatagramPacket udpPacket = new DatagramPacket(data, data.length, address, destinationPort);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(udpPacket);
            //System.out.println("Paket gesendet an " + destinationIP + ":" + destinationPort);
        }
    }
}
