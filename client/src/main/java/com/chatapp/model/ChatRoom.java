package com.chatapp.model;

import java.sql.Timestamp;

public class ChatRoom {
    private int id;
    private String name;
    private String type;
    private Timestamp createdAt;

    // Constructors, getters, setters

    public ChatRoom(int id, String name, String type, Timestamp createdAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.createdAt = createdAt;
    }

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

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}