package com.campusquest.security;

import com.campusquest.user.AccountStatus;
import com.campusquest.user.UserRepository;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a cryptographically valid JWT into an authenticated user <b>only if</b> the account still exists, is
 * active, and has not revoked its tokens since issuance (token version). The role is taken from the
 * database, never from the token, so a role claim cannot be used to escalate.
 */
@Component
public class JwtToUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public JwtToUserConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseSubject(jwt.getSubject());
        Object versionClaim = jwt.getClaim(AccessTokenService.TOKEN_VERSION_CLAIM);
        if (!(versionClaim instanceof Number tokenVersion)) {
            throw revoked();
        }
        UserRepository.AuthSnapshot snapshot = users.findAuthSnapshot(userId).orElseThrow(JwtToUserConverter::revoked);
        if (snapshot.getStatus() != AccountStatus.ACTIVE || snapshot.getTokenVersion() != tokenVersion.intValue()) {
            throw revoked();
        }
        return new UserAuthenticationToken(new AuthenticatedUser(userId, snapshot.getEmail(), snapshot.getRole()), jwt);
    }

    private static UUID parseSubject(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw revoked();
        }
    }

    private static InvalidBearerTokenException revoked() {
        return new InvalidBearerTokenException("Token is invalid or has been revoked");
    }
}
