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
            @NotBlank @Size(max = 100, message = "must be at most 100 characters") String fullName,
            @Email @NotBlank @Size(max = 254) String email,
            @Size(min = 6, max = 72, message = "must be between 6 and 72 characters")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password) {}

    public record LoginRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {}

    public record ForgotPasswordRequest(@Email @NotBlank @Size(max = 254) String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @Size(min = 6, max = 72, message = "must be between 6 and 72 characters")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String newPassword) {}

    public record AuthResponse(String token, Long userId, String fullName, String email) {}
}
