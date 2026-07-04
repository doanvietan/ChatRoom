package com.chatapp.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Model hiển thị 1 dòng trong bảng "Danh sách Client" trên ServerView.
 * Đây thuần tuý là Model dữ liệu cho UI (JavaFX Bean), không chứa logic nghiệp vụ.
 */
public class ClientTableModel {
    private final SimpleIntegerProperty id;
    private final SimpleStringProperty username;
    private final SimpleStringProperty email;
    private final SimpleStringProperty status;
    private final SimpleStringProperty lastSeen;

    public ClientTableModel(int id, String username, String email, String status, String lastSeen) {
        this.id = new SimpleIntegerProperty(id);
        this.username = new SimpleStringProperty(username);
        this.email = new SimpleStringProperty(email);
        this.status = new SimpleStringProperty(status);
        this.lastSeen = new SimpleStringProperty(lastSeen);
    }

    public int getId() { return id.get(); }
    public SimpleIntegerProperty idProperty() { return id; }

    public String getUsername() { return username.get(); }
    public SimpleStringProperty usernameProperty() { return username; }

    public String getEmail() { return email.get(); }
    public SimpleStringProperty emailProperty() { return email; }

    public String getStatus() { return status.get(); }
    public SimpleStringProperty statusProperty() { return status; }

    public String getLastSeen() { return lastSeen.get(); }
    public SimpleStringProperty lastSeenProperty() { return lastSeen; }
}
