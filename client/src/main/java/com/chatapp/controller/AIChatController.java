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
    private Button sendBtn;

    private ClientSocket clientSocket;

    // Cờ kiểm soát luồng gõ chữ
    private volatile boolean isTyping = false;

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            aiInputParams.requestFocus();
            // Lời chào hiển thị ngay khi vừa mở form
            addAIResponse("Xin chào! Mình là Trợ lý ảo Gemini. Mình có thể giúp gì cho bạn hôm nay?");
        });
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

    public void addAIResponse(String message) {
        String formatContent = message.replace("\\n", "\n").trim();

        new Thread(() -> {
            // Bật cờ trạng thái đang gõ
            isTyping = true;

            Platform.runLater(() -> {
                aiChatArea.appendText("🤖 GEMINI: ");

                // Khóa ô text nhưng KHÔNG khóa nút bấm để user có thể bấm Dừng
                aiInputParams.setDisable(true);

                // Biến nút Gửi thành nút Dừng (Màu đỏ)
                if (sendBtn != null) {
                    sendBtn.setText("Dừng");
                    sendBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 20;");
                }
            });

            try {
                for (int i = 0; i < formatContent.length(); i++) {
                    // Nếu user bấm dừng -> Ngắt vòng lặp ngay lập tức
                    if (!isTyping) break;

                    char c = formatContent.charAt(i);
                    Platform.runLater(() -> {
                        aiChatArea.appendText(String.valueOf(c));
                        scrollToBottom();
                    });

                    Thread.sleep(20); // Tốc độ gõ
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Hoàn tất hoặc bị ngắt -> Khôi phục lại giao diện
            Platform.runLater(() -> {
                if (!isTyping) {
                    aiChatArea.appendText(" ... [Đã dừng]");
                }
                aiChatArea.appendText("");
                scrollToBottom();

                // Tắt cờ, mở khóa ô nhập, đổi lại nút Gửi (Màu xanh)
                isTyping = false;
                aiInputParams.setDisable(false);
                if (sendBtn != null) {
                    sendBtn.setText("Gửi");
                    sendBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-background-radius: 20;");
                }
                aiInputParams.requestFocus();
            });

        }).start();
    }

    @FXML
    private void sendMessageToAI() {
        // Nếu AI đang gõ mà user bấm nút này -> Dừng việc gõ lại
        if (isTyping) {
            isTyping = false;
            return;
        }

        // Logic gửi tin nhắn bình thường
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