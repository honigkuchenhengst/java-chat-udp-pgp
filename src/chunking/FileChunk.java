package chunking;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class FileChunk {
    private final int fileId;
    private final int chunkNumber;
    private final int totalChunks;
    private final byte[] data;
    private final byte[] fileName;

    public FileChunk(int fileId, int chunkNumber, int totalChunks, byte[] data, byte[] fileName) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.totalChunks = totalChunks;
        this.data = data;
        this.fileName = fileName;
    }

    public int getFileId() { return fileId; }
    public int getChunkNumber() { return chunkNumber; }
    public int getTotalChunks() { return totalChunks; }
    public byte[] getFileName() { return fileName; }
    public byte[] getData() { return data; }

    public byte[] getChunk(){
        byte[] chunk = new byte[data.length + 40];
        byte[] fileIdBytes = new byte[2];
        fileIdBytes[0] = (byte) (fileId >> 8);
        fileIdBytes[1] = (byte) (fileId);
        byte[] chunkNumberBytes = ByteBuffer.allocate(4).putInt(chunkNumber).array();
        byte[] totalChunksBytes = ByteBuffer.allocate(4).putInt(totalChunks).array();
        System.arraycopy(fileIdBytes, 0, chunk, 0, fileIdBytes.length);
        System.arraycopy(chunkNumberBytes, 0, chunk, 2, chunkNumberBytes.length);
        System.arraycopy(totalChunksBytes, 0, chunk, 6, totalChunksBytes.length);
        if(chunkNumber == 0){
            System.arraycopy(fileName,0,chunk,10,30);
            System.arraycopy(data, 0, chunk, 40, data.length);
        } else {
            System.arraycopy(data, 0, chunk, 10, data.length);
        }
        return chunk;
    }

    public static FileChunk parseChunk(byte[] chunk){
        int fileId;
        byte[] chunkNumberBytes = new byte[4];
        byte[] totalChunksBytes = new byte[4];
        byte[] fileNameBytes = new byte[30];
        byte[] data;
        fileId = ((chunk[0] & 0xFF) << 8) | ((chunk[1] & 0xFF));
        System.arraycopy(chunk, 2, chunkNumberBytes, 0, chunkNumberBytes.length);
        System.arraycopy(chunk, 6, totalChunksBytes, 0, totalChunksBytes.length);
        if(ByteBuffer.wrap(chunkNumberBytes).getInt() == 0){
            data = new byte[chunk.length - 40];
            System.arraycopy(chunk, 10, fileNameBytes, 0, 30);
            System.arraycopy(chunk, 40, data, 0, data.length);
        } else {
            data = new byte[chunk.length - 10];
            System.arraycopy(chunk, 10, data, 0, data.length);
        }
        return new FileChunk(fileId,
                ByteBuffer.wrap(chunkNumberBytes).getInt(),
                ByteBuffer.wrap(totalChunksBytes).getInt(),
                data, fileNameBytes);
    }
}
