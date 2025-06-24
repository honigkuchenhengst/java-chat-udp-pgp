package packet;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;


public class PacketHeader {
    private InetAddress sourceIp;
    private int sourcePort;
    private InetAddress destIp;
    private int destPort;
    private PacketType type;
    private int packetLength;
    private int checksum;

    public static final int HEADER_SIZE = 17;

    public PacketHeader(InetAddress sourceIp, int sourcePort,
                        InetAddress destIp, int destPort,
                        PacketType type, int packetLength, int checksum) {
        this.sourceIp = sourceIp;
        this.sourcePort = sourcePort;
        this.destIp = destIp;
        this.destPort = destPort;
        this.type = type;
        this.packetLength = packetLength;
        this.checksum = checksum;
    }

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
        buffer.put(sourceIp.getAddress());              // 4 Bytes
        buffer.putShort((short) sourcePort);            // 2 Bytes
        buffer.put(destIp.getAddress());                // 4 Bytes
        buffer.putShort((short) destPort);              // 2 Bytes
        buffer.put(type.getCode());                     // 1 Byte
        buffer.putShort((short) packetLength);          // 2 Bytes
        buffer.putShort((short) checksum);              // 2 Bytes
        return buffer.array();
    }

    public static PacketHeader deserialize(byte[] data) throws Exception {
        if (data.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Header too short: " + data.length);
        }
        if (data.length > HEADER_SIZE) {
            throw new IllegalArgumentException("Header too long: " + data.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] sourceIpBytes = new byte[4];
        buffer.get(sourceIpBytes);
        InetAddress sourceIp = InetAddress.getByAddress(sourceIpBytes);

        int sourcePort = Short.toUnsignedInt(buffer.getShort());

        byte[] destIpBytes = new byte[4];
        buffer.get(destIpBytes);
        InetAddress destIp = InetAddress.getByAddress(destIpBytes);

        int destPort = Short.toUnsignedInt(buffer.getShort());

        byte typeCode = buffer.get();
        PacketType type = PacketType.fromCode(typeCode);

        int packetLength = Short.toUnsignedInt(buffer.getShort());
        int checksum = Short.toUnsignedInt(buffer.getShort());

        return new PacketHeader(sourceIp, sourcePort, destIp, destPort, type, packetLength, checksum);
    }
    @Override
    public String toString() {
        return "PacketHeader{" +
                "sourceIP=" + sourceIp.getHostAddress() +
                ", sourcePort=" + sourcePort +
                ", destIP=" + destIp.getHostAddress() +
                ", destPort=" + destPort +
                ", type=" + type +
                ", length=" + packetLength +
                ", checksum=" + checksum +
                '}';
    }


    // Getter + toString() falls Logs brauchen

    public static void main(String[] args) throws Exception {
        try {
            // Erstelle Beispiel PacketHeader
            InetAddress sourceIP = InetAddress.getByName("192.168.0.1");
            int sourcePort = 5000;
            InetAddress destIP = InetAddress.getByName("192.168.0.2");
            int destPort = 6000;
            PacketType type = PacketType.MESSAGE;
            int length = 123;
            int checksum = 456;

            PacketHeader originalHeader = new PacketHeader(sourceIP, sourcePort, destIP, destPort, type, length, checksum);

            // Serialisieren
            byte[] data = originalHeader.serialize();

            // Deserialisieren
            PacketHeader deserializedHeader = PacketHeader.deserialize(data);

            // Ausgabe zum Vergleich
            System.out.println("Original Header:");
            System.out.println(originalHeader);
            System.out.println("Serialzed Header:");
            System.out.println(Arrays.toString(data));
            System.out.println("Deserialized Header:");
            System.out.println(deserializedHeader);

            // Einfacher Vergleich
            if (originalHeader.toString().equals(deserializedHeader.toString())) {
                System.out.println("TEST SUCCESS: Header wurde korrekt serialisiert/deserialisiert.");
            } else {
                System.out.println("TEST FAILURE: Unterschied beim Header.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public PacketType getType() {
        return type;
    }

    public InetAddress getDestIp() {
        return destIp;
    }

    public InetAddress getSourceIp() {
        return sourceIp;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public int getDestPort() {
        return destPort;
    }

    public int getPacketLength() {
        return packetLength;
    }

    public int getChecksum() {
        return checksum;
    }
}
