package com.chatapp.service;

import com.chatapp.util.ClientSocket;
import java.io.IOException;

public class SocketClient {
    private static SocketClient instance;
    private ClientSocket clientSocket;

    private SocketClient() {}

    // Lấy instance duy nhất
    public static synchronized SocketClient getInstance() {
        if (instance == null) {
            instance = new SocketClient();
        }
        return instance;
    }

    // Kết nối tới server
    public void connect(String host, int port) throws IOException {
        if (clientSocket == null) {
            clientSocket = new ClientSocket(host, port);
        }
    }

    // Gửi tin nhắn
    public void sendMessage(String msg) {
        if (clientSocket != null) {
            clientSocket.sendMessage(msg);
        }
    }

    // Nhận tin nhắn (Dùng cho Login đồng bộ)
    public String receiveMessage() throws IOException {
        if (clientSocket != null) {
            return clientSocket.receiveMessage();
        }
        return null;
    }

    // Getter cho ClientSocket gốc (nếu cần xử lý sâu)
    public ClientSocket getClientSocket() {
        return clientSocket;
    }

    public int getCurrentUserId() {
        return clientSocket != null ? clientSocket.getCurrentUserId() : -1;
    }

    public void close() {
        // Logic đóng socket nếu cần
    }
}