package com.campusquest.user;

public enum Role {
    ADMIN,
    STAFF,
    TEACHER,
    STUDENT;

    public String authority() {
        return "ROLE_" + name();
    }
}
