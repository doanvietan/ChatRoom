package com.chatapp.service;

import com.chatapp.util.DBConnection;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerGUI extends Application {

    // =====================================================================
    // CONSTANTS & COLORS
    // =====================================================================
    private static final int SERVER_PORT = 12345;
    private static final int LOG_FLUSH_MS = 200;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String C_BG = "#f5f6fa";
    private static final String C_HEADER_BG = "#ffffff";
    private static final String C_BORDER = "#dde1e7";
    private static final String C_BAR_BG = "#ffffff";
    private static final String C_LABEL = "#4a5568";
    private static final String C_LABEL_BOLD = "#1a202c";
    private static final String C_FIELD_BG = "#eef2ff";
    private static final String C_FIELD_TEXT = "#3730a3";
    private static final String C_FIELD_BDR = "#c7d2fe";
    private static final String C_LOG_BG = "#fafafa";
    private static final String C_LOG_TEXT = "#1e293b";
    private static final String C_GREEN = "#16a34a";
    private static final String C_RED = "#dc2626";
    private static final String C_BTN_START = "#16a34a";
    private static final String C_BTN_STOP = "#dc2626";
    private static final String C_BTN_CLEAR = "#64748b";
    private static final String C_BTN_RELOAD = "#2563eb";

    // =====================================================================
    // STATE & SERVICES
    // =====================================================================
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private ExecutorService clientPool;
    private ServerSocket serverSocket;

    // Biến lưu trữ mật khẩu giải mã do Admin nhập trên giao diện
    private String currentSecretKey = "";

    // =====================================================================
    // UI COMPONENTS
    // =====================================================================
    private TextArea consoleLog;
    private Button btnStart, btnStop, btnClearLog;
    private TextField txtServerIp, txtServerPort;
    private Label lblStatusDot, lblStatusText;

    private TableView<ClientModel> clientTable;
    private Label lblConnectedCount;

    private TableView<MessageModel> messageTable;
    private Label lblMessageCount;
    private PasswordField txtSecretKey;

    // =====================================================================
    // APPLICATION ENTRY
    // =====================================================================
    @Override
    public void start(Stage stage) {
        stage.setTitle("ChatApp — Server Management");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + C_BG + ";");
        root.setTop(buildHeader());
        root.setCenter(buildTabPane());

        Scene scene = new Scene(root, 980, 680);
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setMinWidth(850);
        stage.setMinHeight(550);

        stage.setOnCloseRequest(e -> {
            stopServer();
            Platform.exit();
        });

        startLogFlusher();
        stage.show();
    }

    // =====================================================================
    // UI BUILDERS
    // =====================================================================
    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 20, 14, 20));
        header.setStyle("-fx-background-color: " + C_HEADER_BG + "; -fx-border-color: " + C_BORDER + "; -fx-border-width: 0 0 1 0;");

        Label icon = new Label("⚡");
        icon.setStyle("-fx-background-color: #eff6ff; -fx-text-fill: #2563eb; -fx-font-size: 18px; -fx-background-radius: 50; -fx-padding: 6 10;");

        VBox titles = new VBox(2);
        Label title = new Label("Server Management Panel");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        title.setTextFill(Color.web(C_LABEL_BOLD));

        Label subtitle = new Label("Hệ thống giám sát mạng, quản trị người dùng & bảo mật nội dung");
        subtitle.setFont(Font.font("Segoe UI", 12));
        subtitle.setTextFill(Color.web("#94a3b8"));

        titles.getChildren().addAll(title, subtitle);
        header.getChildren().addAll(icon, titles);
        return header;
    }

    private TabPane buildTabPane() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: " + C_BG + ";");

        Tab tabLog = new Tab("  💻  Terminal  ");
        tabLog.setContent(buildLogPane());

        Tab tabClients = new Tab("  👥  Danh sách Client  ");
        tabClients.setContent(buildClientPane());
        tabClients.setOnSelectionChanged(e -> {
            if (tabClients.isSelected()) reloadClientDataAsync();
        });

        Tab tabMessages = new Tab("  💬  Quản lý Tin Nhắn  ");
        tabMessages.setContent(buildMessagePane());
        tabMessages.setOnSelectionChanged(e -> {
            if (tabMessages.isSelected()) reloadMessageDataAsync();
        });

        tabs.getTabs().addAll(tabLog, tabClients, tabMessages);
        return tabs;
    }

    private BorderPane buildLogPane() {
        BorderPane pane = new BorderPane();
        pane.setStyle("-fx-background-color: " + C_BG + ";");

        HBox bar = new HBox(14);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 20, 12, 20));
        bar.setStyle("-fx-background-color: " + C_BAR_BG + "; -fx-border-color: " + C_BORDER + "; -fx-border-width: 0 0 1 0;");

        Label lblIp = infoLabel("IP Server:");
        txtServerIp = infoField("Offline", 155);
        Label lblPort = infoLabel("Port:");
        txtServerPort = infoField("---", 75);

        Separator sep = new Separator();
        sep.setOrientation(javafx.geometry.Orientation.VERTICAL);
        sep.setPrefHeight(28);

        lblStatusDot = new Label("●");
        lblStatusDot.setFont(Font.font("System", 14));
        lblStatusDot.setTextFill(Color.web(C_RED));

        lblStatusText = new Label("Server đang dừng");
        lblStatusText.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 12));
        lblStatusText.setTextFill(Color.web("#94a3b8"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusBox = new HBox(6, lblStatusDot, lblStatusText);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        bar.getChildren().addAll(lblIp, txtServerIp, lblPort, txtServerPort, spacer, sep, statusBox);
        pane.setTop(bar);

        consoleLog = new TextArea();
        consoleLog.setEditable(false);
        consoleLog.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px; -fx-control-inner-background: " + C_LOG_BG + ";");

        VBox wrapper = new VBox(consoleLog);
        VBox.setVgrow(wrapper, Priority.ALWAYS);
        VBox.setVgrow(consoleLog, Priority.ALWAYS);
        wrapper.setStyle("-fx-border-color: " + C_BORDER + "; -fx-border-radius: 6; -fx-padding: 2;");
        VBox.setMargin(wrapper, new Insets(12, 16, 4, 16));
        pane.setCenter(wrapper);

        btnStart = new Button("▶  Khởi động");
        styleButton(btnStart, C_BTN_START);
        btnStart.setOnAction(e -> startServer());

        btnStop = new Button("⏹  Dừng server");
        styleButton(btnStop, C_BTN_STOP);
        btnStop.setDisable(true);
        btnStop.setOnAction(e -> stopServer());

        btnClearLog = new Button("🗑  Xoá log");
        styleButton(btnClearLog, C_BTN_CLEAR);
        btnClearLog.setOnAction(e -> consoleLog.clear());

        HBox ctrlBar = new HBox(14, btnStart, btnStop, btnClearLog);
        ctrlBar.setAlignment(Pos.CENTER);
        ctrlBar.setPadding(new Insets(14, 20, 14, 20));
        pane.setBottom(ctrlBar);

        return pane;
    }

    private BorderPane buildClientPane() {
        clientTable = new TableView<>();
        clientTable.setStyle("-fx-font-size: 13px;");

        TableColumn<ClientModel, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> c.getValue().idProperty());
        colId.setPrefWidth(60);

        TableColumn<ClientModel, String> colUser = new TableColumn<>("Tài khoản");
        colUser.setCellValueFactory(c -> c.getValue().usernameProperty());
        colUser.setPrefWidth(160);

        TableColumn<ClientModel, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(c -> c.getValue().emailProperty());
        colEmail.setPrefWidth(240);

        TableColumn<ClientModel, String> colStatus = new TableColumn<>("Trạng thái");
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setPrefWidth(120);

        TableColumn<ClientModel, String> colSeen = new TableColumn<>("Hoạt động lần cuối");
        colSeen.setCellValueFactory(c -> c.getValue().lastSeenProperty());
        colSeen.setPrefWidth(200);

        clientTable.getColumns().addAll(colId, colUser, colEmail, colStatus, colSeen);

        lblConnectedCount = new Label("Đang tải...");
        Button btnRefresh = new Button("🔄 Làm mới");
        styleButton(btnRefresh, C_BTN_RELOAD);
        btnRefresh.setOnAction(e -> reloadClientDataAsync());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, lblConnectedCount, spacer, btnRefresh);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: " + C_HEADER_BG + "; -fx-border-color: " + C_BORDER + "; -fx-border-width: 0 0 1 0;");

        BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(clientTable);
        return pane;
    }

    private BorderPane buildMessagePane() {
        messageTable = new TableView<>();
        messageTable.setStyle("-fx-font-size: 13px;");
        messageTable.setPlaceholder(new Label("Chưa có dữ liệu tin nhắn."));

        TableColumn<MessageModel, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> c.getValue().idProperty());
        colId.setPrefWidth(60);

        TableColumn<MessageModel, Number> colRoom = new TableColumn<>("Phòng");
        colRoom.setCellValueFactory(c -> c.getValue().roomIdProperty());
        colRoom.setPrefWidth(70);

        TableColumn<MessageModel, String> colSender = new TableColumn<>("Người gửi");
        colSender.setCellValueFactory(c -> c.getValue().senderProperty());
        colSender.setPrefWidth(140);

        TableColumn<MessageModel, String> colContent = new TableColumn<>("Nội dung");
        colContent.setCellValueFactory(c -> c.getValue().contentProperty());
        colContent.setPrefWidth(350);

        TableColumn<MessageModel, String> colType = new TableColumn<>("Loại");
        colType.setCellValueFactory(c -> c.getValue().typeProperty());
        colType.setPrefWidth(80);

        TableColumn<MessageModel, String> colTime = new TableColumn<>("Thời gian gửi");
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colTime.setPrefWidth(160);

        messageTable.getColumns().addAll(colId, colRoom, colSender, colContent, colType, colTime);

        // Menu chuột phải để xoá
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("🗑 Xoá tin nhắn này khỏi CSDL");
        deleteItem.setOnAction(e -> {
            MessageModel selected = messageTable.getSelectionModel().getSelectedItem();
            if (selected != null) deleteMessageFromDB(selected.getId());
        });
        contextMenu.getItems().add(deleteItem);
        messageTable.setContextMenu(contextMenu);

        lblMessageCount = new Label("Trạng thái: ");

        // Ô NHẬP MẬT KHẨU GIẢI MÃ
        txtSecretKey = new PasswordField();
        txtSecretKey.setPromptText("Nhập khóa giải mã...");
        txtSecretKey.setPrefWidth(180);
        txtSecretKey.textProperty().addListener((obs, old, newVal) -> this.currentSecretKey = newVal);

        Button btnRefresh = new Button("🔓 Giải mã & Làm mới");
        styleButton(btnRefresh, C_BTN_RELOAD);
        btnRefresh.setOnAction(e -> reloadMessageDataAsync());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(10, lblMessageCount, spacer, new Label("Khóa AES:"), txtSecretKey, btnRefresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: " + C_HEADER_BG + "; -fx-border-color: " + C_BORDER + "; -fx-border-width: 0 0 1 0;");

        BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(messageTable);
        return pane;
    }

    // =====================================================================
    // CORE SERVER METHODS
    // =====================================================================
    private void startServer() {
        if (isRunning.get()) return;
        isRunning.set(true);
        btnStart.setDisable(true);
        btnStop.setDisable(false);

        clientPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "ClientHandler");
            t.setDaemon(true);
            return t;
        });

        Thread acceptThread = new Thread(this::runAcceptLoop, "ServerAcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void runAcceptLoop() {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                log("[DATABASE] Kết nối CSDL thành công.");
            }
        } catch (SQLException e) {
            log("[DATABASE] ⚠ Không kết nối được CSDL: " + e.getMessage());
        }

        try {
            clientPool.submit(new VideoRelayServer());
            log("[SERVICES] Video Relay Server đã khởi động.");
        } catch (Exception e) {
            log("[SERVICES] ⚠ Video Relay lỗi: " + e.getMessage());
        }

        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            String ip = java.net.InetAddress.getLocalHost().getHostAddress();
            int port = serverSocket.getLocalPort();

            Platform.runLater(() -> setServerOnlineUI(ip, port));

            log("=".repeat(50));
            log("🚀 SERVER ĐÃ KHỞI CHẠY");
            log("📍 IP   : " + ip);
            log("🔌 Port : " + port);
            log("=".repeat(50));

            while (isRunning.get()) {
                Socket client;
                try {
                    client = serverSocket.accept();
                } catch (IOException e) {
                    if (!isRunning.get()) break;
                    continue;
                }
                log("[NETWORK] 🟢 Client kết nối — IP: " + client.getInetAddress().getHostAddress());
                clientPool.submit(new ClientHandler(client));
                reloadClientDataAsync();
            }
        } catch (IOException e) {
            if (isRunning.get()) {
                log("[NETWORK] ✖ Lỗi socket: " + e.getMessage());
            }
        }
        log("[SYSTEM] Server đã dừng hoàn toàn.");
    }

    private void stopServer() {
        if (!isRunning.get()) return;
        isRunning.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log("[ERROR] Lỗi: " + e.getMessage());
        }
        if (clientPool != null) clientPool.shutdownNow();
        Platform.runLater(this::setServerOfflineUI);
    }

    // =====================================================================
    // LOGIC GIẢI MÃ TIN NHẮN THEO ĐÚNG EncryptionUtil
    // =====================================================================
    private void reloadMessageDataAsync() {
        Thread t = new Thread(() -> {
            ObservableList<MessageModel> list = FXCollections.observableArrayList();
            String sql = "SELECT m.id, m.room_id, u.username, m.content, m.message_type, m.sent_at " +
                    "FROM messages m JOIN users u ON m.sender_id = u.id " +
                    "ORDER BY m.sent_at DESC LIMIT 200";

            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    long id = rs.getLong("id");
                    int roomId = rs.getInt("room_id");
                    String sender = rs.getString("username");
                    String rawContent = rs.getString("content");
                    String type = rs.getString("message_type");
                    String time = rs.getTimestamp("sent_at") != null
                            ? rs.getTimestamp("sent_at").toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                            : "—";

                    // GỌI HÀM GIẢI MÃ
                    String decryptedContent = decryptMessageContent(rawContent);
                    list.add(new MessageModel(id, roomId, sender, decryptedContent, type, time));
                }
            } catch (SQLException e) {
                log("[DB_ERROR] Lỗi tải danh sách tin nhắn: " + e.getMessage());
            }

            Platform.runLater(() -> {
                messageTable.setItems(list);
                lblMessageCount.setText("Đã tải " + list.size() + " tin nhắn.");
            });
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * Dịch ngược bằng thuật toán và secretKey trực tiếp (giống EncryptionUtil)
     */
    private String decryptMessageContent(String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) return "";

        // Kiểm tra tiền tố ENC:
        String encryptedData = rawContent;
        if (rawContent.startsWith("ENC:")) {
            encryptedData = rawContent.substring(4); // Cắt bỏ "ENC:"
        } else {
            return rawContent; // Không mã hóa -> Trả về bản gốc
        }

        // Kiểm tra Admin đã nhập mật khẩu chưa
        if (this.currentSecretKey == null || this.currentSecretKey.isEmpty()) {
            return "[🔒 Đã mã hóa] " + encryptedData;
        }

        try {
            // Lấy bytes trực tiếp từ mật khẩu Admin nhập (không qua hàm băm SHA-256)
            byte[] keyBytes = this.currentSecretKey.getBytes(StandardCharsets.UTF_8);

            // Cắt hoặc bù padding để đảm bảo độ dài khóa đúng 16 byte chuẩn của AES
            // Nếu bạn nhập "ChatappSecretKey" nó sẽ giữ nguyên đúng 16 ký tự
            keyBytes = Arrays.copyOf(keyBytes, 16);

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            // Dữ liệu Base64 bị sai
            return rawContent;
        } catch (Exception e) {
            // Sai mật khẩu hoặc lỗi thuật toán
            return "[❌ Sai khóa giải mã] " + encryptedData;
        }
    }

    private void deleteMessageFromDB(long msgId) {
        String sql = "DELETE FROM messages WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, msgId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                log("[DATABASE] Quản trị viên đã xoá tin nhắn ID: " + msgId);
                reloadMessageDataAsync();
            }
        } catch (SQLException e) {
            log("[DB_ERROR] Lỗi khi xoá tin nhắn: " + e.getMessage());
        }
    }

    // =====================================================================
    // UTILS & MODELS
    // =====================================================================
    private void reloadClientDataAsync() {
        Thread t = new Thread(() -> {
            ObservableList<ClientModel> list = FXCollections.observableArrayList();
            String sql = "SELECT id, username, email, online_status, last_seen FROM users ORDER BY id";
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String username = rs.getString("username");
                    String email = rs.getString("email");
                    String status = rs.getBoolean("online_status") ? "🟢 Online" : "⚫ Offline";
                    String lastSeen = rs.getTimestamp("last_seen") != null ? rs.getTimestamp("last_seen").toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) : "—";
                    list.add(new ClientModel(id, username, email, status, lastSeen));
                }
            } catch (SQLException e) {
                log("[DB_ERROR] Lỗi tải dữ liệu người dùng: " + e.getMessage());
            }

            Platform.runLater(() -> {
                clientTable.setItems(list);
                lblConnectedCount.setText("Tổng cộng: " + list.size() + " người dùng");
            });
        });
        t.setDaemon(true);
        t.start();
    }

    private void log(String message) {
        logQueue.offer("[" + LocalDateTime.now().format(TIME_FMT) + "] " + message);
    }

    private void startLogFlusher() {
        Thread flusher = new Thread(() -> {
            while (true) {
                try {
                    String first = logQueue.take();
                    StringBuilder sb = new StringBuilder(first).append('\n');
                    String next;
                    while ((next = logQueue.poll()) != null) {
                        sb.append(next).append('\n');
                    }

                    Platform.runLater(() -> {
                        consoleLog.appendText(sb.toString());
                        consoleLog.setScrollTop(Double.MAX_VALUE);
                    });
                    Thread.sleep(LOG_FLUSH_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        flusher.setDaemon(true);
        flusher.start();
    }

    private void setServerOnlineUI(String ip, int port) {
        txtServerIp.setText(ip);
        txtServerPort.setText(String.valueOf(port));
        lblStatusDot.setTextFill(Color.web(C_GREEN));
        lblStatusText.setText("Server đang hoạt động");
    }

    private void setServerOfflineUI() {
        txtServerIp.setText("Offline");
        txtServerPort.setText("---");
        lblStatusDot.setTextFill(Color.web(C_RED));
        lblStatusText.setText("Server đang dừng");
        btnStart.setDisable(false);
        btnStop.setDisable(true);
    }

    private static Label infoLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        l.setTextFill(Color.web(C_LABEL));
        return l;
    }

    private static TextField infoField(String text, double width) {
        TextField tf = new TextField(text);
        tf.setEditable(false);
        tf.setPrefWidth(width);
        tf.setStyle("-fx-background-color: " + C_FIELD_BG + "; -fx-text-fill: " + C_FIELD_TEXT + "; -fx-alignment: center; -fx-font-weight: bold; -fx-background-radius: 8;");
        return tf;
    }

    private static void styleButton(Button btn, String hexColor) {
        String base = String.format("-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;", hexColor);
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(base + "-fx-opacity: 0.85;"));
        btn.setOnMouseExited(e -> btn.setStyle(base));
    }

    // =====================================================================
    // LỚP DATA MODEL (HIỂN THỊ DỮ LIỆU)
    // =====================================================================
    public static class ClientModel {
        private final SimpleIntegerProperty id;
        private final SimpleStringProperty username, email, status, lastSeen;

        public ClientModel(int i, String u, String e, String s, String l) {
            id = new SimpleIntegerProperty(i);
            username = new SimpleStringProperty(u);
            email = new SimpleStringProperty(e);
            status = new SimpleStringProperty(s);
            lastSeen = new SimpleStringProperty(l);
        }

        public SimpleIntegerProperty idProperty() { return id; }
        public SimpleStringProperty usernameProperty() { return username; }
        public SimpleStringProperty emailProperty() { return email; }
        public SimpleStringProperty statusProperty() { return status; }
        public SimpleStringProperty lastSeenProperty() { return lastSeen; }
    }

    public static class MessageModel {
        private final SimpleLongProperty id;
        private final SimpleIntegerProperty roomId;
        private final SimpleStringProperty sender, content, type, time;

        public MessageModel(long i, int r, String s, String c, String t, String tm) {
            id = new SimpleLongProperty(i);
            roomId = new SimpleIntegerProperty(r);
            sender = new SimpleStringProperty(s);
            content = new SimpleStringProperty(c);
            type = new SimpleStringProperty(t);
            time = new SimpleStringProperty(tm);
        }

        public SimpleLongProperty idProperty() { return id; }
        public SimpleIntegerProperty roomIdProperty() { return roomId; }
        public SimpleStringProperty senderProperty() { return sender; }
        public SimpleStringProperty contentProperty() { return content; }
        public SimpleStringProperty typeProperty() { return type; }
        public SimpleStringProperty timeProperty() { return time; }
        public long getId() { return id.get(); }
    }
}