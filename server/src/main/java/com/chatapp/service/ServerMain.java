package com.chatapp.service;

import com.chatapp.util.DBConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;

public class ServerMain {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("Connected to MySQL database successfully!");
            }
        } catch (SQLException e) {
            System.err.println("Failed to connect to MySQL: " + e.getMessage());
            return;
        }

        new Thread(new VideoRelayServer()).start();

        // 2. Khởi chạy Chat Server (TCP)
        try (ServerSocket serverSocket = new ServerSocket(100)) {
            System.out.println("Chat Server started on TCP port 100");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                System.out.println("New chat client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}