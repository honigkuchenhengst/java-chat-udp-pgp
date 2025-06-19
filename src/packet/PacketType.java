package packet;

public enum PacketType {
    FILE((byte) 0),
    MESSAGE((byte) 1),
    SYN((byte) 2),
    ACK((byte) 3),
    FIN((byte) 4),
    SYN_ACK((byte) 5),
    FIN_ACK((byte) 6);

    private final byte code;

    PacketType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static PacketType fromCode(byte code) {
        for (PacketType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Unknown packet type code: " + code);
    }
}
