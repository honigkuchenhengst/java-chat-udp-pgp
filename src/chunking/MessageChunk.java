package chunking;

public class MessageChunk {
    private final int messageId;
    private final int chunkNumber;
    private final int totalChunks;
    private final byte[] data;

    public MessageChunk(int messageId, int chunkNumber, int totalChunks, byte[] data) {
        this.messageId = messageId;
        this.chunkNumber = chunkNumber;
        this.totalChunks = totalChunks;
        this.data = data;
    }

    public int getMessageId() { return messageId; }
    public int getChunkNumber() { return chunkNumber; }
    public int getTotalChunks() { return totalChunks; }
    public byte[] getData() { return data; }
}