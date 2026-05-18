package com.chatapp.model.entity;

import java.sql.Timestamp;

public class ChatRoom {
    private int id;
    private String name;
    private String type;
    private int creatorId; // Đã thêm trường này để khớp với DB và DAO
    private Timestamp createdAt;

    // Constructor mặc định
    public ChatRoom() {
    }

    // Constructor đầy đủ
    public ChatRoom(int id, String name, String type, int creatorId, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.creatorId = creatorId;
        this.createdAt = createdAt;
    }

    // Getters và Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "ChatRoom{id=" + id + ", name='" + name + "', type='" + type + "'}";
    }
}