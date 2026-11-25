package com.campusquest.auth;

import com.campusquest.common.Text;
import com.campusquest.user.UserApi.MeView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthApi {

    private AuthApi() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Size(max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String firstName,
            @NotBlank @Size(max = 80) @Pattern(regexp = Text.PERSON_NAME_PATTERN) String lastName,
            @NotNull UUID cohortId) {}

    public record VerifyEmailRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String code) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(max = 128) String newPassword) {}

    public record SessionResponse(String accessToken, String tokenType, long expiresIn, MeView user) {}

    public record MessageResponse(String message) {}
}
