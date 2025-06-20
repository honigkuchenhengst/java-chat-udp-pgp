package chunking;

import java.util.HashMap;
import java.util.Map;

public class ChunkManager {
    private final Map<Integer, Map<Integer, byte[]>> chunkStorage = new HashMap<>();
    private final Map<Integer, Integer> expectedChunks = new HashMap<>();

    public void addChunk(MessageChunk chunk) {
        int messageId = chunk.getMessageId();

        chunkStorage.putIfAbsent(messageId, new HashMap<>());
        chunkStorage.get(messageId).put(chunk.getChunkNumber(), chunk.getData());
        expectedChunks.putIfAbsent(messageId, chunk.getTotalChunks());
    }

    public boolean isComplete(int messageId) {
        if (!expectedChunks.containsKey(messageId)) {
            return false;
        }
        return chunkStorage.get(messageId).size() == expectedChunks.get(messageId);
    }

    public byte[] assembleMessage(int messageId) {
        if (!isComplete(messageId)) {
            throw new IllegalStateException("Message not complete yet");
        }

        Map<Integer, byte[]> chunks = chunkStorage.get(messageId);
        int totalSize = chunks.values().stream().mapToInt(b -> b.length).sum();
        byte[] fullMessage = new byte[totalSize];

        int offset = 0;
        for (int i = 0; i < expectedChunks.get(messageId); i++) {
            byte[] part = chunks.get(i);
            System.arraycopy(part, 0, fullMessage, offset, part.length);
            offset += part.length;
        }

        return fullMessage;
    }
}