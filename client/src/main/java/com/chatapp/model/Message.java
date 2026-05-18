package com.chatapp.model;

import java.sql.Timestamp;

public class Message {
    private int id;
    private int roomId;
    private int senderId;
    private String content;
    private String type;
    private Timestamp timestamp;

    // Constructors, getters, setters
    public Message() {}
    public Message(int roomId, int senderId, String content, String type) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.type = type;
    }

    // Getters/Setters...

    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}