package com.fintechassist.banking;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "jwt.secret=test-secret-test-secret-test-secret-123456")
@AutoConfigureMockMvc
class BankingApiSecurityTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-123456";

    @Autowired MockMvc mvc;

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(String subject) {
        return Jwts.builder().subject(subject).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(SECRET)).compact();
    }

    private static String bearer(String subject) {
        return "Bearer " + token(subject);
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        for (String path : new String[]{"/api/transactions/1", "/api/cards/1", "/api/accounts/1"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/cards/1/card-debit-1/block")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageTokensAreRejected() throws Exception {
        for (String t : new String[]{"garbage", "a.b.c", "", "null"}) {
            mvc.perform(get("/api/cards/1").header("Authorization", "Bearer " + t)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/cards/1").header("Authorization", "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String forged = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key("some-other-secret-some-other-secret-9999")).compact();
        mvc.perform(get("/api/cards/1").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String expired = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key(SECRET)).compact();
        mvc.perform(get("/api/cards/1").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutSignatureIsRejected() throws Exception {
        String unsigned = Jwts.builder().subject("1").compact();
        mvc.perform(get("/api/cards/1").header("Authorization", "Bearer " + unsigned)).andExpect(status().isUnauthorized());

        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\"}".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/api/cards/1").header("Authorization", "Bearer " + header + "." + payload + "."))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithNonNumericSubjectIsRejected() throws Exception {
        mvc.perform(get("/api/cards/1").header("Authorization", bearer("admin"))).andExpect(status().isUnauthorized());
    }

    @Test
    void usersCanReadTheirOwnData() throws Exception {
        mvc.perform(get("/api/cards/1").header("Authorization", bearer("1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/transactions/1").header("Authorization", bearer("1"))).andExpect(status().isOk());
        mvc.perform(get("/api/accounts/1").header("Authorization", bearer("1"))).andExpect(status().isOk());
    }

    @Test
    void usersCannotReadSomeoneElsesData() throws Exception {
        for (String path : new String[]{"/api/transactions/2", "/api/cards/2", "/api/accounts/2"}) {
            mvc.perform(get(path).header("Authorization", bearer("1"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void usersCannotBlockSomeoneElsesCard() throws Exception {
        mvc.perform(post("/api/cards/2/card-debit-2/block").header("Authorization", bearer("1")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/cards/2").header("Authorization", bearer("2")))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void userCannotBlockAnotherUsersCardByUsingTheirOwnIdInThePath() throws Exception {
        mvc.perform(post("/api/cards/3/card-debit-4/block").header("Authorization", bearer("3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NOT_FOUND"));
        mvc.perform(get("/api/cards/4").header("Authorization", bearer("4")))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void blockingACardChangesItsStatus() throws Exception {
        mvc.perform(post("/api/cards/5/card-debit-5/block").header("Authorization", bearer("5")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        mvc.perform(get("/api/cards/5").header("Authorization", bearer("5")))
                .andExpect(jsonPath("$[0].status").value("BLOCKED"))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"));
    }

    @Test
    void blockingTwiceReportsAlreadyBlocked() throws Exception {
        mvc.perform(post("/api/cards/6/card-credit-6/block").header("Authorization", bearer("6")));
        String second = mvc.perform(post("/api/cards/6/card-credit-6/block").header("Authorization", bearer("6")))
                .andReturn().getResponse().getContentAsString();
        assertEquals(true, second.contains("already blocked"));
    }

    @Test
    void cardNumbersAreNeverReturnedInFull() throws Exception {
        String body = mvc.perform(get("/api/cards/7").header("Authorization", bearer("7")))
                .andReturn().getResponse().getContentAsString();
        assertEquals(false, body.matches("(?s).*\\d{12,}.*"));
        assertEquals(true, body.contains("**** **** ****"));
    }

    @Test
    void limitParameterIsClampedAndValidated() throws Exception {
        mvc.perform(get("/api/transactions/8?limit=1").header("Authorization", bearer("8")))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/transactions/8?limit=-5").header("Authorization", bearer("8")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/transactions/8?limit=999999").header("Authorization", bearer("8")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/transactions/8?limit=abc").header("Authorization", bearer("8")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericUserIdInThePathIsABadRequest() throws Exception {
        mvc.perform(get("/api/cards/abc").header("Authorization", bearer("1"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/cards/1%20OR%201=1").header("Authorization", bearer("1"))).andExpect(status().isBadRequest());
    }

    @Test
    void faqListIsPublicAndComplete() throws Exception {
        mvc.perform(get("/api/faqs")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/cards/1").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/cards/1").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
