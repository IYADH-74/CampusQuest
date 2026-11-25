package com.campusquest.quest;

public enum QuestCategory {
    /** The only category for assignments. */
    COURSEWORK,
    CERTIFICATION,
    COMPETITION,
    COURSE,
    EVENT;

    public boolean allowedFor(QuestKind kind) {
        return kind == QuestKind.ASSIGNMENT ? this == COURSEWORK : this != COURSEWORK;
    }
}
