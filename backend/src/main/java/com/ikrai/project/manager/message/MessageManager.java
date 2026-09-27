package com.ikrai.project.manager.message;

import com.ikrai.project.common.enums.MessageType;

public interface MessageManager {

    void send(Long receiverId, MessageType type, String title, String content, Long borrowId);

    void sendToAdmins(MessageType type, String title, String content, Long borrowId);
}
