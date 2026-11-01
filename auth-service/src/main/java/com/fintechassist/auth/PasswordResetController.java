package com.fintechassist.auth;

import com.fintechassist.auth.AuthDtos.ForgotPasswordRequest;
import com.fintechassist.auth.AuthDtos.ResetPasswordRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ObjectProvider<JavaMailSender> mail;
    private final String frontendUrl;
    private final Duration ttl;
    private final boolean exposeLink;
    private final RateLimiter requests = new RateLimiter(5, Duration.ofMinutes(15));

    public PasswordResetController(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                                   ObjectProvider<JavaMailSender> mail,
                                   @Value("${app.frontend-url}") String frontendUrl,
                                   @Value("${app.reset-token-minutes}") long minutes,
                                   @Value("${app.dev-expose-reset-link}") boolean exposeLink) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
        this.ttl = Duration.ofMinutes(minutes);
        this.exposeLink = exposeLink;
    }

    @PostMapping("/forgot-password")
    @Transactional
    public Map<String, String> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "If that email is registered, a password reset link has been sent.");

        String email = req.email().trim().toLowerCase();
        if (!requests.tryAcquire(email)) return response;

        users.findByEmail(email).ifPresent(user -> {
            tokens.deleteByUserId(user.getId());
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            tokens.save(new PasswordResetToken(user.getId(), sha256(token), Instant.now().plus(ttl)));

            String link = frontendUrl + "/reset-password?token=" + token;
            sendOrLog(user, link);
            if (exposeLink) response.put("devResetLink", link);
        });
        return response;
    }

    @PostMapping("/reset-password")
    @Transactional
    public Map<String, String> reset(@Valid @RequestBody ResetPasswordRequest req) {
        PasswordResetToken t = tokens.findByTokenHash(sha256(req.token()))
                .filter(x -> !x.isUsed() && x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired. Please request a new one."));
        User user = users.findById(t.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset link"));
        user.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(user);
        t.markUsed();
        tokens.save(t);
        return Map.of("message", "Password updated. You can now log in.");
    }

    private void sendOrLog(User user, String link) {
        JavaMailSender sender = mail.getIfAvailable();
        if (sender == null) {
            log.info("[password reset] SMTP not configured. Reset link for {}: {}", user.getEmail(), link);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setTo(user.getEmail());
            m.setSubject("Reset your FintechAssist password");
            m.setText("Hi " + user.getFullName() + ",\n\nUse this link to set a new password (valid for "
                    + ttl.toMinutes() + " minutes):\n" + link + "\n\nIf you did not ask for this, ignore this email.");
            sender.send(m);
        } catch (Exception e) {
            log.error("Could not send reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
