import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class SRSender {
    public SRSender(int timeout, String fileName) throws SocketException {
        this.fileName = fileName;
        this.timeout = timeout;
        clientSocket = new DatagramSocket();
        channel = new Channel();
        channel.readChannelInfo();
        LOG = new LogUtils();
        timer = new Timer();
    }

    private final int timeout;
    private LogUtils LOG;
    private final String fileName;
    private final int windowSize = 10;
    private final Channel channel;
    private DatagramSocket clientSocket;
    private InetAddress inetAddress;
    private Timer timer;

    public void send() throws IOException {
        int base = 0;
        int sequenceNumber = 0;
        HashMap<Integer, Packet> window = new HashMap();
        byte[] data = new byte[Packet.payloadSize];
        File file = new File(getClass().getResource(fileName).getFile());
        BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
        inetAddress = InetAddress.getByName(channel.getHost());
        int numberOfPackets = (int) Math.ceil((double) file.length() / Packet.payloadSize);

        while ( true ) {
            /*
             * First check if we can send more packets. If yes, do so.
             */
            while ( sequenceNumber < base + windowSize && sequenceNumber < numberOfPackets) {
                int currentPayloadSize = bufferedInputStream.read(data);
                sequenceNumber = sendPacket(sequenceNumber, window, data, currentPayloadSize, base, PacketType.DATA, true);
            }

            /*
             * Now that we sent as many packets as we could, wait for ACKs.
             */
            byte[] buffer = new byte[Packet.headerLength];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            clientSocket.receive(packet);
            Packet receivedPacket = Packet.reassemblePacket(buffer);
            LOG.receivePacket(receivedPacket);

            /*
             * Calculate position of ACKed packet.
             */
            int positionOfACKedPacketInWindow = receivedPacket.getSequenceNumber() - (base % Packet.maxSequenceNumber);
            if ( Packet.maxSequenceNumber - (base % Packet.maxSequenceNumber) <= windowSize && receivedPacket.getSequenceNumber() < windowSize ) {
                positionOfACKedPacketInWindow = Packet.maxSequenceNumber - (base % Packet.maxSequenceNumber) + receivedPacket.getSequenceNumber();
            }

            if ( positionOfACKedPacketInWindow < windowSize && positionOfACKedPacketInWindow >= 0 ) {
                window.get(base + positionOfACKedPacketInWindow).setACKed(true);

                if ( positionOfACKedPacketInWindow == 0 ) {
                    int slideSize = 0;
                    for ( int i = base; i < base + windowSize; i++ ) {
                        Packet packetInWindow = window.get(i);
                        if ( packetInWindow != null && packetInWindow.isACKed() ) {
                            slideSize++;
                            synchronized (window) {
                                window.remove(i);
                            }
                        } else {
                            break;
                        }
                    }
                    base += slideSize;
                }

                if ( base == numberOfPackets ) {
                    break;
                }
            }
        }

        /*
         * Now, send EOT packet.
         */
        sendPacket(sequenceNumber, window, new byte[0], 0, base, PacketType.EOT, true);
        byte[] buffer = new byte[Packet.headerLength];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        clientSocket.receive(packet);
        Packet receivedPacket = Packet.reassemblePacket(buffer);
        LOG.receivePacket(receivedPacket);
        while ( receivedPacket.getPacketType() != PacketType.EOT ) {
            clientSocket.receive(packet);
            receivedPacket = Packet.reassemblePacket(buffer);
            LOG.receivePacket(receivedPacket);
        }

        /*
         * Close resources.
         */
        clientSocket.close();
        bufferedInputStream.close();

        System.exit(0);
    }

    private int sendPacket(int sequenceNumber, HashMap<Integer, Packet> window, byte[] data,
                           int currentPayloadSize, int base, PacketType packetType, boolean setTimer) throws IOException {
        Packet dataPacket = new Packet(packetType, currentPayloadSize, sequenceNumber, Arrays.copyOf(data, currentPayloadSize));
        dataPacket.setTimestamp(System.currentTimeMillis() + timeout);
        byte[] packet = dataPacket.createPacket();
        synchronized (window) {
            window.put(sequenceNumber, dataPacket);
        }
        DatagramPacket datagramPacket = new DatagramPacket(packet, dataPacket.getPacketLength(), inetAddress, channel.getPort());
        clientSocket.send(datagramPacket);
        LOG.sendPacket(dataPacket);
        if ( base == 0 ) {
            timer.schedule(new SRTimerTask(window), timeout);
        }
        sequenceNumber++;
        return sequenceNumber;
    }

    public static void main(String[] args) {
        try {
            if ( args.length == 2 ) {
                SRSender SRSender = new SRSender(Integer.valueOf(args[0]), args[1]);
                SRSender.send();
            } else {
                System.out.println("Incorrect number of arguments passed.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class SRTimerTask extends TimerTask {
        SRTimerTask(HashMap<Integer, Packet> window) {
            this.window = window;
        }

        HashMap<Integer, Packet> window;

        @Override
        public void run() {
            try {
                long minTime = Long.MAX_VALUE;
                long newTimeout = timeout;
                long currentTime = System.currentTimeMillis();
                synchronized (window) {
                    for (int i : window.keySet()) {
                        if (currentTime >= window.get(i).getTimestamp() && !window.get(i).isACKed()) {
                            Packet packet = window.get(i);
                            DatagramPacket datagramPacket = new DatagramPacket(packet.createPacket(), packet.getPacketLength(), inetAddress, channel.getPort());
                            clientSocket.send(datagramPacket);
                            LOG.sendPacket(packet);
                            packet.setTimestamp(System.currentTimeMillis() + timeout);
                            break;
                        }
                    }

                    for (int i : window.keySet()) {
                        if (currentTime < window.get(i).getTimestamp() && window.get(i).getTimestamp() < minTime && !window.get(i).isACKed()) {
                            minTime = window.get(i).getTimestamp();
                            newTimeout = minTime - currentTime;
                        }
                    }
                }
                timer.cancel();
                timer.purge();
                timer = new Timer();
                timer.schedule(new SRTimerTask(window), newTimeout);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
