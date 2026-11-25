package com.campusquest.quest;

import java.util.EnumSet;
import java.util.Set;

public enum ParticipationStatus {
    ACTIVE,
    SUBMITTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    EXPIRED,
    CANCELLED;

    /** Work in flight: occupies a challenge slot. */
    public static final Set<ParticipationStatus> IN_FLIGHT = EnumSet.of(ACTIVE, SUBMITTED);

    /** Statuses that do not hold a place in a challenge's capacity. */
    public static final Set<ParticipationStatus> NOT_HOLDING_A_PLACE = EnumSet.of(WITHDRAWN, CANCELLED);
}
