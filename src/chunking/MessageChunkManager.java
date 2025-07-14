package chunking;

import java.util.HashMap;
import java.util.Map;

public class MessageChunkManager {
    private final Map<Integer, Map<Integer, byte[]>> chunkStorage = new HashMap<>();
    private final Map<Integer, Integer> expectedChunks = new HashMap<>();

    public void addChunk(MessageChunk chunk) {
        int id = chunk.getMessageId();
        chunkStorage.putIfAbsent(id, new HashMap<>());
        chunkStorage.get(id).put(chunk.getChunkNumber(), chunk.getData());
        expectedChunks.putIfAbsent(id, chunk.getTotalChunks());
    }

    public boolean isComplete(int id) {
        if (!expectedChunks.containsKey(id)) return false;
        return chunkStorage.get(id).size() == expectedChunks.get(id);
    }

    public byte[] assembleMessage(int id) {
        if (!isComplete(id)) {
            throw new IllegalStateException("Message not complete yet");
        }
        Map<Integer, byte[]> chunks = chunkStorage.get(id);
        int totalSize = chunks.values().stream().mapToInt(b -> b.length).sum();
        byte[] data = new byte[totalSize];
        int offset = 0;
        for (int i = 0; i < expectedChunks.get(id); i++) {
            byte[] part = chunks.get(i);
            System.arraycopy(part, 0, data, offset, part.length);
            offset += part.length;
        }
        return data;
    }

}
