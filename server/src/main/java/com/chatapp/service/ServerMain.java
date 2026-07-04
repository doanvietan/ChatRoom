package com.chatapp.service;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.chatapp.controller.ServerController;

/**
 * Application entry point (View layer bootstrap), tương tự vai trò của Main.java bên client:
 * chỉ nạp FXML + gắn Controller, không chứa logic UI hay nghiệp vụ.
 */
public class ServerMain extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/view/ServerView.fxml"));
        Parent root = loader.load();
        ServerController controller = loader.getController();

        stage.setTitle("ChatApp — Server Management");
        stage.setScene(new Scene(root, 980, 680));
        stage.setResizable(true);
        stage.setMinWidth(850);
        stage.setMinHeight(550);

        stage.setOnCloseRequest(e -> {
            controller.shutdown();
            Platform.exit();
        });

        stage.show();
    }

    public static void main(String[] args) {
        System.out.println("Đang khởi động hệ thống quản lý Server...");
        launch(args);
    }
}