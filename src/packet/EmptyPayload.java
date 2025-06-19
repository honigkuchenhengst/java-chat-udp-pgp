package packet;

public class EmptyPayload extends Payload {
    @Override
    public byte[] serialize() {
        return new byte[0]; // Keine Daten
    }

    public static EmptyPayload deserialize(byte[] data) {
        return new EmptyPayload();
    }
}
