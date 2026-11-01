package com.fintechassist.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {
    public record ChatRequest(String chatId, @NotBlank @Size(max = 2000) String message) {}
    public record ChatSummary(String id, String title, Instant updatedAt) {}
    public record MessageView(String role, String content, Instant createdAt) {}

    private final ChatService chatService;
    private final ChatRepository chats;
    private final ChatMessageRepository messages;

    public ChatController(ChatService chatService, ChatRepository chats, ChatMessageRepository messages) {
        this.chatService = chatService;
        this.chats = chats;
        this.messages = messages;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@AuthenticationPrincipal JwtUser user, @Valid @RequestBody ChatRequest req) {
        return chatService.stream(user, req.chatId(), req.message());
    }

    @GetMapping("/chats")
    public List<ChatSummary> list(@AuthenticationPrincipal JwtUser user) {
        return chats.findByUserIdOrderByUpdatedAtDesc(user.userId()).stream()
                .map(c -> new ChatSummary(c.getId(), c.getTitle(), c.getUpdatedAt())).toList();
    }

    @GetMapping("/chats/{id}/messages")
    public List<MessageView> history(@AuthenticationPrincipal JwtUser user, @PathVariable String id) {
        requireOwned(user, id);
        return messages.findByChatIdOrderByIdAsc(id).stream()
                .map(m -> new MessageView(m.getRole(), m.getContent(), m.getCreatedAt())).toList();
    }

    @DeleteMapping("/chats/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@AuthenticationPrincipal JwtUser user, @PathVariable String id) {
        requireOwned(user, id);
        messages.deleteByChatId(id);
        chats.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void requireOwned(JwtUser user, String chatId) {
        chats.findByIdAndUserId(chatId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));
    }
}
