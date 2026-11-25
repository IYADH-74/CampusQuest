package com.campusquest.security;

import com.campusquest.common.Ids;
import com.campusquest.config.SecuritySettings;
import com.campusquest.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Mints short-lived RS256 access tokens. */
@Service
public class AccessTokenService {

    public static final String TOKEN_VERSION_CLAIM = "tv";
    public static final String ROLE_CLAIM = "role";

    public record IssuedAccessToken(String value, Instant expiresAt, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final JwtKeys keys;
    private final SecuritySettings.Jwt settings;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, JwtKeys keys, SecuritySettings settings, Clock clock) {
        this.encoder = encoder;
        this.keys = keys;
        this.settings = settings.jwt();
        this.clock = clock;
    }

    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(settings.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .audience(List.of(settings.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .id(Ids.newId().toString())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .claim(ROLE_CLAIM, user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.keyId()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, expiresAt, settings.accessTokenTtl().toSeconds());
    }
}
