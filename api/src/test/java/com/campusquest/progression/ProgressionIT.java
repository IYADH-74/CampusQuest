package com.campusquest.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusquest.cohort.Cohort;
import com.campusquest.support.IntegrationTest;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProgressionIT extends IntegrationTest {

    @Test
    void boostsCannotOverlapAndStoppingOneFreesTheWindow() throws Exception {
        String admin = token(user(Role.ADMIN));
        Instant start = Instant.now().plusSeconds(30 * 24 * 3600);
        String first = read(post(admin, "/api/v1/admin/boosts", boostJson(start, start.plusSeconds(7200)))
                .andExpect(status().isCreated()), "$.id");
        try {
            post(admin, "/api/v1/admin/boosts", boostJson(start.plusSeconds(3600), start.plusSeconds(10_000)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOST_OVERLAP"));
            post(admin, "/api/v1/admin/boosts", boostJson(start, start.plusSeconds(8 * 24 * 3600)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BOOST_WINDOW"));
        } finally {
            post(admin, "/api/v1/admin/boosts/{id}/stop", null, first).andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelledAt").exists());
        }
        String replacement = read(post(admin, "/api/v1/admin/boosts", boostJson(start, start.plusSeconds(7200)))
                .andExpect(status().isCreated()), "$.id");
        post(admin, "/api/v1/admin/boosts/{id}/stop", null, replacement).andExpect(status().isOk());
    }

    @Test
    void boostsAreAdminOnly() throws Exception {
        post(token(user(Role.STAFF)), "/api/v1/admin/boosts",
                boostJson(Instant.now(), Instant.now().plusSeconds(60))).andExpect(status().isForbidden());
    }

    @Test
    void leaderboardRanksTiesTogetherAndFlagsTheViewer() throws Exception {
        Cohort cohort = cohort();
        User first = student(cohort);
        User tied = student(cohort);
        User last = student(cohort);
        setXp(first, 9000);
        setXp(tied, 9000);
        setXp(last, 100);

        get(token(last), "/api/v1/leaderboard?cohortId={id}", cohort.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].rank").value(1))
                .andExpect(jsonPath("$.content[1].rank").value(1))
                .andExpect(jsonPath("$.content[2].rank").value(3))
                .andExpect(jsonPath("$.content[2].me").value(true))
                .andExpect(jsonPath("$.content[0].displayName").value(org.hamcrest.Matchers.endsWith(".")))
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    void manualAdjustmentsGoThroughTheLedgerAndAreAudited() throws Exception {
        User admin = user(Role.ADMIN);
        String adminToken = token(admin);
        User student = user(Role.STUDENT);
        setXp(student, 1000);

        post(adminToken, "/api/v1/admin/students/{id}/xp-adjustments", "{\"amount\":-5000,\"note\":\"Cheating\"}",
                student.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-1000))
                .andExpect(jsonPath("$.balanceAfter").value(0));
        assertThat(xpOf(student)).isZero();

        post(adminToken, "/api/v1/admin/students/{id}/xp-adjustments", "{\"amount\":100,\"note\":\"x\"}", student.getId())
                .andExpect(status().isBadRequest());
        post(adminToken, "/api/v1/admin/students/{id}/xp-adjustments", "{\"amount\":100,\"note\":\"Bonus\"}",
                user(Role.TEACHER).getId())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_A_STUDENT"));

        get(adminToken, "/api/v1/admin/audit-events?action=XP_ADJUSTED&targetId={id}", student.getId())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.content[0].details.applied").value(-1000));
    }

    private static String boostJson(Instant startsAt, Instant endsAt) {
        return """
                {"multiplier":1.25,"startsAt":"%s","endsAt":"%s","reason":"Test boost"}
                """.formatted(startsAt, endsAt);
    }
}
