package routing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class RoutingEntry {

    private InetAddress destinationIP;
    private int destinationPort;
    private InetAddress nextHopIP;
    private int nextHopPort;
    private int hopCount;

    public RoutingEntry(InetAddress destinationIP, int destinationPort,
                        InetAddress nextHopIP, int nextHopPort, int hopCount) {
        this.destinationIP = destinationIP;
        this.destinationPort = destinationPort;
        this.nextHopIP = nextHopIP;
        this.nextHopPort = nextHopPort;
        this.hopCount = hopCount;
    }

    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.put(destinationIP.getAddress()); // 4 Byte
        buffer.putShort((short) destinationPort); // 2 Byte
        buffer.put(nextHopIP.getAddress()); // 4 Byte
        buffer.putShort((short) nextHopPort); // 2 Byte
        buffer.put((byte) hopCount); // 1 Byte
        buffer.put(new byte[3]); // 3 Null-Bytes als Prüfsumme
        return buffer.array();
    }

    public static RoutingEntry deserialize(byte[] data) throws UnknownHostException, IllegalArgumentException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] destIPBytes = new byte[4];
        buffer.get(destIPBytes);
        int destPort = buffer.getShort() & 0xFFFF; //signed zu unsigned, sonst negativer int wert
        byte[] nextIPBytes = new byte[4];
        buffer.get(nextIPBytes);
        int nextPort = buffer.getShort() & 0xFFFF;
        int hopCount = buffer.get() & 0xFF;
        byte[] nullBytes = new byte[3];
        buffer.get(nullBytes);
        for (byte b : nullBytes) {
            if (b != 0) {
                throw new IllegalArgumentException("Corrupted RoutingEntry: null bytes not zero");
            }
        }
        return new RoutingEntry(
                InetAddress.getByAddress(destIPBytes),
                destPort,
                InetAddress.getByAddress(nextIPBytes),
                nextPort,
                hopCount
        );
    }

    @Override
    public String toString() {
        return String.format(
                "Ziel: %s:%d | NextHop: %s:%d | HopCount: %d",
                destinationIP.getHostAddress(), destinationPort,
                nextHopIP.getHostAddress(), nextHopPort,
                hopCount
        );
    }

    public InetAddress getDestinationIP() { return destinationIP; }
    public int getDestinationPort() { return destinationPort; }
    public InetAddress getNextHopIP() { return nextHopIP; }
    public int getNextHopPort() { return nextHopPort; }
    public int getHopCount() { return hopCount; }
    public void setHopCount(int hopCount) { this.hopCount = hopCount; }
}
