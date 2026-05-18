package com.chatapp.service;

import java.util.Map;

public interface ChatEventListener {
    void onMessageReceived(String senderName, String message);
    void onRoomListUpdated(Map<String, Integer> rooms);
    void onUserListUpdated(Map<Integer, String> users);
    void onAllRoomsUpdated(Map<String, Integer> allRooms);
    void onHistoryLoaded(String historyContent);
    void onConnectionLost();
    void onJoinRoomSuccess(String roomName);
    // Bạn có thể thêm các sự kiện khác tùy nhu cầu
}