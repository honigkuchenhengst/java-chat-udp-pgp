package packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MessagePayload extends Payload {
    private int messageId;
    private int chunkNumber;
    private int totalChunks;
    private String messageText;

    public MessagePayload(int messageId, int chunkNumber, int totalChunks, String messageText) {
        this.messageId = messageId;
        this.chunkNumber = chunkNumber;
        this.totalChunks = totalChunks;
        this.messageText = messageText;
    }

    public byte[] serialize() {
        byte[] textBytes = messageText.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 4 + textBytes.length);
        //TODO Header durch chunking header ersetzen
        buffer.putShort((short) messageId);
        buffer.putInt(chunkNumber);
        buffer.putInt(totalChunks);
        buffer.put(textBytes);
        return buffer.array();
    }

    public byte[] getTextBytes() {
        return messageText.getBytes(StandardCharsets.US_ASCII);
    }

    public static MessagePayload deserialize(byte[] data, int lengthData) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int messageId = buffer.getShort() & 0xFFFF;
        int chunkNumber = buffer.getInt();
        int totalChunks = buffer.getInt();
        System.out.println(buffer.remaining());
        byte[] textBytes = new byte[lengthData - 10]; //-10 fuer payloadHeader
        buffer.get(textBytes);
        String messageText = new String(textBytes, StandardCharsets.US_ASCII);
        return new MessagePayload(messageId, chunkNumber, totalChunks, messageText);
    }

    public String getMessageText() {
        return messageText;
    }
    public int getMessageId() {
        return messageId;
    }
    public int getChunkNumber() {
        return chunkNumber;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public byte[] getData(){
        return serialize();
    }
    @Override
    public String toString() {
        return "MessagePayload{" +
                "messageId=" + messageId +
                ", chunkNumber=" + chunkNumber +
                ", totalChunks=" + totalChunks +
                ", messageText='" + messageText + '\'' +
                '}';
    }
}
