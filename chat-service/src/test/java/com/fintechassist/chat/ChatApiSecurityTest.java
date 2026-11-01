package com.fintechassist.chat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import reactor.core.publisher.Flux;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "vector.datasource.url=jdbc:h2:mem:vec;DB_CLOSE_DELAY=-1",
        "vector.datasource.username=sa",
        "vector.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.ai.model.embedding=none",
        "jwt.secret=test-secret-test-secret-test-secret-123456",
        "chat.rate-limit-per-minute=3",
        "banking.url=http://localhost:1"
})
@AutoConfigureMockMvc
class ChatApiSecurityTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-123456";

    @Autowired MockMvc mvc;
    @Autowired ChatRepository chats;
    @Autowired ChatMessageRepository messages;

    @MockitoBean ChatService chatService;
    @MockitoBean PgVectorStore vectorStore;
    @MockitoBean EmbeddingModel embeddingModel;

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String bearer(long userId) {
        return "Bearer " + Jwts.builder().subject(String.valueOf(userId)).claim("name", "User " + userId)
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key(SECRET)).compact();
    }

    private static long freshUser() {
        return 1_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L);
    }

    @BeforeEach
    void stubChatService() {
        when(chatService.stream(any(), any(), any()))
                .thenReturn(Flux.just(ServerSentEvent.<String>builder("{}").event("done").build()));
    }

    private ResultActions send(long userId, String json) throws Exception {
        return mvc.perform(post("/api/chat").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void everyEndpointRequiresLogin() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chats")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/chats/abc/messages")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/chats/abc")).andExpect(status().isUnauthorized());
        verifyNoInteractions(chatService);
    }

    @Test
    void forgedExpiredAndGarbageTokensAreRejected() throws Exception {
        String forged = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key("another-secret-another-secret-another-1")).compact();
        String expired = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key(SECRET)).compact();
        for (String token : new String[]{forged, expired, "garbage", "a.b.c"}) {
            mvc.perform(get("/api/chats").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theUserIdPassedToTheChatServiceIsTheOneInTheToken() throws Exception {
        long me = freshUser();
        send(me, "{\"message\":\"hello\",\"userId\":999,\"user\":{\"id\":999}}").andExpect(status().isOk());

        ArgumentCaptor<JwtUser> captor = ArgumentCaptor.forClass(JwtUser.class);
        verify(chatService).stream(captor.capture(), any(), eq("hello"));
        assertEquals(me, captor.getValue().userId());
    }

    @Test
    void usersOnlySeeTheirOwnChats() throws Exception {
        long a = freshUser(), b = freshUser();
        chats.save(new Chat(a, "A's chat"));
        chats.save(new Chat(b, "B's chat"));

        mvc.perform(get("/api/chats").header("Authorization", bearer(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("A's chat"));
    }

    @Test
    void usersCannotReadAnotherUsersMessages() throws Exception {
        long a = freshUser(), b = freshUser();
        Chat chat = chats.save(new Chat(a, "private"));
        messages.save(new ChatMessage(chat.getId(), "user", "my secret question"));

        mvc.perform(get("/api/chats/" + chat.getId() + "/messages").header("Authorization", bearer(b)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/chats/" + chat.getId() + "/messages").header("Authorization", bearer(a)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].content").value("my secret question"));
    }

    @Test
    void usersCannotDeleteAnotherUsersChat() throws Exception {
        long a = freshUser(), b = freshUser();
        Chat chat = chats.save(new Chat(a, "keep me"));
        messages.save(new ChatMessage(chat.getId(), "user", "hello"));

        mvc.perform(delete("/api/chats/" + chat.getId()).header("Authorization", bearer(b)))
                .andExpect(status().isNotFound());

        assertTrue(chats.findById(chat.getId()).isPresent());
        assertEquals(1, messages.findByChatIdOrderByIdAsc(chat.getId()).size());
    }

    @Test
    void deletingYourOwnChatRemovesItAndItsMessages() throws Exception {
        long a = freshUser();
        Chat chat = chats.save(new Chat(a, "bye"));
        messages.save(new ChatMessage(chat.getId(), "user", "hello"));

        mvc.perform(delete("/api/chats/" + chat.getId()).header("Authorization", bearer(a)))
                .andExpect(status().isNoContent());

        assertTrue(chats.findById(chat.getId()).isEmpty());
        assertTrue(messages.findByChatIdOrderByIdAsc(chat.getId()).isEmpty());
    }

    @Test
    void unknownChatIdsAreNotFoundNotServerErrors() throws Exception {
        long a = freshUser();
        for (String id : new String[]{"does-not-exist", "' OR '1'='1", "..%2F..%2Fetc"}) {
            mvc.perform(get("/api/chats/" + id + "/messages").header("Authorization", bearer(a)))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Test
    void blankMissingAndOversizedMessagesAreRejected() throws Exception {
        long a = freshUser();
        send(a, "{\"message\":\"\"}").andExpect(status().isBadRequest());
        send(a, "{\"message\":\"   \"}").andExpect(status().isBadRequest());
        send(a, "{}").andExpect(status().isBadRequest());
        send(a, "{\"message\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
        send(a, "{\"message\":\"" + "x".repeat(2000) + "\"}").andExpect(status().isOk());
    }

    @Test
    void sendingTooManyMessagesIsRateLimitedPerUser() throws Exception {
        long spammer = freshUser(), other = freshUser();
        for (int i = 0; i < 3; i++) send(spammer, "{\"message\":\"hi\"}").andExpect(status().isOk());
        send(spammer, "{\"message\":\"hi\"}").andExpect(status().isTooManyRequests());
        send(other, "{\"message\":\"hi\"}").andExpect(status().isOk());
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/chat").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/chat").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
