package com.chatapp.model.dao;
import com.chatapp.model.entity.ChatRoom;
import com.chatapp.util.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatRoomDAO {
    public int createRoom(ChatRoom room) throws SQLException {
        String sql = "INSERT INTO chat_rooms (name, type, creator_id) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, room.getName());
            ps.setString(2, room.getType());
            ps.setInt(3, room.getCreatorId());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return -1;
    }

    public void addMember(int roomId, int userId) throws SQLException {
        String sql = "INSERT INTO room_members (room_id, user_id) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public int getOrCreatePrivateRoom(int userId1, int userId2) throws SQLException {
        // 1. Kiểm tra xem đã từng chat chưa
        String checkSql = "SELECT r.id FROM chat_rooms r " +
                "JOIN room_members m1 ON r.id = m1.room_id " +
                "JOIN room_members m2 ON r.id = m2.room_id " +
                "WHERE r.type = 'PRIVATE' AND m1.user_id = ? AND m2.user_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, userId1);
            ps.setInt(2, userId2);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id"); // Đã có, trả về ID luôn
            }
        }

        // 2. Chưa có thì tạo mới (Tên phòng để NULL hoặc string bất kỳ, vì hàm hiển thị ở trên đã lo việc đổi tên rồi)
        String createRoomSql = "INSERT INTO chat_rooms (type, creator_id) VALUES ('PRIVATE', ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(createRoomSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId1);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int roomId = rs.getInt(1);
                // Thêm 2 thành viên vào
                addMember(roomId, userId1);
                addMember(roomId, userId2);
                return roomId;
            }
        }
        return -1;
    }

    public List<ChatRoom> getRoomsForUser(int currentUserId) throws SQLException {
        List<ChatRoom> rooms = new ArrayList<>();

        // SQL NÂNG CAO:
        // Nếu là PRIVATE -> Lấy username của người kia làm tên hiển thị.
        // Nếu là GROUP/PUBLIC -> Lấy tên phòng bình thường.
        String sql = "SELECT r.id, r.type, " +
                "CASE " +
                "  WHEN r.type = 'PRIVATE' THEN " +
                "    (SELECT u.username FROM users u " +
                "     JOIN room_members rm ON u.id = rm.user_id " +
                "     WHERE rm.room_id = r.id AND u.id != ?) " +
                "  ELSE r.name " +
                "END AS display_name " +
                "FROM chat_rooms r " +
                "JOIN room_members m ON r.id = m.room_id " +
                "WHERE m.user_id = ? " +
                "ORDER BY r.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, currentUserId); // Tham số cho truy vấn con (để loại trừ bản thân)
            ps.setInt(2, currentUserId); // Tham số cho WHERE chính

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ChatRoom room = new ChatRoom();
                room.setId(rs.getInt("id"));
                room.setName(rs.getString("display_name")); // Tên hiển thị đã xử lý (Tên người kia)
                room.setType(rs.getString("type"));
                rooms.add(room);
            }
        }
        // Thêm PUBLIC rooms (vì broadcast, ai cũng có thể thấy)
        String publicSql = "SELECT * FROM chat_rooms WHERE type = 'PUBLIC'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(publicSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ChatRoom room = new ChatRoom();
                room.setId(rs.getInt("id"));
                room.setName(rs.getString("name"));
                room.setType(rs.getString("type"));
                room.setCreatedAt(rs.getTimestamp("created_at"));
                rooms.add(room);
            }
        }
        return rooms;
    }

    public List<Integer> getRoomMembers(int roomId) throws SQLException {
        List<Integer> members = new ArrayList<>();
        String sql = "SELECT user_id FROM room_members WHERE room_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                members.add(rs.getInt("user_id"));
            }
        }
        return members;
    }

    public List<ChatRoom> getAllRooms() throws SQLException {
        List<ChatRoom> rooms = new ArrayList<>();
        String sql = "SELECT id, name, type FROM chat_rooms WHERE type IN ('GROUP')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ChatRoom room = new ChatRoom();
                room.setId(rs.getInt("id"));
                room.setName(rs.getString("name"));
                room.setType(rs.getString("type"));
                rooms.add(room);
            }
        }
        return rooms;
    }
}