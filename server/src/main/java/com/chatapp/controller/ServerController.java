package com.chatapp.controller;

import com.chatapp.model.ClientTableModel;
import com.chatapp.model.MessageTableModel;
import com.chatapp.service.ServerAdminService;
import com.chatapp.service.ServerEventListener;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Controller (View-Controller layer) cho ServerView.fxml.
 * Chỉ chịu trách nhiệm: đọc thao tác từ UI, gọi xuống ServerAdminService (Model/Service layer),
 * và cập nhật UI khi service báo sự kiện về qua ServerEventListener.
 * Không chứa logic nghiệp vụ (socket, SQL, mã hoá...).
 */
public class ServerController implements ServerEventListener {

    // ----- Terminal tab -----
    @FXML private TextArea consoleLog;
    @FXML private Button btnStart, btnStop, btnClearLog;
    @FXML private TextField txtServerIp, txtServerPort;
    @FXML private Label lblStatusDot, lblStatusText;

    // ----- Client tab -----
    @FXML private TableView<ClientTableModel> clientTable;
    @FXML private TableColumn<ClientTableModel, Number> colClientId;
    @FXML private TableColumn<ClientTableModel, String> colUsername, colEmail, colStatus, colLastSeen;
    @FXML private Label lblConnectedCount;
    @FXML private Tab tabClients;

    // ----- Message tab -----
    @FXML private TableView<MessageTableModel> messageTable;
    @FXML private TableColumn<MessageTableModel, Number> colMsgId, colRoom;
    @FXML private TableColumn<MessageTableModel, String> colSender, colContent, colType, colTime;
    @FXML private Label lblMessageCount;
    @FXML private PasswordField txtSecretKey;
    @FXML private Tab tabMessages;

    private ServerAdminService adminService;

    @FXML
    public void initialize() {
        adminService = new ServerAdminService(this);

        setupClientTable();
        setupMessageTable();

        txtSecretKey.textProperty().addListener((obs, oldVal, newVal) -> adminService.setSecretKey(newVal));
    }

    private void setupClientTable() {
        colClientId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colLastSeen.setCellValueFactory(new PropertyValueFactory<>("lastSeen"));
    }

    private void setupMessageTable() {
        colMsgId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colRoom.setCellValueFactory(new PropertyValueFactory<>("roomId"));
        colSender.setCellValueFactory(new PropertyValueFactory<>("sender"));
        colContent.setCellValueFactory(new PropertyValueFactory<>("content"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("time"));
        messageTable.setPlaceholder(new Label("Chưa có dữ liệu tin nhắn."));
    }

    // =====================================================================
    // UI EVENT HANDLERS (gọi xuống Service)
    // =====================================================================
    @FXML
    private void onStartServer() {
        btnStart.setDisable(true);
        btnStop.setDisable(false);
        adminService.startServer();
    }

    @FXML
    private void onStopServer() {
        adminService.stopServer();
    }

    @FXML
    private void onClearLog() {
        consoleLog.clear();
    }

    @FXML
    private void onRefreshClients() {
        adminService.reloadClientDataAsync();
    }

    @FXML
    private void onRefreshMessages() {
        adminService.reloadMessageDataAsync();
    }

    @FXML
    private void onDeleteSelectedMessage() {
        MessageTableModel selected = messageTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            adminService.deleteMessage(selected.getId());
        }
    }

    @FXML
    private void onClientsTabSelected() {
        if (tabClients.isSelected()) {
            adminService.reloadClientDataAsync();
        }
    }

    @FXML
    private void onMessagesTabSelected() {
        if (tabMessages.isSelected()) {
            adminService.reloadMessageDataAsync();
        }
    }

    /** Gọi khi cửa sổ đóng lại (App/Main nên gọi hàm này từ setOnCloseRequest). */
    public void shutdown() {
        adminService.stopServer();
    }

    // =====================================================================
    // ServerEventListener — Service gọi các hàm này để báo sự kiện về UI.
    // Các hàm này có thể được gọi từ background thread nên luôn bọc Platform.runLater.
    // =====================================================================
    @Override
    public void onLog(String logChunk) {
        Platform.runLater(() -> {
            consoleLog.appendText(logChunk);
            consoleLog.setScrollTop(Double.MAX_VALUE);
        });
    }

    @Override
    public void onServerStarted(String ip, int port) {
        Platform.runLater(() -> {
            txtServerIp.setText(ip);
            txtServerPort.setText(String.valueOf(port));
            lblStatusDot.setTextFill(Color.web("#16a34a"));
            lblStatusText.setText("Server đang hoạt động");
        });
    }

    @Override
    public void onServerStopped() {
        Platform.runLater(() -> {
            txtServerIp.setText("Offline");
            txtServerPort.setText("---");
            lblStatusDot.setTextFill(Color.web("#dc2626"));
            lblStatusText.setText("Server đang dừng");
            btnStart.setDisable(false);
            btnStop.setDisable(true);
        });
    }

    @Override
    public void onClientListLoaded(List<ClientTableModel> clients) {
        Platform.runLater(() -> {
            clientTable.setItems(FXCollections.observableArrayList(clients));
            lblConnectedCount.setText("Tổng cộng: " + clients.size() + " người dùng");
        });
    }

    @Override
    public void onMessageListLoaded(List<MessageTableModel> messages) {
        Platform.runLater(() -> {
            messageTable.setItems(FXCollections.observableArrayList(messages));
            lblMessageCount.setText("Đã tải " + messages.size() + " tin nhắn.");
        });
    }
}
