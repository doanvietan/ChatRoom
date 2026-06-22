package com.chatapp.model.dao;
import com.chatapp.model.entity.Message;
import com.chatapp.util.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    public void saveMessage(Message msg) throws SQLException {
        String sql = "INSERT INTO messages (room_id, sender_id, content, message_type) VALUES (?, ?, ?, ?)";
        // Thêm Statement.RETURN_GENERATED_KEYS ở đây
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, msg.getRoomId());
            ps.setInt(2, msg.getSenderId());
            ps.setString(3, msg.getContent());
            ps.setString(4, msg.getType());
            ps.executeUpdate();

            // Lấy ID vừa sinh tự động từ cơ sở dữ liệu và gán ngược lại vào đối tượng msg
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    msg.setId(generatedKeys.getInt(1));
                }
            }
        }
    }

    public List<Message> getMessagesForRoom(int roomId) throws SQLException {
        List<Message> messages = new ArrayList<>();
        // SỬA: Bỏ điều kiện AND is_active = 1 để lấy hết tất cả tin nhắn
        String sql = "SELECT * FROM messages WHERE room_id = ? ORDER BY sent_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Message msg = new Message();
                msg.setId(rs.getInt("id"));
                msg.setRoomId(rs.getInt("room_id"));
                msg.setSenderId(rs.getInt("sender_id"));
                msg.setContent(rs.getString("content"));
                msg.setType(rs.getString("message_type"));
                msg.setIsActive(rs.getInt("is_active")); // Thêm dòng này để lấy trạng thái xoá
                messages.add(msg);
            }
        }
        return messages;
    }

    // Phương thức mới: Cập nhật trạng thái tin nhắn thành đã xoá (is_active = 0)
    public boolean softDeleteMessage(int messageId) throws SQLException {
        String sql = "UPDATE messages SET is_active = 0 WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, messageId);
            int rowsAffected = ps.executeUpdate();
            // Trả về true nếu cập nhật thành công (có ít nhất 1 dòng bị ảnh hưởng)
            return rowsAffected > 0;
        }
    }
}