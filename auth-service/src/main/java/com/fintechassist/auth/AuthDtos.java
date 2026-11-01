package com.fintechassist.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public static final String PASSWORD_REGEX = "^[A-Z](?=.*\\d)(?=.*[^A-Za-z0-9]).*$";
    public static final String PASSWORD_MESSAGE =
            "must start with a capital letter and contain at least one number and one special character";

    public record SignupRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @Size(min = 6, message = "must be at least 6 characters")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @Size(min = 6, message = "must be at least 6 characters")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String newPassword) {}

    public record AuthResponse(String token, Long userId, String fullName, String email) {}
}
