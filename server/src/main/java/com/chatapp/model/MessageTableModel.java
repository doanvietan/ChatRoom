package com.chatapp.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Model hiển thị 1 dòng trong bảng "Quản lý Tin Nhắn" trên ServerView.
 * Đây thuần tuý là Model dữ liệu cho UI (JavaFX Bean), không chứa logic nghiệp vụ.
 */
public class MessageTableModel {
    private final SimpleLongProperty id;
    private final SimpleIntegerProperty roomId;
    private final SimpleStringProperty sender;
    private final SimpleStringProperty content;
    private final SimpleStringProperty type;
    private final SimpleStringProperty time;

    public MessageTableModel(long id, int roomId, String sender, String content, String type, String time) {
        this.id = new SimpleLongProperty(id);
        this.roomId = new SimpleIntegerProperty(roomId);
        this.sender = new SimpleStringProperty(sender);
        this.content = new SimpleStringProperty(content);
        this.type = new SimpleStringProperty(type);
        this.time = new SimpleStringProperty(time);
    }

    public long getId() { return id.get(); }
    public SimpleLongProperty idProperty() { return id; }

    public int getRoomId() { return roomId.get(); }
    public SimpleIntegerProperty roomIdProperty() { return roomId; }

    public String getSender() { return sender.get(); }
    public SimpleStringProperty senderProperty() { return sender; }

    public String getContent() { return content.get(); }
    public SimpleStringProperty contentProperty() { return content; }

    public String getType() { return type.get(); }
    public SimpleStringProperty typeProperty() { return type; }

    public String getTime() { return time.get(); }
    public SimpleStringProperty timeProperty() { return time; }
}
