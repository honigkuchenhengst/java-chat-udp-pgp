package chunking;

import java.util.HashMap;
import java.util.Map;

public class FileChunkManager {
    private final Map<Integer, Map<Integer, byte[]>> chunkStorage = new HashMap<>();
    private final Map<Integer, Integer> expectedChunks = new HashMap<>();
    private final Map<Integer, String> fileNames = new HashMap<>();

    public void addChunk(FileChunk chunk) {
        int id = chunk.getFileId();
        chunkStorage.putIfAbsent(id, new HashMap<>());
        chunkStorage.get(id).put(chunk.getChunkNumber(), chunk.getData());
        expectedChunks.putIfAbsent(id, chunk.getTotalChunks());
        if (chunk.getChunkNumber() == 0) {
            fileNames.put(id, new String(chunk.getFileName()).trim());
        }
    }

    public boolean isComplete(int id) {
        if (!expectedChunks.containsKey(id)) return false;
        return chunkStorage.get(id).size() == expectedChunks.get(id);
    }

    public byte[] assembleFile(int id) {
        if (!isComplete(id)) {
            throw new IllegalStateException("File not complete yet");
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

    public String getFileName(int id) {
        return fileNames.get(id);
    }
}
