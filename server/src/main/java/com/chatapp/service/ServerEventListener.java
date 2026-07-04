package com.chatapp.service;

import com.chatapp.model.ClientTableModel;
import com.chatapp.model.MessageTableModel;

import java.util.List;

/**
 * Interface callback để ServerAdminService báo sự kiện về cho Controller (View-layer),
 * tương tự vai trò của ChatEventListener bên phía client.
 *
 * Các phương thức này có thể được gọi từ background thread (I/O, DB, socket...),
 * nên phía Controller cần tự bọc Platform.runLater khi cập nhật UI.
 */
public interface ServerEventListener {
    void onLog(String logChunk);

    void onServerStarted(String ip, int port);

    void onServerStopped();

    void onClientListLoaded(List<ClientTableModel> clients);

    void onMessageListLoaded(List<MessageTableModel> messages);
}
