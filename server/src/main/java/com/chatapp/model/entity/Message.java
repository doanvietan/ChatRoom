package com.chatapp.model.entity;

import java.sql.Timestamp;

public class Message {
    private long id; // Đổi sang long vì DB là BIGINT
    private int roomId;
    private int senderId;
    private String content;
    private String type; // 'TEXT', 'FILE', 'IMAGE', 'VIDEO'
    private String filePath; // Thêm trường này để khớp với DB
    private Timestamp timestamp;

    public Message() {
    }

    // Constructor dùng khi tạo tin nhắn mới (chưa có ID và Time)
    public Message(int roomId, int senderId, String content, String type, String filePath) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.type = type;
        this.filePath = filePath;
    }

    // Getters và Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}