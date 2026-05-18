package com.chatapp.controller;

import com.chatapp.util.ClientSocket;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class AIChatController {
    @FXML
    private TextArea aiChatArea;
    @FXML
    private TextField aiInputParams;
    @FXML
    private Button sendBtn; // Thêm nút gửi vào controller để khóa lại khi đang gõ

    private ClientSocket clientSocket;

    @FXML
    public void initialize() {
        Platform.runLater(() -> aiInputParams.requestFocus());
    }

    public void setClientSocket(ClientSocket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void addUserMessage(String message) {
        Platform.runLater(() -> {
            aiChatArea.appendText("\n🧑 BẠN: " + message + "\n");
            scrollToBottom();
        });
    }

    /**
     * ✅ CẬP NHẬT MỚI: Hiệu ứng gõ chữ từng ký tự
     */
    public void addAIResponse(String message) {
        String formatContent = message.replace("\\n", "\n").trim();

        // Chạy trên luồng nền để không làm đơ giao diện
        new Thread(() -> {

            // 1. Hiển thị tiêu đề trước
            Platform.runLater(() -> {
                aiChatArea.appendText("\n🤖 GEMINI:\n");
                // Khóa ô nhập liệu để tránh spam khi AI đang trả lời (tuỳ chọn)
                aiInputParams.setDisable(true);
                if(sendBtn != null) sendBtn.setDisable(true);
            });

            // 2. Vòng lặp gõ từng chữ
            try {
                for (int i = 0; i < formatContent.length(); i++) {
                    char c = formatContent.charAt(i);

                    // Cập nhật UI (phải dùng Platform.runLater)
                    Platform.runLater(() -> {
                        aiChatArea.appendText(String.valueOf(c));
                        scrollToBottom();
                    });

                    // Tốc độ gõ: 10ms - 30ms là đẹp nhất
                    // Nếu văn bản dài, gõ nhanh (10ms), ngắn thì chậm (30ms)
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // 3. Kết thúc và mở khóa nhập liệu
            Platform.runLater(() -> {
                aiChatArea.appendText("\n__________________________________________________\n");
                scrollToBottom();
                aiInputParams.setDisable(false);
                if(sendBtn != null) sendBtn.setDisable(false);
                aiInputParams.requestFocus(); // Focus lại để chat tiếp
            });

        }).start();
    }

    @FXML
    private void sendMessageToAI() {
        String msg = aiInputParams.getText().trim();
        if (msg.isEmpty()) return;

        if (clientSocket != null) {
            addUserMessage(msg);
            clientSocket.sendMessage("AI:" + msg);
            aiInputParams.clear();
        }
    }

    private void scrollToBottom() {
        aiChatArea.selectPositionCaret(aiChatArea.getLength());
        aiChatArea.deselect();
    }
}