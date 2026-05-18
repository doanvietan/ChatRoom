package com.chatapp.util;

import java.io.*;
import java.net.Socket;

public class ClientSocket {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private int userId;

    public ClientSocket(String host, int port) throws IOException {
        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String receiveMessage() throws IOException {
        return in.readLine();
    }

    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Getter & Setter cho ID của chính user này
    public int getCurrentUserId() {
        return userId;
    }

    public void setCurrentUserId(int id) {
        this.userId = id;
    }
}