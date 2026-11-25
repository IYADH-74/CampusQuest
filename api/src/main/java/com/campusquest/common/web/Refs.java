package com.campusquest.common.web;

import com.campusquest.cohort.Cohort;
import com.campusquest.common.Text;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import java.util.UUID;

/** Compact references embedded in other views. They never carry contact details or secrets. */
public final class Refs {

    private Refs() {}

    public record UserRef(UUID id, String displayName, Role role) {
        public static UserRef of(User user) {
            return user == null ? null
                    : new UserRef(user.getId(), Text.displayName(user.getFirstName(), user.getLastName()), user.getRole());
        }
    }

    public record CohortRef(UUID id, String code, String name) {
        public static CohortRef of(Cohort cohort) {
            return cohort == null ? null : new CohortRef(cohort.getId(), cohort.getCode(), cohort.getName());
        }
    }
}
