package com.campusquest.quest;

public enum QuestStatus {
    DRAFT,
    PUBLISHED,
    /** Challenge no longer accepting new participants; existing ones can finish. */
    CLOSED,
    CANCELLED
}
