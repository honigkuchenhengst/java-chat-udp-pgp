package packet;

import java.nio.ByteBuffer;

public class AckPayload extends Payload {
    private final int fileId;
    private final int ackNumber;

    public AckPayload(int fileId, int ackNumber) {
        this.fileId = fileId;
        this.ackNumber = ackNumber;
    }

    public int getFileId() {
        return fileId;
    }

    public int getAckNumber() {
        return ackNumber;
    }

    @Override
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        buffer.putShort((short) fileId);
        buffer.putInt(ackNumber);
        return buffer.array();
    }

    public static AckPayload deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int fileId = Short.toUnsignedInt(buffer.getShort());
        int ackNumber = buffer.getInt();
        return new AckPayload(fileId, ackNumber);
    }
}
