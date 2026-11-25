package com.campusquest.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("campusquest.security")
public record SecuritySettings(
        @Valid @NotNull Cors cors,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RefreshToken refreshToken,
        @Valid @NotNull Verification verification,
        @NotNull Duration passwordResetTtl,
        @NotNull Duration inviteTtl,
        @DefaultValue List<String> allowedEmailDomains) {

    public SecuritySettings {
        allowedEmailDomains = allowedEmailDomains.stream()
                .map(String::trim)
                .filter(d -> !d.isEmpty())
                .map(d -> d.toLowerCase(Locale.ROOT))
                .toList();
    }

    public record Cors(@DefaultValue List<String> allowedOrigins) {}

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotNull Duration accessTokenTtl,
            String privateKey,
            String publicKey,
            boolean allowEphemeralKeys) {}

    public record RefreshToken(
            @NotNull Duration ttl,
            @NotNull Duration absoluteTtl,
            @NotBlank String cookieName,
            @NotBlank String cookiePath,
            boolean cookieSecure) {}

    public record Verification(@NotNull Duration codeTtl, @Min(1) int maxAttempts, String pepper) {}

    public boolean isEmailDomainAllowed(String normalizedEmail) {
        if (allowedEmailDomains.isEmpty()) {
            return true;
        }
        String domain = normalizedEmail.substring(normalizedEmail.lastIndexOf('@') + 1);
        return allowedEmailDomains.contains(domain);
    }
}
