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
        byte[] textBytes = messageText.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 4 + 2 + textBytes.length);
        buffer.putShort((short) messageId);
        buffer.putInt(chunkNumber);
        buffer.putInt(totalChunks);
        buffer.putShort((short) textBytes.length);
        buffer.put(textBytes);
        return buffer.array();
    }

    public static MessagePayload deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int messageId = buffer.getShort() & 0xFFFF;
        int chunkNumber = buffer.getInt();
        int totalChunks = buffer.getInt();
        int textLength = buffer.getShort() & 0xFFFF;
        byte[] textBytes = new byte[textLength];
        buffer.get(textBytes);
        String messageText = new String(textBytes, StandardCharsets.UTF_8);
        return new MessagePayload(messageId, chunkNumber, totalChunks, messageText);
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
