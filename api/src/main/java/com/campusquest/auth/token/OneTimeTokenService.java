package com.campusquest.auth.token;

import com.campusquest.config.SecuritySettings;
import com.campusquest.security.SecureTokens;
import com.campusquest.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Issues and redeems one-time secrets.
 * <ul>
 *   <li>Email codes: 6 digits from {@code SecureRandom}, stored as HMAC-SHA256(pepper, tokenId:code), burned
 *       after a few wrong guesses. The pepper means a database leak alone doesn't let anyone brute-force codes offline.</li>
 *   <li>Link tokens (reset / invite): 256-bit random, stored as SHA-256.</li>
 * </ul>
 * Issuing a new secret always invalidates older ones of the same purpose.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OneTimeTokenService {

    private static final int MIN_PEPPER_LENGTH = 32;

    private final OneTimeTokenRepository tokens;
    private final SecuritySettings settings;
    private final Clock clock;

    public OneTimeTokenService(OneTimeTokenRepository tokens, SecuritySettings settings, Clock clock) {
        if (!StringUtils.hasText(settings.verification().pepper())
                || settings.verification().pepper().length() < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException("campusquest.security.verification.pepper (VERIFICATION_PEPPER) must be "
                    + "set to a random secret of at least " + MIN_PEPPER_LENGTH + " characters");
        }
        this.tokens = tokens;
        this.settings = settings;
        this.clock = clock;
    }

    public String issueVerificationCode(User user) {
        Instant now = clock.instant();
        tokens.consumeAll(user.getId(), EnumSet.of(TokenPurpose.EMAIL_VERIFICATION), now);
        String code = SecureTokens.numericCode(6);
        OneTimeToken token = new OneTimeToken(user.getId(), TokenPurpose.EMAIL_VERIFICATION, now,
                now.plus(settings.verification().codeTtl()));
        token.setTokenHash(codeHash(token, code));
        tokens.save(token);
        return code;
    }

    /**
     * Checks a code against the user's open verification token. A wrong guess counts as an attempt, and that
     * count is persisted even if the caller later throws, because callers mark this path {@code noRollbackFor}.
     */
    public boolean redeemVerificationCode(User user, String code) {
        Instant now = clock.instant();
        List<OneTimeToken> open = tokens.findOpenForUpdate(user.getId(), TokenPurpose.EMAIL_VERIFICATION);
        if (open.isEmpty()) {
            return false;
        }
        OneTimeToken token = open.getFirst();
        if (!token.isUsable(now, settings.verification().maxAttempts())) {
            return false;
        }
        if (code != null && SecureTokens.constantTimeEquals(token.getTokenHash(), codeHash(token, code))) {
            token.consume(now);
            return true;
        }
        token.recordFailedAttempt();
        return false;
    }

    public String issueLinkToken(User user, TokenPurpose purpose) {
        Duration ttl = switch (purpose) {
            case PASSWORD_RESET -> settings.passwordResetTtl();
            case ACCOUNT_INVITE -> settings.inviteTtl();
            case EMAIL_VERIFICATION -> throw new IllegalArgumentException("Use issueVerificationCode");
        };
        Instant now = clock.instant();
        tokens.consumeAll(user.getId(), EnumSet.of(purpose), now);
        String raw = SecureTokens.randomToken();
        OneTimeToken token = new OneTimeToken(user.getId(), purpose, now, now.plus(ttl));
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        tokens.save(token);
        return raw;
    }

    /** Atomically redeems a link token if it is open, unexpired and of an accepted purpose. */
    public Optional<OneTimeToken> redeemLinkToken(String raw, Set<TokenPurpose> acceptedPurposes) {
        if (!StringUtils.hasText(raw) || raw.length() > 128) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByHashForUpdate(SecureTokens.sha256Hex(raw))
                .filter(t -> acceptedPurposes.contains(t.getPurpose()))
                .filter(t -> t.isUsable(now, Integer.MAX_VALUE))
                .map(t -> {
                    t.consume(now);
                    return t;
                });
    }

    public void invalidateAll(User user, Set<TokenPurpose> purposes) {
        tokens.consumeAll(user.getId(), purposes, clock.instant());
    }

    private String codeHash(OneTimeToken token, String code) {
        return SecureTokens.hmacSha256Hex(settings.verification().pepper(), token.getId() + ":" + code);
    }
}
