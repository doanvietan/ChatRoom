package com.chatapp.service;
import com.chatapp.model.dao.MessageDAO;
import com.chatapp.model.dao.ChatRoomDAO;
import com.chatapp.model.entity.ChatRoom;
import com.chatapp.model.entity.Message;
import java.sql.SQLException;
import java.util.List;

public class ChatService {
    private MessageDAO messageDAO = new MessageDAO();
    private ChatRoomDAO roomDAO = new ChatRoomDAO();

    public void sendMessage(Message msg) throws SQLException {
        messageDAO.saveMessage(msg);
    }

    public List<Message> getHistory(int roomId) throws SQLException {
        return messageDAO.getMessagesForRoom(roomId);
    }

    public void joinRoom(int roomId, int userId) throws SQLException {
        roomDAO.addMember(roomId, userId);
    }

    public List<ChatRoom> getUserRooms(int userId) throws SQLException {
        return roomDAO.getRoomsForUser(userId);
    }

}