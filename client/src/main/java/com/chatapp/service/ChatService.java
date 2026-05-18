package com.chatapp.service;

import javafx.application.Platform;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatService {
    private final SocketClient socketClient;
    private final ChatEventListener listener;
    private boolean isRunning = true;

    // Cache danh sách user để map ID sang Name
    private final Map<Integer, String> idToUserMap = new ConcurrentHashMap<>();

    public ChatService(ChatEventListener listener) {
        this.listener = listener;
        this.socketClient = SocketClient.getInstance();
    }

    // Bắt đầu lắng nghe tin nhắn từ Server
    public void startListening() {
        Thread thread = new Thread(() -> {
            try {
                String response;
                while (isRunning && (response = socketClient.receiveMessage()) != null) {
                    processResponse(response);
                }
            } catch (IOException e) {
                Platform.runLater(listener::onConnectionLost);
            }
        });
        thread.setDaemon(true); // Tự động tắt khi tắt app
        thread.start();
    }

    public void stop() {
        isRunning = false;
    }

    private void processResponse(String response) {
        if (response == null || response.isEmpty()) return;

        Platform.runLater(() -> {
            if (response.startsWith("MESSAGE:")) {
                handleNewMessage(response.substring(8));
            } else if (response.startsWith("ROOMS:")) {
                handleRoomList(response.substring(6));
            } else if (response.startsWith("ALL_USERS:")) {
                handleAllUsers(response.substring(10));
            } else if (response.startsWith("ALL_ROOMS:")) {
                handleAllRooms(response.substring(10));
            } else if (response.startsWith("HISTORY:")) {
                listener.onHistoryLoaded(response.substring(8));
            } else if (response.startsWith("OK:JOINED:")) {
                // Xử lý logic join thành công, có thể reload lại danh sách phòng
                socketClient.sendMessage("GET_ROOMS");
            }
        });
    }

    private void handleNewMessage(String data) {
        try {
            String[] parts = data.split(":", 3);
            int senderId = Integer.parseInt(parts[1]);
            String content = parts.length > 2 ? parts[2] : "";

            // Không hiện tin của chính mình (đã xử lý ở UI)
            if (senderId == socketClient.getCurrentUserId()) return;

            String senderName = idToUserMap.getOrDefault(senderId, "User " + senderId);
            listener.onMessageReceived(senderName, content);
        } catch (Exception e) {
            System.err.println("Error parsing message: " + e.getMessage());
        }
    }

    private void handleRoomList(String data) {
        Map<String, Integer> rooms = new HashMap<>();
        String[] rawRooms = data.split(";");
        for (String r : rawRooms) {
            if (!r.isEmpty()) {
                String[] parts = r.split(",");
                int id = Integer.parseInt(parts[0]);
                String name = parts.length > 1 ? parts[1] : "Chat";
                String type = parts.length > 2 ? parts[2] : "PRIVATE";
                rooms.put(name + " (" + type + ")", id);
            }
        }
        listener.onRoomListUpdated(rooms);
    }

    private void handleAllUsers(String data) {
        Map<Integer, String> users = new HashMap<>();
        idToUserMap.clear();
        String[] rawUsers = data.split(";");
        for (String u : rawUsers) {
            if (!u.isEmpty()) {
                String[] parts = u.split(",");
                int id = Integer.parseInt(parts[0]);
                String name = parts.length > 1 ? parts[1] : "Unknown";
                users.put(id, name);
                idToUserMap.put(id, name);
            }
        }
        listener.onUserListUpdated(users);
    }

    private void handleAllRooms(String data) {
        Map<String, Integer> publicRooms = new HashMap<>();
        String[] rawRooms = data.split(";");
        for (String r : rawRooms) {
            if (!r.isEmpty()) {
                String[] parts = r.split(",");
                int id = Integer.parseInt(parts[0]);
                String name = parts.length > 1 ? parts[1] : "Unknown";
                publicRooms.put(name, id);
            }
        }
        listener.onAllRoomsUpdated(publicRooms);
    }

    // --- Public Methods cho Controller gọi ---

    public void loadInitialData() {
        socketClient.sendMessage("GET_ROOMS");
        socketClient.sendMessage("GET_USERS");
        socketClient.sendMessage("GET_ALL_ROOMS");
    }

    public void sendMessage(int roomId, String message, String type) {
        socketClient.sendMessage("SEND_MESSAGE:" + roomId + ":" + message + ":" + type);
    }

    public void sendAiQuery(String query) {
        socketClient.sendMessage("AI:" + query);
    }

    public void joinRoom(int roomId) {
        socketClient.sendMessage("JOIN_ROOM:" + roomId);
    }

    public void startPrivateChat(String username) {
        socketClient.sendMessage("START_PRIVATE_CHAT:" + username);
    }

    public void getHistory(int roomId) {
        socketClient.sendMessage("GET_HISTORY:" + roomId);
    }

    public String getCurrentUserName() {
        return idToUserMap.getOrDefault(socketClient.getCurrentUserId(), "Bạn");
    }
}