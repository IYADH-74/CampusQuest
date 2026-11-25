package com.campusquest.security;

import com.campusquest.common.error.ApiException;
import java.util.Locale;

/**
 * NIST SP 800-63B style: long passwords, no composition rules, plus a few cheap checks against the
 * obviously guessable. Max length bounds hashing cost (Argon2 is deliberately expensive).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {}

    public static void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw weak("Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
        }
        if (password.chars().distinct().count() < 5) {
            throw weak("Password is too repetitive");
        }
        if (email != null && email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (localPart.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(localPart)) {
                throw weak("Password must not contain your email address");
            }
        }
    }

    private static ApiException weak(String detail) {
        return ApiException.badRequest("WEAK_PASSWORD", detail);
    }
}
