package com.chatapp.service;

import com.chatapp.model.ClientTableModel;
import com.chatapp.model.MessageTableModel;
import com.chatapp.util.DBConnection;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Toàn bộ nghiệp vụ (Model/Service layer) của màn hình quản trị Server:
 * - Khởi động / dừng ServerSocket, quản lý pool xử lý client.
 * - Truy vấn CSDL để nạp danh sách client & tin nhắn.
 * - Giải mã nội dung tin nhắn theo khoá do Admin nhập.
 * - Xoá tin nhắn khỏi CSDL.
 *
 * Lớp này KHÔNG biết gì về JavaFX UI (Stage, Scene, Node...). Mọi kết quả được
 * báo về thông qua {@link ServerEventListener}, Controller sẽ là nơi cập nhật UI.
 */
public class ServerAdminService {

    private static final int SERVER_PORT = 12345;
    private static final int LOG_FLUSH_MS = 200;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private ExecutorService clientPool;
    private ServerSocket serverSocket;
    private Thread logFlusherThread;

    // Mật khẩu giải mã do Admin nhập trên giao diện
    private volatile String currentSecretKey = "";

    private final ServerEventListener listener;

    public ServerAdminService(ServerEventListener listener) {
        this.listener = listener;
        startLogFlusher();
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public void setSecretKey(String secretKey) {
        this.currentSecretKey = secretKey;
    }

    // =====================================================================
    // CORE SERVER METHODS
    // =====================================================================
    public void startServer() {
        if (isRunning.get()) return;
        isRunning.set(true);

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

            listener.onServerStarted(ip, port);

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

    public void stopServer() {
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
        listener.onServerStopped();
    }

    // =====================================================================
    // TRUY VẤN DANH SÁCH CLIENT
    // =====================================================================
    public void reloadClientDataAsync() {
        Thread t = new Thread(() -> {
            List<ClientTableModel> list = new ArrayList<>();
            String sql = "SELECT id, username, email, online_status, last_seen FROM users ORDER BY id";
            try (Connection conn = DBConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String username = rs.getString("username");
                    String email = rs.getString("email");
                    String status = rs.getBoolean("online_status") ? "🟢 Online" : "⚫ Offline";
                    String lastSeen = rs.getTimestamp("last_seen") != null
                            ? rs.getTimestamp("last_seen").toLocalDateTime().format(DATETIME_FMT)
                            : "—";
                    list.add(new ClientTableModel(id, username, email, status, lastSeen));
                }
            } catch (SQLException e) {
                log("[DB_ERROR] Lỗi tải dữ liệu người dùng: " + e.getMessage());
            }
            listener.onClientListLoaded(list);
        });
        t.setDaemon(true);
        t.start();
    }

    // =====================================================================
    // TRUY VẤN & GIẢI MÃ TIN NHẮN
    // =====================================================================
    public void reloadMessageDataAsync() {
        Thread t = new Thread(() -> {
            List<MessageTableModel> list = new ArrayList<>();
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
                            ? rs.getTimestamp("sent_at").toLocalDateTime().format(DATETIME_FMT)
                            : "—";

                    String decryptedContent = decryptMessageContent(rawContent);
                    list.add(new MessageTableModel(id, roomId, sender, decryptedContent, type, time));
                }
            } catch (SQLException e) {
                log("[DB_ERROR] Lỗi tải danh sách tin nhắn: " + e.getMessage());
            }
            listener.onMessageListLoaded(list);
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * Dịch ngược bằng thuật toán và secretKey Admin nhập trực tiếp trên UI
     * (khác với EncryptionUtil vì đây phục vụ mục đích tra cứu/kiểm tra của quản trị viên).
     */
    private String decryptMessageContent(String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) return "";

        String encryptedData = rawContent;
        if (rawContent.startsWith("ENC:")) {
            encryptedData = rawContent.substring(4);
        } else {
            return rawContent; // Không mã hoá -> trả về bản gốc
        }

        if (this.currentSecretKey == null || this.currentSecretKey.isEmpty()) {
            return "[🔒 Đã mã hóa] " + encryptedData;
        }

        try {
            byte[] keyBytes = this.currentSecretKey.getBytes(StandardCharsets.UTF_8);
            keyBytes = Arrays.copyOf(keyBytes, 16);

            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            return rawContent;
        } catch (Exception e) {
            return "[❌ Sai khóa giải mã] " + encryptedData;
        }
    }

    public void deleteMessage(long msgId) {
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
    // LOG
    // =====================================================================
    public void log(String message) {
        logQueue.offer("[" + LocalDateTime.now().format(TIME_FMT) + "] " + message);
    }

    private void startLogFlusher() {
        logFlusherThread = new Thread(() -> {
            while (true) {
                try {
                    String first = logQueue.take();
                    StringBuilder sb = new StringBuilder(first).append('\n');
                    String next;
                    while ((next = logQueue.poll()) != null) {
                        sb.append(next).append('\n');
                    }
                    listener.onLog(sb.toString());
                    Thread.sleep(LOG_FLUSH_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ServerLogFlusher");
        logFlusherThread.setDaemon(true);
        logFlusherThread.start();
    }
}
