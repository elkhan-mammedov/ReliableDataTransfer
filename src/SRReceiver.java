import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;

public class SRReceiver {
    public SRReceiver(String fileName) {
        this.fileName = fileName;
        LOG = new LogUtils();
        try {
            this.serverSocket = new DatagramSocket();
            String parentDir = getClass().getResource("/").getFile();
            File file = new File(parentDir, "recvInfo");
            FileWriter fileWriter = new FileWriter(file);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(InetAddress.getLocalHost().getHostName() + " " + serverSocket.getLocalPort());
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String fileName;
    private final int windowSize = 10;
    private DatagramSocket serverSocket;
    private LogUtils LOG;

    public void run() {
        try {
            int expectedSequenceNumber = 0;
            HashMap<Integer, Packet> window = new HashMap();
            InetAddress inetAddress = null;
            Channel channel = new Channel();
            boolean readChannelInfo = false;
            Packet ackPacket;
            byte[] buffer = new byte[Packet.payloadSize + Packet.headerLength];
            FileOutputStream fileOutputStream = new FileOutputStream(fileName);

            while ( true ) {
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(datagramPacket);
                if ( !readChannelInfo ) {
                    channel.setHost(datagramPacket.getAddress().getHostName());
                    inetAddress = InetAddress.getByName(channel.getHost());
                    channel.setPort(datagramPacket.getPort());
                    readChannelInfo = true;
                }

                /*
                 * Here we unpack the message that we received.
                 */
                Packet receivedPacket = Packet.reassemblePacket(buffer);
                LOG.receivePacket(receivedPacket);
                if ( receivedPacket.getPacketType() == PacketType.EOT ) {
                    Packet eotPacket = new Packet(PacketType.EOT, 0, expectedSequenceNumber, new byte[0]);
                    byte[] packet = eotPacket.createPacket();
                    datagramPacket = new DatagramPacket(packet, eotPacket.getPacketLength(), inetAddress, channel.getPort());
                    serverSocket.send(datagramPacket);
                    LOG.sendPacket(eotPacket);
                    break;
                }

                /*
                 * Calculate position of ACKed packet.
                 */
                int positionOfACKedPacketInWindow = receivedPacket.getSequenceNumber() - (expectedSequenceNumber % Packet.maxSequenceNumber);
                if ( Packet.maxSequenceNumber - (expectedSequenceNumber % Packet.maxSequenceNumber) <= windowSize && receivedPacket.getSequenceNumber() < windowSize ) {
                    positionOfACKedPacketInWindow = Packet.maxSequenceNumber - (expectedSequenceNumber % Packet.maxSequenceNumber) + receivedPacket.getSequenceNumber();
                } else if ( Packet.maxSequenceNumber - receivedPacket.getSequenceNumber() <= windowSize && (expectedSequenceNumber % Packet.maxSequenceNumber) < windowSize ) {
                    positionOfACKedPacketInWindow = receivedPacket.getSequenceNumber() - Packet.maxSequenceNumber - (expectedSequenceNumber % Packet.maxSequenceNumber);
                }

                if ( receivedPacket.getSequenceNumber() == (expectedSequenceNumber % Packet.maxSequenceNumber) ) {
                    ackPacket = new Packet(PacketType.ACK, 0, receivedPacket.getSequenceNumber(), new byte[0]);
                    byte[] packet = ackPacket.createPacket();
                    datagramPacket = new DatagramPacket(packet, ackPacket.getPacketLength(), inetAddress, channel.getPort());
                    serverSocket.send(datagramPacket);
                    LOG.sendPacket(ackPacket);
                    fileOutputStream.write(receivedPacket.getData());
                    expectedSequenceNumber++;

                    int slideSize = 0;
                    for ( int i = expectedSequenceNumber; i < expectedSequenceNumber + windowSize; i++ ) {
                        if ( window.containsKey(i) ) {
                            Packet packetInWindow = window.get(i);
                            fileOutputStream.write(packetInWindow.getData());
                            window.remove(i);
                            slideSize++;
                        } else {
                            break;
                        }
                    }

                    expectedSequenceNumber += slideSize;
                } else if ( positionOfACKedPacketInWindow < windowSize && positionOfACKedPacketInWindow > 0) {
                    ackPacket = new Packet(PacketType.ACK, 0, receivedPacket.getSequenceNumber(), new byte[0]);
                    byte[] packet = ackPacket.createPacket();
                    datagramPacket = new DatagramPacket(packet, ackPacket.getPacketLength(), inetAddress, channel.getPort());
                    serverSocket.send(datagramPacket);
                    LOG.sendPacket(ackPacket);

                    window.put(expectedSequenceNumber + positionOfACKedPacketInWindow, receivedPacket);
                } else if ( positionOfACKedPacketInWindow < 0 ) {
                    ackPacket = new Packet(PacketType.ACK, 0, receivedPacket.getSequenceNumber(), new byte[0]);
                    byte[] packet = ackPacket.createPacket();
                    datagramPacket = new DatagramPacket(packet, ackPacket.getPacketLength(), inetAddress, channel.getPort());
                    serverSocket.send(datagramPacket);
                    LOG.sendPacket(ackPacket);
                }
            }

            fileOutputStream.close();
            serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        try {
            if ( args.length == 1 ) {
                SRReceiver SRReceiver = new SRReceiver(args[0]);
                SRReceiver.run();
            } else {
                System.out.println("Incorrect number of arguments passed.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
