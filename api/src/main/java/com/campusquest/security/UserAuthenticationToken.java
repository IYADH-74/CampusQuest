package com.campusquest.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class UserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;
    private final transient Jwt jwt;

    public UserAuthenticationToken(AuthenticatedUser principal, Jwt jwt) {
        super(List.of(new SimpleGrantedAuthority(principal.role().authority())));
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    @Override
    public String getName() {
        return principal.id().toString();
    }
}
