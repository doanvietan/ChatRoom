package com.chatapp.service;
import com.chatapp.model.dao.UserDAO;
import com.chatapp.model.dao.ChatRoomDAO;
import com.chatapp.model.entity.ChatRoom;
import com.chatapp.model.entity.Message;
import com.chatapp.model.entity.User;
import com.chatapp.util.DBConnection;
import com.chatapp.util.Protocol;
import org.checkerframework.checker.units.qual.A;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private User user;
    private static Map<Integer, ClientHandler> onlineUsers = new HashMap<>();
    private UserDAO userDAO = new UserDAO();
    private ChatRoomDAO roomDAO = new ChatRoomDAO();
    private ChatService chatService = new ChatService();
    private AuthService authService = new AuthService();

    private static final String UPLOAD_DIR = "server_files/";

    // Thêm khối static này để tự động tạo thư mục nếu chưa có
    static {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            String input;
            while ((input = in.readLine()) != null) {
                String[] parts = input.split(":", 5);
                String command = parts[0];
                switch (command) {
                    case Protocol.LOGIN:
                        handleLogin(parts[1], parts[2]);
                        break;
                    case Protocol.REGISTER:
                        handleRegister(parts[1], parts[2], parts[3]);
                        break;
                    case Protocol.START_PRIVATE_CHAT:
                        handleStartPrivateChat(parts[1]); // parts[1] = username của người kia
                        break;
                    case Protocol.GET_ALL_USERS:
                        handleGetAllUsers();
                        break;
                    case Protocol.GET_ALL_ROOMS:
                        handleGetAllRooms();
                        break;
                    case Protocol.JOIN_ROOM:
                        handleJoinRoom(Integer.parseInt(parts[1]));
                        break;
                    case Protocol.GET_ROOMS:
                        handleGetRooms();
                        break;
                    case Protocol.SEND_MESSAGE:
                        handleMessage(Integer.parseInt(parts[1]), user.getId(), parts[2], "TEXT"); // roomId, content, type
                        break;
                    case Protocol.GET_HISTORY:
                        handleGetHistory(Integer.parseInt(parts[1]));
                        break;
                    case "CREATE_GROUP":
                        handleCreateGroup(parts[1]);
                        break;
                    case "UPLOAD_FILE":
                        // Client gửi: UPLOAD_FILE:roomId:filename:base64data
                        handleFileUpload(Integer.parseInt(parts[1]), parts[2], parts[3]);
                        break;
                    case "DOWNLOAD_FILE":
                        // Client gửi yêu cầu tải: DOWNLOAD_FILE:filename
                        handleFileDownload(parts[1]);
                        break;
                    // Trong ClientHandler.java trên Server
                    case "VIDEOCALL":
                        // Client gửi: "VIDEOCALL:RoomID"
                        int vidRoomId = Integer.parseInt(parts[1]);

                        // Server tạo tin nhắn: "VIDEOCALL:RoomID:SenderID"
                        String callMsg = "VIDEOCALL:" + vidRoomId + ":" + user.getId();

                        // Broadcast cho các thành viên khác trong phòng đó
                        List<Integer> members = roomDAO.getRoomMembers(vidRoomId);
                        for (int memberId : members) {
                            if (memberId != user.getId()) { // Không gửi lại cho chính người gọi
                                ClientHandler target = onlineUsers.get(memberId);
                                if (target != null) {
                                    target.out.println(callMsg);
                                }
                            }
                        }
                        break;
                    case "AI":
                        // Lấy câu hỏi từ client gửi lên (dạng: AI:Câu hỏi của tôi)
                        String query = "";
                        if (parts.length > 1) {
                            // Cắt bỏ chữ "AI:" ở đầu để lấy nội dung
                            query = input.substring(3);
                        }

                        final String finalQuery = query;

                        // Chạy luồng riêng để hỏi AI
                        new Thread(() -> {
                            AIService aiService = new AIService();
                            String answer = aiService.getAIResponse(finalQuery);

                            // Xử lý xuống dòng: Protocol socket dùng dòng mới (\n) để ngắt tin nhắn.
                            // Nếu câu trả lời AI có \n, socket sẽ tưởng là nhiều tin nhắn.
                            // Ta đổi \n thành \\n để gửi đi an toàn.
                            String safeAnswer = answer.replace("\n", "\\n");

                            // Gửi về client với prefix "AI_RESPONSE:"
                            out.println("AI_RESPONSE:" + safeAnswer);
                        }).start();
                        break;

                }
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        } finally {
            // Cleanup, remove from onlineUsers
            if (user != null) {
                onlineUsers.remove(user.getId());
            }
        }
    }

    private void handleFileUpload(int roomId, String filename, String base64Data) throws SQLException {
        try {
            // 1. Giải mã Base64 thành mảng byte
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            // 2. Lưu file vào thư mục của Server
            File dest = new File(UPLOAD_DIR + filename);
            Files.write(dest.toPath(), fileBytes);

            // 3. Thông báo cho mọi người trong phòng biết có file mới (dùng lại hàm handleMessage)
            handleMessage(roomId, user.getId(), filename, "FILE");
        } catch (IOException e) {
            System.err.println("Lỗi lưu file trên server: " + e.getMessage());
        }
    }

    private void handleFileDownload(String filename) {
        try {
            File file = new File(UPLOAD_DIR + filename);
            if (file.exists()) {
                // Đọc file và mã hóa thành Base64 gửi về Client
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String base64Data = Base64.getEncoder().encodeToString(fileBytes);

                // Xóa dấu \n do Base64 sinh ra (nếu có) để an toàn qua Socket
                base64Data = base64Data.replace("\n", "").replace("\r", "");

                out.println("FILE_DATA:" + filename + ":" + base64Data);
            } else {
                out.println("ERROR:File không tồn tại trên server.");
            }
        } catch (IOException e) {
            System.err.println("Lỗi gửi file cho client: " + e.getMessage());
        }
    }
    private void handleLogin(String username, String password) throws SQLException {
        user = authService.login(username, password);
        if (user != null) {
            onlineUsers.put(user.getId(), this);
            out.println("OK:Login successful:" + user.getId());
        } else {
            out.println("ERROR:Invalid credentials");
        }
    }

    private void handleRegister(String username, String password, String email) throws SQLException {
        if (userDAO.getUserByUsername(username) != null) {
            out.println("ERROR:Username exists");
            return;
        }
        User newUser = new User(username, password, email);
        authService.register(newUser);
        out.println("OK:Registered");
    }


    private void handleStartPrivateChat(String targetUsername) throws SQLException {
        User targetUser = userDAO.getUserByUsername(targetUsername);
        if (targetUser == null) {
            return; // Hoặc báo lỗi nhẹ
        }

        // Lấy ID phòng (cũ hoặc mới tạo)
        int roomId = roomDAO.getOrCreatePrivateRoom(user.getId(), targetUser.getId());

        // Gửi về Client: "OK:PRIVATE_CHAT:10:targetUsername"
        // Client sẽ tự biết chuyển màn hình mà không cần hỏi user
        out.println("OK:PRIVATE_CHAT:" + roomId + ":" + targetUsername);
    }

    private void handleCreateGroup(String name) throws SQLException {
        ChatRoom room = new ChatRoom();
        room.setName(name);
        room.setType("GROUP");
        room.setCreatorId(user.getId());
        int roomId = roomDAO.createRoom(room);
        roomDAO.addMember(roomId, user.getId());
        out.println("OK:CREATED:" + roomId);
    }

    private void handleGetAllUsers() throws SQLException {
        List<User> users = userDAO.getAllUsers();
        StringBuilder sb = new StringBuilder("ALL_USERS:");
        for (User u : users) {
            sb.append(u.getId()).append(",").append(u.getUsername()).append(";");
        }
        out.println(sb.toString());
    }

    private void handleGetAllRooms() throws SQLException {
        List<ChatRoom> rooms = roomDAO.getAllRooms();
        StringBuilder sb = new StringBuilder("ALL_ROOMS:");
        for (ChatRoom room : rooms) {
            sb.append(room.getId()).append(",").append(room.getName()).append(",").append(room.getType()).append(";");
        }
        out.println(sb.toString());
    }

    private void handleJoinRoom(int roomId) throws SQLException {
        chatService.joinRoom(roomId, user.getId());
        out.println("OK:JOINED:" + roomId);
    }

    private void handleGetRooms() throws SQLException {
        List<ChatRoom> rooms = chatService.getUserRooms(user.getId());
        StringBuilder sb = new StringBuilder("ROOMS:");
        for (ChatRoom room : rooms) {
            String roomName = room.getName();
            if ("PRIVATE".equals(room.getType()) && roomName == null) {
                // Sửa mới: Lấy username của người kia cho display
                List<Integer> members = roomDAO.getRoomMembers(room.getId());
                for (int memberId : members) {
                    if (memberId != user.getId()) {
                        User otherUser = userDAO.getUserById(memberId); // Giả sử bạn có method getUserById trong UserDAO
                        roomName = "Private with " + otherUser.getUsername();
                        break;
                    }
                }
            }
            sb.append(room.getId()).append(",").append(roomName).append(",").append(room.getType()).append(";");
        }
        out.println(sb.toString());
    }

    private void handleMessage(int roomId, int senderId, String content, String type) throws SQLException {
        Message msg = new Message();
        msg.setRoomId(roomId);
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setType(type);
        chatService.sendMessage(msg);
        // Lấy type room
        String roomType = getRoomType(roomId); // Method phụ, implement dưới
        String messageStr = "MESSAGE:" + roomId + ":" + senderId + ":" + content + ":" + type;
        if ("PRIVATE".equals(roomType)) {
            // Unicast: gửi đến người kia
            List<Integer> members = roomDAO.getRoomMembers(roomId);
            for (int memberId : members) {
                if (memberId != senderId) {
                    ClientHandler target = onlineUsers.get(memberId);
                    if (target != null) {
                        target.out.println(messageStr);
                    }
                }
            }
        } else if ("GROUP".equals(roomType)) {
            // Multicast: gửi đến tất cả members
            List<Integer> members = roomDAO.getRoomMembers(roomId);
            for (int memberId : members) {
                ClientHandler target = onlineUsers.get(memberId);
                if (target != null) {
                    target.out.println(messageStr);
                }
            }
        } else if ("PUBLIC".equals(roomType)) {
            // Broadcast: gửi đến tất cả online
            for (ClientHandler handler : onlineUsers.values()) {
                handler.out.println(messageStr);
            }
        }
    }

    private String getRoomType(int roomId) throws SQLException {
        String sql = "SELECT type FROM chat_rooms WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("type");
            }
        }
        return null;
    }

    private void handleGetHistory(int roomId) throws SQLException {
        List<Message> history = chatService.getHistory(roomId);
        StringBuilder sb = new StringBuilder("HISTORY:");
        for (Message msg : history) {
            // CẬP NHẬT: Thêm .append(msg.getType()) vào cuối
            sb.append(msg.getSenderId()).append(":")
                    .append(msg.getContent()).append(":")
                    .append(msg.getType()).append(";");
        }
        out.println(sb.toString());
    }
}