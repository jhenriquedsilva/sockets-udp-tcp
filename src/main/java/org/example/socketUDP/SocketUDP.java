package org.example.socketUDP;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SocketUDP {
    private static final int DNS_SERVER_PORT = 53;

    public static void main(String[] args) throws IOException {

        InetAddress ipAddress = InetAddress.getByName("1.0.0.1");


        short ID = createId();
        byte[] flagsByteArray = createFlags();

        short QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        dataOutputStream.writeShort(ID);
        dataOutputStream.write(flagsByteArray);
        dataOutputStream.writeShort(QDCOUNT);
        dataOutputStream.writeShort(ANCOUNT);
        dataOutputStream.writeShort(NSCOUNT);
        dataOutputStream.writeShort(ARCOUNT);

        String domain = "ufpa.br";
        String[] domainParts = domain.split("\\.");

        for (int i = 0; i < domainParts.length; i++) {
            byte[] domainBytes = domainParts[i].getBytes(StandardCharsets.UTF_8);
            dataOutputStream.writeByte(domainBytes.length);
            dataOutputStream.write(domainBytes);
        }

        dataOutputStream.writeByte(0);

        dataOutputStream.writeShort(1);

        dataOutputStream.writeShort(1);

        byte[] dnsFrame = byteArrayOutputStream.toByteArray();

        System.out.println("Sending: " + dnsFrame.length + " bytes");
        for (int i = 0; i < dnsFrame.length; i++) {
            System.out.print(String.format("%s", dnsFrame[i]) + " ");
        }

        DatagramPacket dnsReqPacket = new DatagramPacket(dnsFrame, dnsFrame.length, ipAddress, DNS_SERVER_PORT);
        DatagramSocket socket = new DatagramSocket();
        socket.send(dnsReqPacket);

        byte[] response = new byte[1024];
        DatagramPacket packet = new DatagramPacket(response, response.length);
        socket.receive(packet);

        System.out.println("\n\nReceived: " + packet.getLength() + " bytes");
        for (int i = 0; i < packet.getLength(); i++) {
            System.out.print(String.format("%s", response[i]) + " ");
        }
        System.out.println("\n");

        System.out.println("\n\nStart decoding");

        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));

        readId(dataInputStream);

        readFlags(dataInputStream);

        // Read header
        QDCOUNT = dataInputStream.readShort();
        ANCOUNT = dataInputStream.readShort();
        NSCOUNT = dataInputStream.readShort();
        ARCOUNT = dataInputStream.readShort();

        System.out.println("Questions: " + String.format("%s", QDCOUNT) +
                "\nAnswers RRs: " + String.format("%s", ANCOUNT) +
                "\nAuthority RRs: " + String.format("%s", NSCOUNT) +
                "\nAdditional RRs: " + String.format("%s", ARCOUNT));

        readQuestion(dataInputStream);

        // Read answer, authority, and additional sections
        byte firstBytes = dataInputStream.readByte();
        int firstTwoBits = (firstBytes & 0b11000000) >>> 6;

        ByteArrayOutputStream label = new ByteArrayOutputStream();
        Map<String, String> domainToIp = new HashMap<>();

        for (int i = 0; i < ANCOUNT; i++) {
            if (firstTwoBits == 3) {
                byte currentByte = dataInputStream.readByte();
                boolean stop = false;
                byte[] newArray = Arrays.copyOfRange(response, currentByte, response.length);
                DataInputStream sectionDataInputStream = new DataInputStream(new ByteArrayInputStream(newArray));
                ArrayList<Integer> RDATA = new ArrayList<>();
                ArrayList<String> DOMAINS = new ArrayList<>();
                while (!stop) {
                    byte nextByte = sectionDataInputStream.readByte();
                    if (nextByte != 0) {
                        byte[] currentLabel = new byte[nextByte];
                        for (int j = 0; j < nextByte; j++) {
                            currentLabel[j] = sectionDataInputStream.readByte();
                        }
                        label.write(currentLabel);
                    } else {
                        stop = true;
                        short TYPE = dataInputStream.readShort();
                        short CLASS = dataInputStream.readShort();
                        int TTL = dataInputStream.readInt();
                        int RDLENGTH = dataInputStream.readShort();
                        for (int s = 0; s < RDLENGTH; s++) {
                            int nx = dataInputStream.readByte() & 255;// and with 255 to
                            RDATA.add(nx);
                        }

                        System.out.println("Type: " + TYPE +
                                            "\nClass: " + CLASS +
                                            "\nTime to live: " + TTL +
                                            "\nRd Length: " + RDLENGTH);
                    }

                    DOMAINS.add(label.toString(StandardCharsets.UTF_8));
                    label.reset();
                }

                StringBuilder ip = new StringBuilder();
                StringBuilder domainSb = new StringBuilder();
                for (Integer ipPart : RDATA) {
                    ip.append(ipPart).append(".");
                }

                for (String domainPart : DOMAINS) {
                    if (!domainPart.isEmpty()) {
                        domainSb.append(domainPart).append(".");
                    }
                }
                String domainFinal = domainSb.toString();
                String ipFinal = ip.toString();
                domainToIp.put(ipFinal.substring(0, ipFinal.length() - 1),
                        domainFinal.substring(0, domainFinal.length() - 1));

            } else if (firstTwoBits == 0) {
                System.out.println("It's a label");
            }

            firstBytes = dataInputStream.readByte();
            firstTwoBits = (firstBytes & 0b11000000) >>> 6;
        }

        domainToIp.forEach((key, value) -> System.out.println(key + " : " + value));
    }

    static short createId() {
        Random random = new Random();
        return (short) random.nextInt(32767);
    }

    static byte[] createFlags() {
        short requestFlags = Short.parseShort("0000000100000000", 2);
        ByteBuffer byteBuffer = ByteBuffer.allocate(2).putShort(requestFlags);
        return  byteBuffer.array();
    }

    private static void readId(DataInputStream dataInputStream) throws IOException {
        System.out.println("\n\nID: " + dataInputStream.readShort());
    }

    private static void readFlags(DataInputStream dataInputStream) throws IOException {
        short flags = dataInputStream.readByte();

        System.out.println("QR: " + ((flags & 0b10000000) >>> 7) +
                "\nOpcode: " + ((flags & 0b01111000) >>> 3) +
                "\nAA: " + ((flags & 0b00000100) >>> 2) +
                "\nTC: " + ((flags & 0b00000010) >>> 1) +
                "\nRD: " + (flags & 0b00000001));
        flags = dataInputStream.readByte();

        System.out.println("RA: " + ((flags & 0b10000000) >>> 7) +
                "\nZ: " + ((flags & 0b01110000) >>> 4) +
                "\nRCODE: " + (flags & 0b00001111));
    }

    private static void readHeader(DataInputStream dataInputStream) throws IOException {
        short QDCOUNT = dataInputStream.readShort();
        short ANCOUNT = dataInputStream.readShort();
        short  NSCOUNT = dataInputStream.readShort();
        short ARCOUNT = dataInputStream.readShort();

        System.out.println("Questions: " + String.format("%s", QDCOUNT) +
                "\nAnswers RRs: " + String.format("%s", ANCOUNT) +
                "\nAuthority RRs: " + String.format("%s", NSCOUNT) +
                "\nAdditional RRs: " + String.format("%s", ARCOUNT));
    }

    private static void readQuestion(DataInputStream dataInputStream) throws IOException {
        String QNAME = "";
        int recLen;
        while ((recLen = dataInputStream.readByte()) > 0) {
            byte[] record = new byte[recLen];
            for (int i = 0; i < recLen; i++) {
                record[i] = dataInputStream.readByte();
            }
            QNAME = new String(record, StandardCharsets.UTF_8);
        }

        short QTYPE = dataInputStream.readShort();
        short QCLASS = dataInputStream.readShort();

        System.out.println("Record: " + QNAME +
                "\nRecord Type: " + String.format("%s", QTYPE) +
                "\nClass: " + String.format("%s", QCLASS) +
                "\n\nStart answer, authority, and additional sections\n");
    }
}

