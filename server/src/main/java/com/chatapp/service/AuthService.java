package com.chatapp.service;

import com.chatapp.model.dao.UserDAO;
import com.chatapp.model.entity.User;
// import org.mindrot.jbcrypt.BCrypt; // <-- Bỏ dòng này đi vì không dùng nữa

import java.sql.SQLException;

public class AuthService {
    private UserDAO userDAO = new UserDAO();

    // Xử lý Đăng nhập
    public User login(String username, String password) throws SQLException {
        User user = userDAO.getUserByUsername(username);

        if (user != null && user.getPassword().equals(password)) {
            userDAO.updateOnlineStatus(user.getId(), true);
            return user;
        }
        return null;
    }

    // Xử lý Đăng ký
    public void register(User user) throws SQLException {
        userDAO.register(user);
    }
}