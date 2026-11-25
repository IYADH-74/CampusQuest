package com.campusquest.auth.token;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.common.Ids;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.RequestMetadata;
import com.campusquest.config.SecuritySettings;
import com.campusquest.security.SecureTokens;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Rotating refresh tokens with reuse detection (OAuth 2.0 Security BCP §4.14). Each token can be redeemed once.
 * Presenting an already-rotated token means it leaked, so the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    public record Rotation(User user, String newToken) {}

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final AuditService audit;
    private final SecuritySettings.RefreshToken settings;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users, AuditService audit,
            SecuritySettings settings, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.audit = audit;
        this.settings = settings.refreshToken();
        this.clock = clock;
    }

    /** Starts a new session (token family). */
    @Transactional(propagation = Propagation.MANDATORY)
    public String startSession(User user, RequestMetadata client) {
        Instant now = clock.instant();
        return issue(user.getId(), Ids.newId(), now.plus(settings.absoluteTtl()), client, now).raw;
    }

    /**
     * Redeems a refresh token for a fresh one. Revocations done here must survive the 401 that follows,
     * hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken, RequestMetadata client) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() > 128) {
            throw invalid();
        }
        Instant now = clock.instant();
        RefreshToken current = tokens.findByHashForUpdate(SecureTokens.sha256Hex(rawToken)).orElseThrow(this::invalid);
        if (current.isRevoked()) {
            tokens.revokeFamily(current.getFamilyId(), now);
            audit.record(AuditAction.REFRESH_TOKEN_REUSE_DETECTED, current.getUserId(), "refresh_token_family",
                    current.getFamilyId(), Map.of("ip", String.valueOf(client.ip())));
            throw invalid();
        }
        if (current.isExpired(now)) {
            throw invalid();
        }
        User user = users.findById(current.getUserId()).orElseThrow(this::invalid);
        if (!user.isActive()) {
            tokens.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        Issued next = issue(user.getId(), current.getFamilyId(), current.getFamilyExpiresAt(), client, now);
        current.markRotated(now, next.id);
        return new Rotation(user, next.raw);
    }

    /** Ends the session a token belongs to. Unknown or already-revoked tokens are ignored. */
    @Transactional
    public void endSession(String rawToken) {
        if (!StringUtils.hasText(rawToken) || rawToken.length() > 128) {
            return;
        }
        tokens.findByHashForUpdate(SecureTokens.sha256Hex(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAllSessions(UUID userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    private record Issued(UUID id, String raw) {}

    private Issued issue(UUID userId, UUID familyId, Instant familyExpiresAt, RequestMetadata client, Instant now) {
        String raw = SecureTokens.randomToken();
        Instant slidingExpiry = now.plus(settings.ttl());
        Instant expiresAt = slidingExpiry.isBefore(familyExpiresAt) ? slidingExpiry : familyExpiresAt;
        RefreshToken token = new RefreshToken(userId, familyId, SecureTokens.sha256Hex(raw), now, expiresAt,
                familyExpiresAt, client.userAgent(), client.ip());
        tokens.save(token);
        return new Issued(token.getId(), raw);
    }

    private ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
    }
}
