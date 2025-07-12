package chunking;

import packet.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Chunker {
    private static final int MAX_CHUNK_SIZE = 10; // zum Testen klein halten

    public static List<MessageChunk> splitIntoChunks(int messageId, byte[] payload) {
        List<MessageChunk> chunks = new java.util.ArrayList<>();

        int totalChunks = (int) Math.ceil((double) payload.length / MAX_CHUNK_SIZE);

        for (int i = 0; i < totalChunks; i++) {
            int start = i * MAX_CHUNK_SIZE;
            int end = Math.min(start + MAX_CHUNK_SIZE, payload.length);
            byte[] chunkData = new byte[end - start];
            System.arraycopy(payload, start, chunkData, 0, end - start);

            chunks.add(new MessageChunk(messageId, i, totalChunks, chunkData));
        }

        return chunks;
    }

    public static void main(String[] args) throws UnknownHostException {
        InetAddress sourceIP = InetAddress.getByName("192.168.0.1");
        int sourcePort = 1234;
        InetAddress destIP = InetAddress.getByName("192.168.0.2");
        int destPort = 5678;
        PacketType type = PacketType.MESSAGE;
        int length = 10;  // nur Beispiel
        int checksum = 0;

        PacketHeader header = new PacketHeader(sourceIP, sourcePort, destIP, destPort, type, length, checksum);
        MessagePayload payload = new MessagePayload(42, 1, 5, "Hallo, Moin, ich bins der Glookers");
        Packet packet = new Packet(header, payload);
        byte[] packetSer = packet.serialize();
        // --- Teste nun das Chunking ---
        int messageId = 1;
        List<MessageChunk> chunks = splitIntoChunks(messageId, packetSer);
        for(MessageChunk chunk : chunks) {
            String data = new String(chunk.getData(), StandardCharsets.UTF_8);
            System.out.println(data);
        }
        // Simuliere Empfang und Zusammenbau
        ChunkManager manager = new ChunkManager(2);
        for (MessageChunk chunk : chunks) {
            manager.addChunk(chunk);
        }

        if (manager.isComplete(messageId)) {
            byte[] reassembled = manager.assembleMessage(messageId);

            // Versuche das Paket wiederherzustellen
            try {
                Packet reconstructedPacket = Packet.deserialize(reassembled);
                System.out.println("Rekonstruierte Nachricht:");
                System.out.println(reconstructedPacket.getPayload().toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
