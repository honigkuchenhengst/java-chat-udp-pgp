package udpSocket;

import packet.Packet;

import java.io.IOException;
import java.net.*;

public class UdpSender {

    public static void sendPacket(Packet packet, String destinationIP, int destinationPort){
        byte[] data = packet.serialize();

        InetAddress address = null;
        try {
            address = InetAddress.getByName(destinationIP);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        DatagramPacket udpPacket = new DatagramPacket(data, data.length, address, destinationPort);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(udpPacket);
            //System.out.println("Paket gesendet an " + destinationIP + ":" + destinationPort);
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
