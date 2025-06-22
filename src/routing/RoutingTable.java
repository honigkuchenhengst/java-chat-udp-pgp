package routing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class RoutingTable {

    private List<RoutingEntry> entries;

    public RoutingTable() {
        this.entries = new ArrayList<>();
    }

    public void addEntry(RoutingEntry entry) {
        entries.add(entry);
    }

    public List<RoutingEntry> getEntries() {
        return entries;
    }

    // Serialisiert den Header + RoutingTable
    public byte[] serializeWithHeader(InetAddress sourceIP, int sourcePort, InetAddress destIP, int destPort) {
        int tableLength = entries.size() * 16;
        ByteBuffer buffer = ByteBuffer.allocate(14 + tableLength);

        buffer.put(sourceIP.getAddress());
        buffer.putShort((short) sourcePort);
        buffer.put(destIP.getAddress());
        buffer.putShort((short) destPort);
        buffer.putShort((short) tableLength);

        for (RoutingEntry entry : entries) {
            buffer.put(entry.serialize());
        }

        return buffer.array();
    }

    // Deserialisiert kompletten Frame (Header + Tabelle)
    public static DeserializedRoutingTable deserializeWithHeader(byte[] data) throws UnknownHostException {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        byte[] srcIPBytes = new byte[4];
        buffer.get(srcIPBytes);
        int srcPort = buffer.getShort() & 0xFFFF;
        byte[] destIPBytes = new byte[4];
        buffer.get(destIPBytes);
        int destPort = buffer.getShort() & 0xFFFF;
        int tableLength = buffer.getShort() & 0xFFFF;

        RoutingTable table = new RoutingTable();
        for (int i = 0; i < tableLength; i += 16) {
            byte[] entryData = new byte[16];
            buffer.get(entryData);
            RoutingEntry entry = RoutingEntry.deserialize(entryData);
            table.addEntry(entry);
        }

        return new DeserializedRoutingTable(
                InetAddress.getByAddress(srcIPBytes), srcPort,
                InetAddress.getByAddress(destIPBytes), destPort,
                table
        );
    }

    // Hilfsklasse für Rückgabe des Headers + Tabelle
    public static class DeserializedRoutingTable {
        public InetAddress sourceIP;
        public int sourcePort;
        public InetAddress destIP;
        public int destPort;
        public RoutingTable table;

        public DeserializedRoutingTable(InetAddress sourceIP, int sourcePort, InetAddress destIP, int destPort, RoutingTable table) {
            this.sourceIP = sourceIP;
            this.sourcePort = sourcePort;
            this.destIP = destIP;
            this.destPort = destPort;
            this.table = table;
        }
    }
}
