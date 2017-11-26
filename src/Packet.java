import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Timer;

/**
 * Created by elkhan on 17/06/17.
 */
public class Packet {
    public Packet(PacketType packetType, int payloadSize, int sequenceNumber, byte[] data) {
        this.data = data;
        this.packetType = packetType;
        this.packetLength = payloadSize + headerLength;
        this.sequenceNumber = sequenceNumber;
    }

    private Timer packetTimer;
    private long timestamp;
    private PacketType packetType;
    private int packetLength;
    private boolean isACKed = false;
    private int sequenceNumber;
    public static final int maxSequenceNumber = 256;
    public static final int headerLength = 12;
    public static final int payloadSize = 500;
    private byte[] data;

    /*
     * This method constructs a packet which will be sent.
     */
    public byte[] createPacket() {
        byte[] packet = new byte[packetLength];
        System.arraycopy(intToBytes(packetType.getPacketTypeCode()), 0, packet, 0, Integer.BYTES);
        System.arraycopy(intToBytes(packetLength), 0, packet, Integer.BYTES, Integer.BYTES);
        System.arraycopy(intToBytes(sequenceNumber % maxSequenceNumber), 0, packet, 2 * Integer.BYTES, Integer.BYTES);
        System.arraycopy(data, 0, packet, 3 * Integer.BYTES, packetLength - headerLength);
        return packet;
    }

    public static Packet reassemblePacket(byte[] packet) {
        PacketType packetType = PacketType.getPacketType(ByteBuffer.wrap(packet, 0, Integer.BYTES).getInt());
        int packetLength = ByteBuffer.wrap(packet, Integer.BYTES,  Integer.BYTES).getInt();
        int sequenceNumber = ByteBuffer.wrap(packet, 2 * Integer.BYTES,  Integer.BYTES).getInt();
        byte[] data = Arrays.copyOfRange(packet, 3 * Integer.BYTES, packetLength);
        return new Packet(packetType, packetLength - Packet.headerLength, sequenceNumber, data);
    }

    private byte[] intToBytes(int x) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(x);
        return buffer.array();
    }

    public int getPacketLength() {
        return packetLength;
    }

    public byte[] getData() {
        return data;
    }

    public PacketType getPacketType() {
        return packetType;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public Timer getPacketTimer() {
        return packetTimer;
    }

    public void setPacketTimer(Timer packetTimer) {
        this.packetTimer = packetTimer;
    }

    public boolean isACKed() {
        return isACKed;
    }

    public void setACKed(boolean ACKed) {
        isACKed = ACKed;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
