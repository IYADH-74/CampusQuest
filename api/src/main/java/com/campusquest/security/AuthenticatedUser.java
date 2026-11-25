package com.campusquest.security;

import com.campusquest.user.Role;
import java.io.Serializable;
import java.util.UUID;

/**
 * The caller, resolved from a verified access token <em>and</em> the current database state.
 * This is the only source of "who am I" in the application.
 */
public record AuthenticatedUser(UUID id, String email, Role role) implements Serializable {

    public boolean is(Role candidate) {
        return role == candidate;
    }
}
