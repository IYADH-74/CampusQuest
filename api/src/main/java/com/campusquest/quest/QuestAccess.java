package com.campusquest.quest;

import com.campusquest.security.AuthenticatedUser;
import com.campusquest.user.Role;

/**
 * Object-level authorisation rules for quests and participations, kept in one place so they can be reasoned
 * about (and tested) as a whole. Callers turn a {@code false} into a 404 so existence isn't leaked.
 */
final class QuestAccess {

    private QuestAccess() {}

    static boolean canView(Quest quest, AuthenticatedUser me, boolean participates) {
        if (me.is(Role.ADMIN) || me.is(Role.STAFF) || quest.isAuthoredBy(me.id())) {
            return true;
        }
        if (quest.isChallenge() && quest.isVisibleToStudents()) {
            return true;
        }
        return me.is(Role.STUDENT) && participates;
    }

    static boolean canManage(Quest quest, AuthenticatedUser me) {
        return quest.isAuthoredBy(me.id());
    }

    /** Authors review their own quests; staff also run every campus-wide challenge. Admins never review. */
    static boolean canReview(Quest quest, AuthenticatedUser me) {
        if (!me.is(Role.TEACHER) && !me.is(Role.STAFF)) {
            return false;
        }
        return quest.isAuthoredBy(me.id()) || (me.is(Role.STAFF) && quest.isChallenge());
    }

    static boolean canListParticipants(Quest quest, AuthenticatedUser me) {
        return me.is(Role.ADMIN) || canReview(quest, me);
    }

    static boolean canView(Participation participation, AuthenticatedUser me) {
        return participation.belongsTo(me.id()) || me.is(Role.ADMIN) || canReview(participation.getQuest(), me);
    }
}
