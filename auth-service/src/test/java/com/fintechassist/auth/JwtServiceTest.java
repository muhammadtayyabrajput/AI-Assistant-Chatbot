package com.fintechassist.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static User user() {
        User u = new User("a@b.com", "Ali Khan", "hash");
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, 42L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return u;
    }

    @Test
    void tokenCarriesUserIdNameAndEmail() {
        String token = new JwtService(SECRET, 24).generate(user());
        Claims c = Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(token).getPayload();
        assertEquals("42", c.getSubject());
        assertEquals("Ali Khan", c.get("name"));
        assertEquals("a@b.com", c.get("email"));
        assertNull(c.get("password"));
    }

    @Test
    void tokenSignedWithOneSecretFailsWithAnother() {
        String token = new JwtService(SECRET, 24).generate(user());
        assertThrows(SignatureException.class,
                () -> Jwts.parser().verifyWith(key("a-completely-different-secret-1234567890")).build().parseSignedClaims(token));
    }

    @Test
    void tamperedPayloadIsRejected() {
        String token = new JwtService(SECRET, 24).generate(user());
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertThrows(Exception.class, () -> Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(forged));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = Jwts.builder().subject("1").expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key(SECRET)).compact();
        assertThrows(ExpiredJwtException.class,
                () -> Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(token));
    }

    @Test
    void unsignedTokensAreRejected() {
        String unsigned = Jwts.builder().subject("1").compact();
        assertThrows(Exception.class, () -> Jwts.parser().verifyWith(key(SECRET)).build().parseSignedClaims(unsigned));
    }

    @Test
    void weakSecretsMakeTheServiceRefuseToStart() {
        assertThrows(WeakKeyException.class, () -> new JwtService("short", 24));
    }
}
