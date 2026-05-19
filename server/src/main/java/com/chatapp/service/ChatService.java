package com.chatapp.service;
import com.chatapp.model.dao.MessageDAO;
import com.chatapp.model.dao.ChatRoomDAO;
import com.chatapp.model.entity.ChatRoom;
import com.chatapp.model.entity.Message;
import com.chatapp.util.EncryptionUtil;

import java.sql.SQLException;
import java.util.List;

public class ChatService {
    private MessageDAO messageDAO = new MessageDAO();
    private ChatRoomDAO roomDAO = new ChatRoomDAO();

    public void sendMessage(Message msg) throws SQLException {
        // 1. Lấy nội dung gốc
        String originalContent = msg.getContent();

        // 2. Mã hóa và set lại vào object Message
        msg.setContent(EncryptionUtil.encrypt(originalContent));

        // 3. Lưu xuống Database (DB sẽ lưu chuỗi đã mã hóa)
        messageDAO.saveMessage(msg);

        // 4. Trả lại nội dung gốc cho object Message
        // (Để ClientHandler nếu có dùng lại msg.getContent() thì vẫn là text bình thường)
        msg.setContent(originalContent);
    }

    public List<Message> getHistory(int roomId) throws SQLException {
        List<Message> history = messageDAO.getMessagesForRoom(roomId);

        // Lặp qua danh sách tin nhắn và giải mã nội dung
        for (Message msg : history) {
            String decryptedContent = EncryptionUtil.decrypt(msg.getContent());
            msg.setContent(decryptedContent);
        }

        return history;
    }

    public void joinRoom(int roomId, int userId) throws SQLException {
        roomDAO.addMember(roomId, userId);
    }

    public List<ChatRoom> getUserRooms(int userId) throws SQLException {
        return roomDAO.getRoomsForUser(userId);
    }

}