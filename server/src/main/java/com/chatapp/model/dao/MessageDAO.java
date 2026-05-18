package com.chatapp.model.dao;
import com.chatapp.model.entity.Message;
import com.chatapp.util.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    public void saveMessage(Message msg) throws SQLException {
        String sql = "INSERT INTO messages (room_id, sender_id, content, message_type) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, msg.getRoomId());
            ps.setInt(2, msg.getSenderId());
            ps.setString(3, msg.getContent());
            ps.setString(4, msg.getType());
            ps.executeUpdate();
        }
    }

    public List<Message> getMessagesForRoom(int roomId) throws SQLException {
        List<Message> messages = new ArrayList<>();
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
                messages.add(msg);
            }
        }
        return messages;
    }
}