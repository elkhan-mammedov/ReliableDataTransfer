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

public class GBNSender {
    public GBNSender(int timeout, String fileName) throws SocketException {
        this.fileName = fileName;
        this.timeout = timeout;
        timer = new Timer();
        clientSocket = new DatagramSocket();
        LOG = new LogUtils();
        channel = new Channel();
        channel.readChannelInfo();
    }

    private final int timeout;
    private final String fileName;
    private final int windowSize = 10;
    private final Channel channel;
    private DatagramSocket clientSocket;
    private InetAddress inetAddress;
    private Timer timer;
    private LogUtils LOG;

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
             * Calculate number of ACKed packets.
             */
            int numberOfACKedPackets = receivedPacket.getSequenceNumber() - (base % Packet.maxSequenceNumber);
            if ( Packet.maxSequenceNumber - (base % Packet.maxSequenceNumber) <= windowSize && receivedPacket.getSequenceNumber() < windowSize ) {
                numberOfACKedPackets = Packet.maxSequenceNumber - (base % Packet.maxSequenceNumber) + receivedPacket.getSequenceNumber();
            }

            if ( numberOfACKedPackets < windowSize && numberOfACKedPackets >= 0 ) {
                int baseOld = base;
                base = base + numberOfACKedPackets + 1;
                for ( int i = baseOld; i < base; i++ ) {
                    synchronized (window) {
                        window.remove(i);
                    }
                }
                if ( base == sequenceNumber ) {
                    timer.cancel();
                    timer.purge();
                    timer = new Timer();
                    if ( base == numberOfPackets ) {
                        break;
                    }
                } else {
                    timer.cancel();
                    timer.purge();
                    timer = new Timer();
                    timer.schedule(new GBNTimerTask(base, windowSize, window), timeout);
                }
            }
        }

        /*
         * Now, send EOT packet.
         */
        timer.cancel();
        timer.purge();
        sendPacket(sequenceNumber, window, new byte[0], 0, base, PacketType.EOT, false);
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
        byte[] packet = dataPacket.createPacket();
        synchronized (window) {
            window.put(sequenceNumber, dataPacket);
        }
        DatagramPacket datagramPacket = new DatagramPacket(packet, dataPacket.getPacketLength(), inetAddress, channel.getPort());
        clientSocket.send(datagramPacket);
        LOG.sendPacket(dataPacket);
        if ( sequenceNumber == base && setTimer ) {
            timer.cancel();
            timer.purge();
            timer = new Timer();
            timer.schedule(new GBNTimerTask(base, windowSize, window), timeout);
        }
        sequenceNumber++;
        return sequenceNumber;
    }

    public static void main(String[] args) {
        try {
            if ( args.length == 2 ) {
                GBNSender GBNSender = new GBNSender(Integer.valueOf(args[0]), args[1]);
                GBNSender.send();
            } else {
                System.out.println("Incorrect number of arguments passed.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class GBNTimerTask extends TimerTask {
        public GBNTimerTask(int base, int windowSize, HashMap<Integer, Packet> window) {
            this.base = base;
            this.windowSize = windowSize;
            this.window = window;
        }

        int base;
        int windowSize;
        HashMap<Integer, Packet> window;

        @Override
        public void run() {
            timer.cancel();
            timer.purge();
            timer = new Timer();
            timer.schedule(new GBNTimerTask(base, windowSize, window), timeout);
            for ( int i = base; i < base + windowSize; i++ ) {
                Packet dataPacket = window.get(i);
                if ( dataPacket == null ) {
                    continue;
                }

                try {
                    byte[] packet = dataPacket.createPacket();
                    DatagramPacket datagramPacket = new DatagramPacket(packet, dataPacket.getPacketLength(), inetAddress, channel.getPort());
                    clientSocket.send(datagramPacket);
                    LOG.sendPacket(dataPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
