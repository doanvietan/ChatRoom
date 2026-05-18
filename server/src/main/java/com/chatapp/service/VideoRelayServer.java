package com.chatapp.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VideoRelayServer implements Runnable {
    private static final int UDP_PORT = 200;
    private static final int BUFFER_SIZE = 60000;
    private boolean running = true;
    private DatagramSocket socket;

    private final Map<Integer, Map<Integer, UserAddress>> roomClients = new ConcurrentHashMap<>();

    public VideoRelayServer() {
        try {
            socket = new DatagramSocket(UDP_PORT);
            System.out.println("Video Relay Server started on UDP port " + UDP_PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                final byte[] dataCopy = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, dataCopy, 0, packet.getLength());
                final InetAddress senderAddress = packet.getAddress();
                final int senderPort = packet.getPort();
                final int dataLength = packet.getLength();

                new Thread(() -> handlePacket(dataCopy, dataLength, senderAddress, senderPort)).start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handlePacket(byte[] data, int length, InetAddress senderAddress, int senderPort) {
        try {
            String msg = new String(data, 0, Math.min(length, 50));

            if (msg.startsWith("VIDEO:")) {
                String[] parts = msg.split(":", 4);
                int roomId = Integer.parseInt(parts[1]);
                int senderId = Integer.parseInt(parts[2]);

                System.out.println("[Server] VIDEO từ room=" + roomId + ", user=" + senderId
                        + ", addr=" + senderAddress + ":" + senderPort + ", size=" + length);

                registerClient(roomId, senderId, senderAddress, senderPort);
                forwardVideo(roomId, senderId, data, length);
            } else if (msg.startsWith("REGISTER:")) {
                String[] parts = msg.split(":", 4);
                int roomId = Integer.parseInt(parts[1]);
                int userId = Integer.parseInt(parts[2]);
                registerClient(roomId, userId, senderAddress, senderPort);
                System.out.println("[Server] REGISTER room=" + roomId + ", user=" + userId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void registerClient(int roomId, int userId, InetAddress address, int port) {
        roomClients.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(userId, new UserAddress(address, port));
        System.out.println("[Server] Registered: room=" + roomId + ", user=" + userId
                + " -> " + address + ":" + port);
    }

    private void forwardVideo(int roomId, int senderId, byte[] data, int length) {
        Map<Integer, UserAddress> clients = roomClients.get(roomId);
        if (clients == null || clients.size() < 2) {
            System.out.println("[Server] Không đủ client trong room " + roomId + " để forward");
            return;
        }

        for (Map.Entry<Integer, UserAddress> entry : clients.entrySet()) {
            if (entry.getKey() != senderId) {
                UserAddress target = entry.getValue();
                try {
                    DatagramPacket forwardPacket = new DatagramPacket(
                            data, length, target.address, target.port
                    );
                    socket.send(forwardPacket);
                    System.out.println("[Server] Forward video đến user=" + entry.getKey()
                            + " @ " + target.address + ":" + target.port);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class UserAddress {
        InetAddress address;
        int port;

        public UserAddress(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }
    }
}
