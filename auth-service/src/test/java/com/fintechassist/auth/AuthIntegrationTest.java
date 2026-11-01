package com.fintechassist.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-secret-test-secret-test-secret-123456",
        "app.dev-expose-reset-link=true"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    private static final String STRONG = "Abcde1!x";
    private static final String SECRET = "test-secret-test-secret-test-secret-123456";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository resetTokens;

    private static String email() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions signup(String name, String email, String password) throws Exception {
        return postJson("/api/auth/signup", json.writeValueAsString(
                java.util.Map.of("fullName", name, "email", email, "password", password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", json.writeValueAsString(java.util.Map.of("email", email, "password", password)));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void signupCreatesAccountAndReturnsUsableToken() throws Exception {
        String email = email();
        ResultActions r = signup("Ali Khan", email, STRONG).andExpect(status().isCreated());
        JsonNode res = body(r);

        assertEquals(email, res.get("email").asText());
        assertEquals("Ali Khan", res.get("fullName").asText());
        assertFalse(r.andReturn().getResponse().getContentAsString().toLowerCase().contains("password"));

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(res.get("token").asText()).getPayload();
        assertEquals(res.get("userId").asText(), claims.getSubject());
        assertEquals("Ali Khan", claims.get("name", String.class));
        assertTrue(claims.getExpiration().toInstant().isAfter(Instant.now().plusSeconds(23 * 3600)));
    }

    @Test
    void passwordIsStoredHashedNeverPlain() throws Exception {
        String email = email();
        signup("Hash Check", email, STRONG).andExpect(status().isCreated());
        String stored = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertNotEquals(STRONG, stored);
        assertTrue(stored.startsWith("$2"), "expected a BCrypt hash");
    }

    @Test
    void emailIsNormalisedToLowerCase() throws Exception {
        String email = email();
        signup("Case", email.toUpperCase(), STRONG).andExpect(status().isCreated());
        assertTrue(users.findByEmail(email).isPresent());
    }

    @Test
    void duplicateEmailIsRejectedEvenWithDifferentNameAndPassword() throws Exception {
        String email = email();
        signup("First", email, STRONG).andExpect(status().isCreated());
        signup("Second Person", email, "Zzzzz9@y").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCase() throws Exception {
        String email = email();
        signup("First", email, STRONG).andExpect(status().isCreated());
        signup("Second", email.toUpperCase(), STRONG).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcde1!x",
            "1Abcde!x",
            "!Abcde1x",
            "Abcdefg!",
            "Abcdefg1",
            "A1!",
            "     ",
    })
    void weakPasswordsAreRejected(String password) throws Exception {
        signup("Weak", email(), password).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcde1!x", "Zebra#2024", "Q1_aaaa", "Password1!"})
    void passwordsMeetingTheRulesAreAccepted(String password) throws Exception {
        signup("Strong", email(), password).andExpect(status().isCreated());
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() throws Exception {
        signup("Long", email(), "A1!" + "a".repeat(80)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "a@", "@b.com", "", "' OR 1=1 --@x.com"})
    void invalidEmailsAreRejected(String email) throws Exception {
        signup("Bad Email", email, STRONG).andExpect(status().isBadRequest());
    }

    @Test
    void overlongNameIsRejectedInsteadOfFailingInTheDatabase() throws Exception {
        signup("N".repeat(101), email(), STRONG).andExpect(status().isBadRequest());
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        postJson("/api/auth/signup", "{}").andExpect(status().isBadRequest());
        postJson("/api/auth/signup", "not json").andExpect(status().is4xxClientError());
    }

    @Test
    void loginWorksWithCorrectCredentialsAndIgnoresEmailCase() throws Exception {
        String email = email();
        signup("Login", email, STRONG).andExpect(status().isCreated());
        login(email.toUpperCase(), STRONG).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameResponse() throws Exception {
        String email = email();
        signup("Same", email, STRONG).andExpect(status().isCreated());
        String wrong = login(email, "Wrong1!pass").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknown = login(email(), "Wrong1!pass").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertEquals(wrong, unknown);
    }

    @Test
    void sqlInjectionInPasswordDoesNotAuthenticate() throws Exception {
        String email = email();
        signup("Inject", email, STRONG).andExpect(status().isCreated());
        login(email, "' OR '1'='1").andExpect(status().isUnauthorized());
        login(email, "x'; DROP TABLE users; --").andExpect(status().isUnauthorized());
        login(email, STRONG).andExpect(status().isOk());
    }

    @Test
    void accountIsLockedTemporarilyAfterRepeatedFailures() throws Exception {
        String email = email();
        signup("Brute", email, STRONG).andExpect(status().isCreated());
        for (int i = 0; i < 10; i++) {
            login(email, "Wrong1!pass").andExpect(status().isUnauthorized());
        }
        login(email, "Wrong1!pass").andExpect(status().isTooManyRequests());
        login(email, STRONG).andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginClearsPreviousFailures() throws Exception {
        String email = email();
        signup("Reset Count", email, STRONG).andExpect(status().isCreated());
        for (int i = 0; i < 9; i++) login(email, "Wrong1!pass").andExpect(status().isUnauthorized());
        login(email, STRONG).andExpect(status().isOk());
        for (int i = 0; i < 9; i++) login(email, "Wrong1!pass").andExpect(status().isUnauthorized());
        login(email, STRONG).andExpect(status().isOk());
    }

    private String requestResetToken(String email) throws Exception {
        String link = body(postJson("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}")
                .andExpect(status().isOk())).get("devResetLink").asText();
        return link.substring(link.indexOf("token=") + 6);
    }

    @Test
    void forgotPasswordGivesTheSameAnswerForUnknownEmails() throws Exception {
        String known = email();
        signup("Known", known, STRONG).andExpect(status().isCreated());
        String unknownMsg = body(postJson("/api/auth/forgot-password", "{\"email\":\"" + email() + "\"}")
                .andExpect(status().isOk())).get("message").asText();
        String knownMsg = body(postJson("/api/auth/forgot-password", "{\"email\":\"" + known + "\"}")
                .andExpect(status().isOk())).get("message").asText();
        assertEquals(unknownMsg, knownMsg);
    }

    @Test
    void fullResetFlowChangesThePassword() throws Exception {
        String email = email();
        signup("Reset", email, STRONG).andExpect(status().isCreated());
        String token = requestResetToken(email);

        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"Newpass1!\"}")
                .andExpect(status().isOk());

        login(email, STRONG).andExpect(status().isUnauthorized());
        login(email, "Newpass1!").andExpect(status().isOk());
    }

    @Test
    void resetLinkWorksOnlyOnce() throws Exception {
        String email = email();
        signup("Once", email, STRONG).andExpect(status().isCreated());
        String token = requestResetToken(email);
        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"Newpass1!\"}")
                .andExpect(status().isOk());
        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"Another1!\"}")
                .andExpect(status().isBadRequest());
        login(email, "Newpass1!").andExpect(status().isOk());
    }

    @Test
    void newResetRequestInvalidatesTheOlderLink() throws Exception {
        String email = email();
        signup("Newest", email, STRONG).andExpect(status().isCreated());
        String first = requestResetToken(email);
        String second = requestResetToken(email);
        postJson("/api/auth/reset-password", "{\"token\":\"" + first + "\",\"newPassword\":\"Newpass1!\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password", "{\"token\":\"" + second + "\",\"newPassword\":\"Newpass1!\"}")
                .andExpect(status().isOk());
    }

    @Test
    void expiredResetLinkIsRejected() throws Exception {
        String email = email();
        signup("Expired", email, STRONG).andExpect(status().isCreated());
        Long userId = users.findByEmail(email).orElseThrow().getId();
        String raw = "expired-token-" + UUID.randomUUID();
        resetTokens.save(new PasswordResetToken(userId, sha256(raw), Instant.now().minusSeconds(60)));

        postJson("/api/auth/reset-password", "{\"token\":\"" + raw + "\",\"newPassword\":\"Newpass1!\"}")
                .andExpect(status().isBadRequest());
        login(email, STRONG).andExpect(status().isOk());
    }

    @Test
    void resetWithWeakPasswordIsRejectedAndLinkStaysUsable() throws Exception {
        String email = email();
        signup("Weak Reset", email, STRONG).andExpect(status().isCreated());
        String token = requestResetToken(email);
        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"weak\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password", "{\"token\":\"" + token + "\",\"newPassword\":\"Newpass1!\"}")
                .andExpect(status().isOk());
    }

    @Test
    void guessedOrMalformedTokensAreRejected() throws Exception {
        for (String t : new String[]{"nope", "' OR '1'='1", "../../etc/passwd", "a".repeat(500)}) {
            postJson("/api/auth/reset-password", json.writeValueAsString(
                    java.util.Map.of("token", t, "newPassword", "Newpass1!"))).andExpect(status().is4xxClientError());
        }
    }

    @Test
    void forgotPasswordIsRateLimitedPerEmailWithoutRevealingIt() throws Exception {
        String email = email();
        signup("Spam", email, STRONG).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            assertTrue(body(postJson("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}")
                    .andExpect(status().isOk())).has("devResetLink"));
        }
        JsonNode limited = body(postJson("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}")
                .andExpect(status().isOk()));
        assertFalse(limited.has("devResetLink"));
    }

    @Test
    void onlyAuthEndpointsAreReachable() throws Exception {
        for (String path : new String[]{"/api/users", "/actuator/env", "/admin", "/api/auth/../users"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertTrue(status == 400 || status == 401 || status == 403 || status == 404, path + " returned " + status);
        }
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/auth/login").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    private static String sha256(String s) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
