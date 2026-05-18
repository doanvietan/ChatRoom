package com.chatapp.controller;

import com.chatapp.service.AudioRecognitionService;
import com.chatapp.util.ClientSocket;
import com.chatapp.util.StegoUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.vosk.Model;

public class MainChatController {
    @FXML private ListView<String> roomList;
    @FXML private ListView<String> allUsersList;
    @FXML private ListView<String> allRoomsList;
    @FXML private TextField messageField;

    // Giao diện Chat mới bằng VBox
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatBox;

    private ClientSocket clientSocket;
    private Stage aiStage;
    private AIChatController aiChatController;
    private int currentRoomId = -1;
    private Model voskModel;
    private AudioRecognitionService audioService = new AudioRecognitionService();

    private final Map<String, Integer> roomMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> userMap = new ConcurrentHashMap<>();
    private final Map<Integer, String> idToUserMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> allRoomMap = new ConcurrentHashMap<>();

    private volatile int pendingRoomId = -1;
    private volatile String pendingTargetUser = null;

    public void setClientSocket(ClientSocket socket) {
        this.clientSocket = socket;
        loadAllData();
        listenForMessages();
    }

    private void loadAllData() {
        clientSocket.sendMessage("GET_ROOMS");
        clientSocket.sendMessage("GET_USERS");
        clientSocket.sendMessage("GET_ALL_ROOMS");
        clientSocket.sendMessage("GET_ALL_USERS");
    }

