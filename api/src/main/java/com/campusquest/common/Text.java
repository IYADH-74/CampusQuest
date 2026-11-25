package com.campusquest.common;

import java.util.Locale;

/** Small, dependency-free text helpers shared across modules. */
public final class Text {

    /** Letters (any script), combining marks, spaces, apostrophes, dots and hyphens. */
    public static final String PERSON_NAME_PATTERN = "^[\\p{L}\\p{M}][\\p{L}\\p{M}' .-]*$";

    private Text() {}

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** "Ada L." — enough to recognise a classmate without publishing full names. */
    public static String displayName(String firstName, String lastName) {
        String initial = lastName == null || lastName.isBlank() ? "" : " " + lastName.strip().charAt(0) + ".";
        return firstName.strip() + initial;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Escapes LIKE wildcards so user input is matched literally. */
    public static String likeContains(String value) {
        String escaped = value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
