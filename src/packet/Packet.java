package packet;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Packet {
    private PacketHeader header;
    private Payload payload;

    public Packet(PacketHeader header, Payload payload) {
        this.header = header;
        this.payload = payload;
    }

    public byte[] serialize() {
        byte[] headerBytes = header.serialize();
        byte[] payloadBytes = payload.serialize();

        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + payloadBytes.length);
        buffer.put(headerBytes);
        buffer.put(payloadBytes);
        return buffer.array();
    }

    public static Packet deserialize(byte[] data) throws Exception {
        byte[] headerBytes = Arrays.copyOfRange(data, 0, PacketHeader.HEADER_SIZE);
        PacketHeader header = PacketHeader.deserialize(headerBytes);

        byte[] payloadBytes = Arrays.copyOfRange(data, PacketHeader.HEADER_SIZE, data.length);
        Payload payload;

        switch (header.getType()) {
            case FILE:
            case MESSAGE:
                payload = MessagePayload.deserialize(payloadBytes);
                break;
            case SYN:
            case SYN_ACK:
            case ACK:
            case FIN:
            case FIN_ACK:
                payload = EmptyPayload.deserialize(payloadBytes);
                break;
            default:
                throw new IllegalArgumentException("Unbekannter PacketType");
        }

        return new Packet(header, payload);
    }

    // Getter, Setter...
    public static void main(String[] args) {
        try {
            // Dummy Daten erzeugen
            InetAddress sourceIP = InetAddress.getByName("192.168.0.1");
            int sourcePort = 1234;
            InetAddress destIP = InetAddress.getByName("192.168.0.2");
            int destPort = 5678;
            PacketType type = PacketType.MESSAGE;
            int length = 10;  // nur Beispiel
            int checksum = 0;

            PacketHeader header = new PacketHeader(sourceIP, sourcePort, destIP, destPort, type, length, checksum);
            MessagePayload payload = new MessagePayload(42, 1, 5, "Hallo, Moin, ich bins der Glookerß");

            Packet packet = new Packet(header, payload);

            // Serialisieren
            byte[] data = packet.serialize();

            System.out.println("Serialisiert: " + data.length + " Bytes");

            // Deserialisieren
            Packet deserializedPacket = Packet.deserialize(data);

            System.out.println("Deserialisiert:");
            System.out.println("Header: " + deserializedPacket.header);
            System.out.println("Payload: " + deserializedPacket.payload);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Payload getPayload() {
        return payload;
    }
}