    private void initVosk() {
        try {
            if (voskModel == null) {
                voskModel = new Model("model-vn");
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                showAlert("Lỗi Khởi Tạo AI", "Chi tiết lỗi: " + e.toString());
            });
        }
    }

    private void listenForMessages() {
        new Thread(() -> {
            try {
                String response;
                while ((response = clientSocket.receiveMessage()) != null) {
                    handleServerResponse(response);
                }
            } catch (IOException e) {
                Platform.runLater(() -> showAlert("Lỗi", "Mất kết nối với server."));
                e.printStackTrace();
            }
        }, "MessageListener").start();
    }

    private void handleServerResponse(String response) {
        if (response == null || response.isEmpty()) return;

        Platform.runLater(() -> {
            if (response.startsWith("MESSAGE:")) {
                handleNewMessage(response.substring(8));
            } else if (response.startsWith("ROOMS:")) {
                updateRoomList(response.substring(6));
            } else if (response.startsWith("ALL_USERS:")) {
                updateAllUsersList(response.substring(10));
            } else if (response.startsWith("ALL_ROOMS:")) {
                updateAllRoomsList(response.substring(10));
            } else if (response.startsWith("HISTORY:")) {
                updateHistory(response.substring(8));
            } else if (response.startsWith("OK:PRIVATE_CHAT:")) {
                handleNewPrivateRoom(response);
            } else if (response.startsWith("OK:JOINED:")) {
                loadRooms();
            } else if (response.startsWith("VIDEOCALL:")) {
                handleIncomingCall(response);
            } else if (response.startsWith("AI_RESPONSE:")) {
                handleAIResponse(response.substring(12));
            } else if (response.startsWith("FILE_DATA:")) {
                handleIncomingFile(response.substring(10));
            }
        });
    }

    // ================== GIAO DIỆN CHAT BẰNG VBOX (CĂN TRÁI/PHẢI) ==================

    private void clearChat() {
        Platform.runLater(() -> chatBox.getChildren().clear());
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    private void appendMessage(String username, String text, boolean isMine) {
        Platform.runLater(() -> {
            HBox messageContainer = new HBox();
            messageContainer.setMaxWidth(Double.MAX_VALUE);

            VBox bubble = new VBox(2);
            bubble.setStyle("-fx-background-radius: 15; -fx-padding: 10; " +
                    (isMine ? "-fx-background-color: #0084ff;" : "-fx-background-color: #e4e6eb;"));

            Label senderName = new Label(isMine ? "Bạn" : username);
            senderName.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isMine ? "#e0e0e0" : "#666666") + ";");

            Label messageLabel = new Label(text);
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(400);
            messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + (isMine ? "white" : "black") + ";");

            bubble.getChildren().addAll(senderName, messageLabel);

            if (isMine) {
                messageContainer.setAlignment(Pos.CENTER_RIGHT);
            } else {
                messageContainer.setAlignment(Pos.CENTER_LEFT);
            }

            messageContainer.getChildren().add(bubble);
            chatBox.getChildren().add(messageContainer);
            scrollToBottom();
        });
    }

    private void appendFileMessage(String username, String filename, boolean isMine) {
        Platform.runLater(() -> {
            HBox messageContainer = new HBox();
            messageContainer.setMaxWidth(Double.MAX_VALUE);

            VBox bubble = new VBox(2);
            bubble.setStyle("-fx-background-radius: 15; -fx-padding: 10; " +
                    (isMine ? "-fx-background-color: #0084ff;" : "-fx-background-color: #e4e6eb;"));

            Label senderName = new Label(isMine ? "Bạn" : username);
            senderName.setStyle("-fx-font-size: 10px; -fx-text-fill: " + (isMine ? "#e0e0e0" : "#666666") + ";");

            Hyperlink fileLink = new Hyperlink("📁 " + filename);
            fileLink.setStyle("-fx-text-fill: " + (isMine ? "white" : "#1a73e8") + "; -fx-font-weight: bold; -fx-underline: true;");
            fileLink.setOnAction(e -> handleFileClick(filename));

            bubble.getChildren().addAll(senderName, fileLink);

            if (isMine) {
                messageContainer.setAlignment(Pos.CENTER_RIGHT);
            } else {
                messageContainer.setAlignment(Pos.CENTER_LEFT);
            }

            messageContainer.getChildren().add(bubble);
            chatBox.getChildren().add(messageContainer);
            scrollToBottom();
        });
    }

    private void appendSystemMessage(String text) {
        Platform.runLater(() -> {
            HBox container = new HBox();
            container.setAlignment(Pos.CENTER);
            Label label = new Label(text);
            label.setStyle("-fx-font-size: 12px; -fx-text-fill: gray; -fx-font-style: italic; -fx-padding: 10;");
            container.getChildren().add(label);
            chatBox.getChildren().add(container);
            scrollToBottom();
        });
    }

    private void handleFileClick(String filename) {
        if (filename.startsWith("stego_") || filename.contains("(Có Mật thư)")) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Tệp tin bí mật");
            alert.setHeaderText("Phát hiện ảnh có thể chứa mật thư!");
            alert.setContentText("Bạn muốn Tải ảnh gốc hay Giải mã mật thư?");
            ButtonType btnGiaiMa = new ButtonType("Giải Mã Mật Thư");
            ButtonType btnTaiVe = new ButtonType("Tải Ảnh Về");
            alert.getButtonTypes().setAll(btnGiaiMa, btnTaiVe, ButtonType.CANCEL);

            alert.showAndWait().ifPresent(type -> {
                if (type == btnGiaiMa) {
                    clientSocket.sendMessage("DOWNLOAD_FILE:" + filename.replace(" (Có Mật thư)", ""));
                    chatBox.getProperties().put("DECODE_MODE", true);
                } else if (type == btnTaiVe) {
                    clientSocket.sendMessage("DOWNLOAD_FILE:" + filename.replace(" (Có Mật thư)", ""));
                }
            });
        } else {
            clientSocket.sendMessage("DOWNLOAD_FILE:" + filename);
        }
    }

    // =================================================================

    private void handleNewMessage(String messageData) {
        try {
            String[] parts = messageData.split(":", 4);
            int roomId = Integer.parseInt(parts[0]);

            if (roomId == currentRoomId && currentRoomId != -1) {
                int senderId = Integer.parseInt(parts[1]);
                if (senderId == clientSocket.getCurrentUserId()) return;

                String username = idToUserMap.getOrDefault(senderId, "User " + senderId);
                String msgContent = parts.length > 2 ? parts[2] : "";
                String type = parts.length > 3 ? parts[3] : "TEXT";

                if ("FILE".equals(type)) {
                    appendFileMessage(username, msgContent, false);
                } else {
                    appendMessage(username, msgContent, false);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing message: " + e.getMessage());
        }
    }

    private void updateHistory(String historyStr) {
        String[] msgs = historyStr.split(";");
        clearChat();
        for (String msg : msgs) {
            if (!msg.isEmpty()) {
                try {
                    String[] parts = msg.split(":", 3);
                    int senderId = Integer.parseInt(parts[0]);
                    boolean isMine = (senderId == clientSocket.getCurrentUserId());
                    String username = idToUserMap.getOrDefault(senderId, "User " + senderId);
                    String msgContent = parts.length > 1 ? parts[1] : "";
                    String type = parts.length > 2 ? parts[2] : "TEXT";

                    if ("FILE".equals(type)) {
                        appendFileMessage(username, msgContent, isMine);
                    } else {
                        appendMessage(username, msgContent, isMine);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing history: " + e.getMessage());
                }
            }
        }
    }

    @FXML
    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (msg.isEmpty()) return;

        if (currentRoomId == -1) {
            appendSystemMessage("⚠️ Đang kết nối máy chủ, vui lòng chờ trong giây lát...");
            return;
        }

        String currentUsername = idToUserMap.getOrDefault(clientSocket.getCurrentUserId(), "Bạn");
        appendMessage(currentUsername, msg, true);

        clientSocket.sendMessage("SEND_MESSAGE:" + currentRoomId + ":" + msg + ":TEXT");
        messageField.clear();
    }

    @FXML
    private void sendFile() {
        if (currentRoomId == -1) {
            showAlert("Lỗi", "Vui lòng chọn phòng chat trước hoặc chờ kết nối.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file để gửi");
        File file = fileChooser.showOpenDialog(new Stage());

        if (file != null && file.exists()) {
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String base64Data = Base64.getEncoder().encodeToString(fileBytes);
                base64Data = base64Data.replace("\n", "").replace("\r", "");

                clientSocket.sendMessage("UPLOAD_FILE:" + currentRoomId + ":" + file.getName() + ":" + base64Data);
                appendFileMessage("Bạn", file.getName(), true);

            } catch (IOException e) {
                showAlert("Lỗi", "Không thể đọc file để gửi.");
            }
        }
    }

    private void handleNewPrivateRoom(String response) {
        try {
            String[] parts = response.split(":");
            if (parts.length > 2) {
                pendingRoomId = Integer.parseInt(parts[2]);
                if (pendingTargetUser == null && parts.length > 3) {
                    pendingTargetUser = parts[3];
                }
                loadRooms();
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing private room: " + e.getMessage());
        }
    }

    private void loadRooms() {
        clientSocket.sendMessage("GET_ROOMS");
    }

    private void updateRoomList(String roomsStr) {
        roomList.getItems().clear();
        roomMap.clear();

        String[] rooms = roomsStr.split(";");
        for (String room : rooms) {
            if (!room.isEmpty()) {
                try {
                    String[] parts = room.split(",");
                    int id = Integer.parseInt(parts[0]);
                    String name = parts.length > 1 && parts[1] != null ? parts[1] : "Private Chat";
                    String type = parts.length > 2 ? parts[2] : "PRIVATE";

                    String display = name + " (" + type + ")";
                    roomList.getItems().add(display);
                    roomMap.put(display, id);
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing room: " + e.getMessage());
                }
            }
        }
        setupRoomListListener();
        handlePendingRoom();
    }

    private void setupRoomListListener() {
        roomList.setOnMouseClicked(event -> {
            String selected = roomList.getSelectionModel().getSelectedItem();
            if (selected != null && roomMap.containsKey(selected)) {
                selectRoom(roomMap.get(selected));
            }
        });
    }

    private void selectRoom(int roomId) {
        currentRoomId = roomId;
        clearChat();
        clientSocket.sendMessage("GET_HISTORY:" + currentRoomId);
        messageField.requestFocus();
    }

    private void handlePendingRoom() {
        if (pendingRoomId != -1) {
            boolean found = false;
            for (Map.Entry<String, Integer> entry : roomMap.entrySet()) {
                if (entry.getValue() == pendingRoomId) {
                    String realKey = entry.getKey();
                    roomList.getSelectionModel().select(realKey);
                    roomList.scrollTo(realKey);
                    currentRoomId = pendingRoomId;
                    clientSocket.sendMessage("GET_HISTORY:" + currentRoomId);

                    appendSystemMessage("✅ Đã kết nối thành công!");

                    String nameUser = (pendingTargetUser != null) ? pendingTargetUser : "người dùng";
                    Platform.runLater(() -> showAlert("Thành công", "Đã kết nối với " + nameUser + "!"));

                    found = true;
                    break;
                }
            }
            pendingRoomId = -1;
            if (found) pendingTargetUser = null;
        }
    }

    private void updateAllUsersList(String usersStr) {
        allUsersList.getItems().clear();
        userMap.clear();
        idToUserMap.clear();

        String[] users = usersStr.split(";");
        int myId = clientSocket.getCurrentUserId();

        for (String user : users) {
            if (!user.isEmpty()) {
                try {
                    String[] parts = user.split(",");
                    int id = Integer.parseInt(parts[0]);
                    String username = parts.length > 1 ? parts[1] : "Unknown";

                    idToUserMap.put(id, username);
                    if (id == myId) continue;

                    allUsersList.getItems().add(username);
                    userMap.put(username, id);
                } catch (NumberFormatException e) {}
            }
        }
        setupUserListListener();
    }

    private void setupUserListListener() {
        allUsersList.setOnMouseClicked(event -> {
            String selectedUser = allUsersList.getSelectionModel().getSelectedItem();
            if (selectedUser != null) {
                startPrivateChat(selectedUser);
            }
        });
    }

    private void startPrivateChat(String targetUsername) {
        String displayKey = targetUsername + " (PRIVATE)";
        if (roomMap.containsKey(displayKey)) {
            int roomId = roomMap.get(displayKey);
            roomList.getSelectionModel().select(displayKey);
            roomList.scrollTo(displayKey);
            selectRoom(roomId);
            return;
        }
        if (!roomList.getItems().contains(displayKey)) {
            roomList.getItems().add(displayKey);
        }
        roomList.getSelectionModel().select(displayKey);
        roomList.scrollTo(displayKey);

        clearChat();
        appendSystemMessage("⏳ Đang kết nối với " + targetUsername + "...");
        messageField.requestFocus();

        currentRoomId = -1;
        pendingTargetUser = targetUsername;
        clientSocket.sendMessage("START_PRIVATE_CHAT:" + targetUsername);
    }

    private void updateAllRoomsList(String roomsStr) {
        allRoomsList.getItems().clear();
        allRoomMap.clear();

        String[] rooms = roomsStr.split(";");
        for (String room : rooms) {
            if (!room.isEmpty()) {
                try {
                    String[] parts = room.split(",");
                    int id = Integer.parseInt(parts[0]);
                    String name = parts.length > 1 ? parts[1] : "Unknown";
                    String type = parts.length > 2 ? parts[2] : "PUBLIC";
                    String display = name + " (" + type + ")";

                    allRoomsList.getItems().add(display);
                    allRoomMap.put(display, id);
                } catch (NumberFormatException e) {}
            }
        }
        setupAllRoomsListListener();
    }

    private void setupAllRoomsListListener() {
        allRoomsList.setOnMouseClicked(event -> {
            String selected = allRoomsList.getSelectionModel().getSelectedItem();
            if (selected != null && allRoomMap.containsKey(selected)) {
                joinRoom(allRoomMap.get(selected), selected, selected.replace(" (PUBLIC)", ""));
            }
        });
    }

    private void joinRoom(int roomId, String displayKey, String simpleName) {
        for (Map.Entry<String, Integer> entry : roomMap.entrySet()) {
            if (entry.getValue() == roomId) {
                String existingKey = entry.getKey();
                roomList.getSelectionModel().select(existingKey);
                roomList.scrollTo(existingKey);
                selectRoom(roomId);
                return;
            }
        }
        roomList.getItems().add(displayKey);
        roomMap.put(displayKey, roomId);
        roomList.getSelectionModel().select(displayKey);
        roomList.scrollTo(displayKey);

        selectRoom(roomId);
        appendSystemMessage("🔔 Hệ thống: Bạn đã tham gia nhóm " + simpleName);
        clientSocket.sendMessage("JOIN_ROOM:" + roomId);
    }

    private void openVideoCallWindow(int roomId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/view/VideoCallView.fxml"));
            Parent root = loader.load();
            VideoCallController videoController = loader.getController();
            int myId = clientSocket.getCurrentUserId();
            videoController.setCallInfo(roomId, myId);
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle("Video Call - Room " + roomId);
            stage.show();
            stage.setOnCloseRequest(e -> videoController.endCall());
        } catch (IOException e) {
            showAlert("Lỗi", "Không thể mở cửa sổ video call.");
        }
    }

    @FXML
    private void startVideoCall() {
        if (currentRoomId == -1) {
            showAlert("Lỗi", "Vui lòng chọn phòng chat trước.");
            return;
        }
        clientSocket.sendMessage("VIDEOCALL:" + currentRoomId);
        openVideoCallWindow(currentRoomId);
    }

    private void handleIncomingCall(String response) {
        try {
            String[] parts = response.split(":");
            int roomId = Integer.parseInt(parts[1]);
            String callerInfo = parts.length > 2 ? parts[2] : "Ai đó";
            try {
                int senderId = Integer.parseInt(callerInfo);
                if (senderId == clientSocket.getCurrentUserId()) return;
                callerInfo = idToUserMap.getOrDefault(senderId, "User " + senderId);
            } catch (NumberFormatException e) {}

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Cuộc gọi đến");
            alert.setHeaderText("Cuộc gọi video từ: " + callerInfo);
            alert.setContentText("Bạn có muốn chấp nhận cuộc gọi không?");
            ButtonType buttonTypeAccept = new ButtonType("Nghe");
            ButtonType buttonTypeDecline = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(buttonTypeAccept, buttonTypeDecline);

            alert.showAndWait().ifPresent(type -> {
                if (type == buttonTypeAccept) {
                    openVideoCallWindow(roomId);
                }
            });
        } catch (Exception e) {}
    }

    @FXML
    private void openAIChat() {
        Platform.runLater(() -> {
            try {
                if (aiStage == null || !aiStage.isShowing()) {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/view/AIChatView.fxml"));
                    Parent root = loader.load();
                    aiChatController = loader.getController();
                    aiChatController.setClientSocket(this.clientSocket);
                    aiStage = new Stage();
                    aiStage.setScene(new Scene(root));
                    aiStage.setTitle("Trợ lý ảo");
                    aiStage.setOnHidden(e -> aiStage = null);
                    aiStage.show();
                } else {
                    aiStage.toFront();
                }
            } catch (IOException e) {
                showAlert("Lỗi", "Không thể mở cửa sổ AI.");
            }
        });
    }

    @FXML
    private void sendSecretImage() {
        if (currentRoomId == -1) {
            showAlert("Lỗi", "Vui lòng chọn phòng chat trước.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh để giấu tin");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg"));
        File imgFile = fileChooser.showOpenDialog(new Stage());
        if (imgFile == null) return;

        TextInputDialog textDialog = new TextInputDialog();
        textDialog.setTitle("Nhập Mật Thư");
        textDialog.setHeaderText("Tin nhắn này sẽ được giấu vào trong ảnh.");
        textDialog.setContentText("Nhập nội dung mật:");
        String secretText = textDialog.showAndWait().orElse("");
        if (secretText.isEmpty()) return;

        TextInputDialog passDialog = new TextInputDialog();
        passDialog.setTitle("Bảo mật ảnh");
        passDialog.setHeaderText("Cần mật khẩu để người nhận mở được ảnh này.");
        passDialog.setContentText("Nhập mật khẩu (Key):");
        String password = passDialog.showAndWait().orElse("");
        if (password.isEmpty()) return;

        try {
            File stegoImage = new File("stego_" + System.currentTimeMillis() + ".png");
            StegoUtil.hideTextInImage(imgFile, secretText, password, stegoImage);

            byte[] fileBytes = Files.readAllBytes(stegoImage.toPath());
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            base64Data = base64Data.replace("\n", "").replace("\r", "");

            clientSocket.sendMessage("UPLOAD_FILE:" + currentRoomId + ":" + stegoImage.getName() + ":" + base64Data);

            appendFileMessage("Bạn", stegoImage.getName() + " (Có Mật thư)", true);

            stegoImage.delete();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi", "Không thể tạo ảnh mật thư.");
        }
    }

    private void handleIncomingFile(String data) {
        String[] parts = data.split(":", 2);
        if (parts.length < 2) return;

        String filename = parts[0];
        String base64Data = parts[1];

        Platform.runLater(() -> {
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(base64Data);

                Object isDecodeMode = chatBox.getProperties().get("DECODE_MODE");
                if (isDecodeMode != null && (boolean) isDecodeMode) {
                    chatBox.getProperties().remove("DECODE_MODE");

                    TextInputDialog passDialog = new TextInputDialog();
                    passDialog.setTitle("Giải mã");
                    passDialog.setHeaderText("Nhập mật khẩu để xem tin nhắn bí mật");
                    String pass = passDialog.showAndWait().orElse("");

                    if (!pass.isEmpty()) {
                        File tempFile = File.createTempFile("temp_stego", ".png");
                        Files.write(tempFile.toPath(), decodedBytes);

                        try {
                            String secret = StegoUtil.extractTextFromImage(tempFile, pass);
                            if (secret == null) {
                                showAlert("Thất bại", "Mật khẩu sai hoặc ảnh không chứa mật thư!");
                            } else {
                                showAlert("✅ Nội Dung Mật Thư", "Tin nhắn: " + secret);
                            }
                        } catch (Exception ex) {
                            showAlert("Thất bại", "Mật khẩu sai hoặc file bị hỏng.");
                        }
                        tempFile.delete();
                    }
                    return;
                }

                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Lưu file tải về");
                fileChooser.setInitialFileName(filename);
                File file = fileChooser.showSaveDialog(new Stage());
                if (file != null) {
                    Files.write(file.toPath(), decodedBytes);
                    showAlert("Thành công", "Đã tải xong file: " + file.getName());
                }
            } catch (Exception e) {
                showAlert("Lỗi", "Không thể xử lý file.");
            }
        });
    }

    @FXML
    private void speechToText() {
        try {
            audioService.initModel();
            messageField.setText("🎤 Đang nghe... Hãy nói đi...");
            messageField.setDisable(true);

            audioService.startListening(
                    (text) -> Platform.runLater(() -> {
                        messageField.setText(text);
                        messageField.setDisable(false);
                    }),
                    (error) -> Platform.runLater(() -> {
                        showAlert("Lỗi", "Lỗi Mic: " + error.getMessage());
                        messageField.setDisable(false);
                    })
            );
        } catch (Exception e) {
            showAlert("Lỗi", "Chưa tải dữ liệu ngôn ngữ.");
        }
    }

    private void handleAIResponse(String content) {
        Platform.runLater(() -> {
            if (aiStage == null || !aiStage.isShowing()) {
                openAIChat();
            }
            if (aiChatController != null) {
                aiChatController.addAIResponse(content);
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}