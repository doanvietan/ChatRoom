package com.chatapp.service;

import javafx.application.Application;

public class ServerMain {
    public static void main(String[] args) {
        System.out.println("Đang khởi động hệ thống quản lý Server...");

        // Kích hoạt giao diện JavaFX
        Application.launch(ServerGUI.class, args);
    }
}