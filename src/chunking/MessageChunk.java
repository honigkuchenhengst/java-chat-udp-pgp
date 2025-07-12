package chunking;

import java.nio.ByteBuffer;

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

    public byte[] getChunk(){
        byte[] chunk = new byte[data.length + 10];
        byte[] messageIdBytes = new byte[2];
        messageIdBytes[0] = (byte) (messageId >> 8);
        messageIdBytes[1] = (byte) (messageId);
        byte[] chunkNumberBytes = ByteBuffer.allocate(4).putInt(chunkNumber).array();
        byte[] totalChunksBytes = ByteBuffer.allocate(4).putInt(totalChunks).array();
        System.arraycopy(messageIdBytes, 0, chunk, 0, messageIdBytes.length);
        System.arraycopy(chunkNumberBytes, 0, chunk, 2, chunkNumberBytes.length);
        System.arraycopy(totalChunksBytes, 0, chunk, 6, totalChunksBytes.length);
        System.arraycopy(data, 0, chunk, 10, data.length);
        return chunk;
    }

    public static MessageChunk parseChunk(byte[] chunk){
        int messageId;
        byte[] chunkNumberBytes = new byte[4];
        byte[] totalChunksBytes = new byte[4];
        byte[] data = new byte[chunk.length - 10];
        messageId = ((chunk[0] & 0xFF) << 8) | ((chunk[1] & 0xFF));
        System.arraycopy(chunk, 2, chunkNumberBytes, 0, chunkNumberBytes.length);
        System.arraycopy(chunk, 6, totalChunksBytes, 0, totalChunksBytes.length);
        System.arraycopy(chunk, 10, data, 0, data.length);
        return new MessageChunk(messageId,
                ByteBuffer.wrap(chunkNumberBytes).getInt(),
                ByteBuffer.wrap(totalChunksBytes).getInt(),
                data);
    }
}