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

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;

    private ClientSocket clientSocket;

    @FXML
    public void initialize() {
        try {
            clientSocket = new ClientSocket("localhost", 100);
            System.out.println("Kết nối socket thành công.");
        } catch (IOException e) {
            showAlert("Lỗi kết nối", "Không thể kết nối đến server.");
            e.printStackTrace();
        }
    }

    @FXML
    private void login() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Lỗi", "Vui lòng nhập đầy đủ thông tin.");
            return;
        }

        try {
            clientSocket.sendMessage("LOGIN:" + username + ":" + password);
            String response = clientSocket.receiveMessage();

            // Server gửi về: "OK:Login successful:105" (Ví dụ ID là 105)
            if (response != null && response.startsWith("OK")) {

                // --- ĐOẠN CODE MỚI ĐỂ LẤY ID ---
                String[] parts = response.split(":");
                if (parts.length >= 3) {
                    try {
                        int userId = Integer.parseInt(parts[2]);
                        clientSocket.setCurrentUserId(userId); // Lưu ID vào Socket
                        System.out.println(">> Đăng nhập thành công! My ID = " + userId);
                    } catch (NumberFormatException e) {
                        System.err.println("Lỗi: Server không gửi về đúng định dạng ID số.");
                    }
                }
                // -------------------------------

                // Chuyển sang màn hình chat chính
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/view/MainChatView.fxml"));
                Parent root = loader.load();

                MainChatController mainController = loader.getController();
                mainController.setClientSocket(clientSocket); // Truyền socket (đã có ID) sang controller mới

                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.setTitle("Chat App - " + username);
                stage.sizeToScene();
                stage.centerOnScreen();

            } else {
                // Xử lý lỗi đăng nhập
                String errorMsg = (response != null && response.contains(":"))
                        ? response.split(":")[1]
                        : "Đăng nhập thất bại";
                showAlert("Lỗi đăng nhập", errorMsg);
            }
        } catch (IOException e) {
            showAlert("Lỗi", "Không thể nhận phản hồi từ server.");
            e.printStackTrace();
        }
    }

    @FXML
    private void register() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/view/RegisterView.fxml"));
        Parent root = loader.load();
        RegisterController controller = loader.getController();
        controller.setClientSocket(clientSocket);
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.getScene().setRoot(root);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}