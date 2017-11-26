/**
 * Created by elkhan on 19/06/17.
 */
public class LogUtils {
    void sendPacket(Packet packet) {
        String packetType = getPacketType(packet);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("PKT SEND ");
        stringBuilder.append(packetType);
        stringBuilder.append(" ");
        stringBuilder.append(packet.getPacketLength());
        stringBuilder.append(" ");
        stringBuilder.append(packet.getSequenceNumber() % Packet.maxSequenceNumber);

        System.out.println(stringBuilder.toString());
    }

    void receivePacket(Packet packet) {
        String packetType = getPacketType(packet);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("PKT RECV ");
        stringBuilder.append(packetType);
        stringBuilder.append(" ");
        stringBuilder.append(packet.getPacketLength());
        stringBuilder.append(" ");
        stringBuilder.append(packet.getSequenceNumber() % Packet.maxSequenceNumber);

        System.out.println(stringBuilder.toString());
    }

    private String getPacketType(Packet packet) {
        String packetType = null;
        switch (packet.getPacketType()) {
            case ACK:
                packetType = "ACK";
                break;
            case DATA:
                packetType = "DATA";
                break;
            case EOT:
                packetType = "EOT";
                break;
            default:
                break;
        }
        return packetType;
    }
}
