/**
 * Created by elkhan on 17/06/17.
 */
public enum PacketType {
    DATA    (0),
    ACK     (1),
    EOT     (2);


    private final int packetTypeCode;

    PacketType(int packetTypeCode) {
        this.packetTypeCode = packetTypeCode;
    }

    public int getPacketTypeCode() {
        return this.packetTypeCode;
    }

    public static PacketType getPacketType(int packetTypeCode) {
        switch (packetTypeCode){
            case 0:
                return DATA;
            case 1:
                return ACK;
            case 2:
                return EOT;
            default:
                return DATA;
        }
    }
}
