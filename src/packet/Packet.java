package packet;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import packet.FilePayload;

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
                payload = FilePayload.deserialize(payloadBytes);
                break;
            case MESSAGE:
                payload = MessagePayload.deserialize(payloadBytes, payloadBytes.length);
                break;
            case SYN:
            case SYN_ACK:
            case ACK:
            case FIN:
            case FIN_ACK:
                payload = EmptyPayload.deserialize(payloadBytes);
                break;
            case DATA_ACK:
                payload = AckPayload.deserialize(payloadBytes);
                break;
            default:
                throw new IllegalArgumentException("Unbekannter PacketType");
        }

        return new Packet(header, payload);
    }



    public Payload getPayload() {
        return payload;
    }
    public PacketHeader getHeader() {
        return header;
    }
}
