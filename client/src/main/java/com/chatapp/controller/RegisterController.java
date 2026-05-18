package com.chatapp.controller;

import com.chatapp.util.ClientSocket;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;

    private ClientSocket clientSocket;

    public void setClientSocket(ClientSocket socket) {
        this.clientSocket = socket;
    }

    @FXML
    private void register() throws IOException {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String email = emailField.getText();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            showAlert("Vui lòng điền đầy đủ thông tin!");
            return;
        }

        // Gửi request đăng ký
        clientSocket.sendMessage("REGISTER:" + username + ":" + password + ":" + email);
        String response = clientSocket.receiveMessage();

        if (response.startsWith("OK")) {
            showAlert("Đăng ký thành công! Quay về đăng nhập.");
            backToLogin();
        } else {
            showAlert("Lỗi: " + response.split(":")[1]);
        }
    }

    @FXML
    private void backToLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/view/LoginView.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.getScene().setRoot(root);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(message);
        alert.show();
    }
}