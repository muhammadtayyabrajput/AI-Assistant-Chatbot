package com.fintechassist.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, String> {
    List<Chat> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<Chat> findByIdAndUserId(String id, Long userId);
}
