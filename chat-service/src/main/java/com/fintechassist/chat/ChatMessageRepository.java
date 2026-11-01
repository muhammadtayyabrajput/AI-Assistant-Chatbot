package com.fintechassist.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatIdOrderByIdAsc(String chatId);
    List<ChatMessage> findTop10ByChatIdOrderByIdDesc(String chatId);
    void deleteByChatId(String chatId);
}
