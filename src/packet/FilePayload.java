package packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class FilePayload extends Payload {
    private final int fileId;
    private final int chunkNumber;
    private final int totalChunks;
    private final String fileName; // only filled for first chunk
    private final byte[] data;

    public FilePayload(int fileId, int chunkNumber, int totalChunks, String fileName, byte[] data) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.totalChunks = totalChunks;
        this.fileName = fileName;
        this.data = data;
    }

    public int getFileId() {
        return fileId;
    }

    public int getChunkNumber() {
        return chunkNumber;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }

    @Override
    public byte[] serialize() {
        int extra = (chunkNumber == 0) ? 30 : 0;
        ByteBuffer buffer = ByteBuffer.allocate(10 + extra + data.length);
        buffer.putShort((short) fileId);
        buffer.putInt(chunkNumber);
        buffer.putInt(totalChunks);
        if (chunkNumber == 0) {
            byte[] nameBytes = new byte[30];
            if (fileName != null) {
                byte[] fnBytes = fileName.getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(fnBytes, 0, nameBytes, 0, Math.min(fnBytes.length, 30));
            }
            buffer.put(nameBytes);
        }
        buffer.put(data);
        return buffer.array();
    }

    public static FilePayload deserialize(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int fileId = Short.toUnsignedInt(buffer.getShort());
        int chunkNumber = buffer.getInt();
        int totalChunks = buffer.getInt();
        String fileName = null;
        int remaining = bytes.length - 10;
        if (chunkNumber == 0) {
            byte[] nameBytes = new byte[30];
            buffer.get(nameBytes);
            fileName = new String(nameBytes, StandardCharsets.US_ASCII).trim();
            remaining -= 30;
        }
        byte[] data = new byte[remaining];
        buffer.get(data);
        return new FilePayload(fileId, chunkNumber, totalChunks, fileName, data);
    }
}
