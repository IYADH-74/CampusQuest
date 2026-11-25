package com.campusquest.user;

public enum AccountStatus {
    /** Self-registered student who has not entered their email code yet. */
    PENDING_VERIFICATION,
    /** Created by an admin; has not chosen a password yet. */
    INVITED,
    ACTIVE,
    DISABLED
}
