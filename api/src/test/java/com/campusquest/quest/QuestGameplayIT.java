package com.campusquest.quest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusquest.cohort.Cohort;
import com.campusquest.support.IntegrationTest;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestGameplayIT extends IntegrationTest {

    @Autowired ExpirySweeper expirySweeper;

    @Test
    void assignmentLifecycle_publishEnrollsTheCohort_approvalAwardsTierXp() throws Exception {
        Cohort cohort = cohort();
        Cohort otherCohort = cohort();
        String teacher = token(teacherOf(cohort));
        User alice = student(cohort);
        User bob = student(cohort);
        User outsider = student(otherCohort);

        String questId = createAssignment(teacher, 3, cohort);
        get(teacher, "/api/v1/quests/{id}", questId).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.xpReward").value(4000));
        post(teacher, "/api/v1/quests/{id}/publish", null, questId).andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2));

        String aliceToken = token(alice);
        get(aliceToken, "/api/v1/me/participations?kind=ASSIGNMENT").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
        get(token(outsider), "/api/v1/me/participations").andExpect(jsonPath("$.totalElements").value(0));
        get(token(outsider), "/api/v1/quests/{id}", questId).andExpect(status().isNotFound());

        String work = participationOf(alice, questId);
        submitWork(aliceToken, work).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        get(teacher, "/api/v1/reviews/pending?size=100").andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + work + "')]").exists());

        post(teacher, "/api/v1/participations/{id}/approve", "{\"feedback\":\"Great work\"}", work)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.xpAwarded").value(4000));

        get(aliceToken, "/api/v1/me").andExpect(jsonPath("$.progression.xp").value(4000))
                .andExpect(jsonPath("$.progression.level").value(0))
                .andExpect(jsonPath("$.progression.nextLevelXp").value(5000));
        get(aliceToken, "/api/v1/me/xp-transactions").andExpect(jsonPath("$.content[0].reason").value("QUEST_REWARD"))
                .andExpect(jsonPath("$.content[0].participationId").value(work));
        // Approving twice is impossible.
        post(teacher, "/api/v1/participations/{id}/approve", "{}", work).andExpect(status().isConflict());
        assertThat(xpOf(alice)).isEqualTo(4000);
        assertThat(xpOf(bob)).isZero();
    }

    @Test
    void approvalCrossingAThresholdLevelsUp() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        User student = student(cohort);
        setXp(student, 4000);
        String questId = publishedAssignment(teacher, 1, cohort);
        String work = participationOf(student, questId);
        submitWork(token(student), work).andExpect(status().isOk());
        post(teacher, "/api/v1/participations/{id}/approve", "{}", work).andExpect(status().isOk());
        get(token(student), "/api/v1/me").andExpect(jsonPath("$.progression.xp").value(6000))
                .andExpect(jsonPath("$.progression.level").value(1));
    }

    @Test
    void rejectedAssignmentsCostXpButNeverBelowZero() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        User student = student(cohort);
        setXp(student, 500);
        String work = participationOf(student, publishedAssignment(teacher, 2, cohort));
        submitWork(token(student), work).andExpect(status().isOk());

        post(teacher, "/api/v1/participations/{id}/reject", "{}", work).andExpect(status().isBadRequest());
        post(teacher, "/api/v1/participations/{id}/reject", "{\"feedback\":\"Plagiarised\"}", work)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.xpAwarded").value(-500));
        assertThat(xpOf(student)).isZero();
    }

    @Test
    void requestChangesSendsWorkBackForAnotherAttempt() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        User student = student(cohort);
        String studentToken = token(student);
        String work = participationOf(student, publishedAssignment(teacher, 1, cohort));

        submitWork(studentToken, work).andExpect(status().isOk());
        post(teacher, "/api/v1/participations/{id}/request-changes", "{\"feedback\":\"Add tests\"}", work)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.revisionCount").value(1))
                .andExpect(jsonPath("$.feedback").value("Add tests"));
        submitWork(studentToken, work).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void sweepExpiresOverdueWork_penalisingAssignmentsOnly() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        String staff = token(user(Role.STAFF));
        User student = student(cohort);
        setXp(student, 10_000);
        String assignment = publishedAssignment(teacher, 1, cohort);
        String challenge = publishedChallenge(staff, 1, "\"dueAt\":\"" + inDays(2) + "\"");
        post(token(student), "/api/v1/quests/{id}/join", null, challenge).andExpect(status().isCreated());

        jdbc.sql("update quests set due_at = ? where id in (?, ?)")
                .params(java.sql.Timestamp.from(Instant.now().minusSeconds(60)), UUID.fromString(assignment),
                        UUID.fromString(challenge))
                .update();
        expirySweeper.sweep();

        String studentToken = token(student);
        get(studentToken, "/api/v1/participations/{id}", participationOf(student, assignment))
                .andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.xpAwarded").value(-2000));
        get(studentToken, "/api/v1/participations/{id}", participationOf(student, challenge))
                .andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.xpAwarded").value(0));
        assertThat(xpOf(student)).isEqualTo(8000);

        // Idempotent: a second sweep changes nothing.
        expirySweeper.sweep();
        assertThat(xpOf(student)).isEqualTo(8000);
        // Past due: no late submissions.
        submitWork(studentToken, participationOf(student, assignment)).andExpect(status().isConflict());
    }

    @Test
    void challengeSlotsGrowWithLevel() throws Exception {
        String staff = token(user(Role.STAFF));
        User student = user(Role.STUDENT);
        String first = publishedChallenge(staff, 1, null);
        String second = publishedChallenge(staff, 1, null);

        post(token(student), "/api/v1/quests/{id}/join", null, first).andExpect(status().isCreated());
        post(token(student), "/api/v1/quests/{id}/join", null, second).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_FREE_SLOT"));

        setXp(student, 30_000); // level 5 → 2 slots
        post(token(student), "/api/v1/quests/{id}/join", null, second).andExpect(status().isCreated());
        post(token(student), "/api/v1/quests/{id}/join", null, first).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    void capacityHoldsUnderConcurrentJoins() throws Exception {
        String staff = token(user(Role.STAFF));
        String challenge = publishedChallenge(staff, 1, "\"maxParticipants\":1");
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tokens.add(token(user(Role.STUDENT)));
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String token : tokens) {
                Callable<Integer> join = () -> {
                    start.await();
                    return post(token, "/api/v1/quests/{id}/join", null, challenge).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(join));
            }
            start.countDown();
            int created = 0;
            for (Future<Integer> result : results) {
                int code = result.get();
                assertThat(code).isIn(201, 409);
                if (code == 201) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        get(staff, "/api/v1/quests/{id}", challenge).andExpect(jsonPath("$.participantCount").value(1));
    }

    @Test
    void minimumLevelIsEnforced() throws Exception {
        String staff = token(user(Role.STAFF));
        String elite = publishedChallenge(staff, 5, "\"minLevel\":3");
        User student = user(Role.STUDENT);
        post(token(student), "/api/v1/quests/{id}/join", null, elite).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEVEL_TOO_LOW"));
        setXp(student, 16_500);
        post(token(student), "/api/v1/quests/{id}/join", null, elite).andExpect(status().isCreated());
    }

    @Test
    void withdrawingFreesTheSlotAndRejoiningReusesTheParticipation() throws Exception {
        String staff = token(user(Role.STAFF));
        User student = user(Role.STUDENT);
        String studentToken = token(student);
        String a = publishedChallenge(staff, 1, null);
        String b = publishedChallenge(staff, 1, null);

        String joined = read(post(studentToken, "/api/v1/quests/{id}/join", null, a).andExpect(status().isCreated()), "$.id");
        post(studentToken, "/api/v1/participations/{id}/withdraw", null, joined)
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        post(studentToken, "/api/v1/quests/{id}/join", null, b).andExpect(status().isCreated());
        String bWork = participationOf(student, b);
        post(studentToken, "/api/v1/participations/{id}/withdraw", null, bWork).andExpect(status().isOk());
        String rejoined = read(post(studentToken, "/api/v1/quests/{id}/join", null, a).andExpect(status().isCreated()), "$.id");
        assertThat(rejoined).isEqualTo(joined);
    }

    @Test
    void assignmentsCannotBeJoinedOrWithdrawnFrom() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        User student = student(cohort);
        String assignment = publishedAssignment(teacher, 1, cohort);
        post(token(student), "/api/v1/quests/{id}/join", null, assignment).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_JOINABLE"));
        post(token(student), "/api/v1/participations/{id}/withdraw", null, participationOf(student, assignment))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_WITHDRAW"));
    }

    @Test
    void cancellingAQuestCancelsOpenWorkButKeepsAwardedXp() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        User done = student(cohort);
        User pending = student(cohort);
        String quest = publishedAssignment(teacher, 1, cohort);
        String doneWork = participationOf(done, quest);
        submitWork(token(done), doneWork).andExpect(status().isOk());
        post(teacher, "/api/v1/participations/{id}/approve", "{}", doneWork).andExpect(status().isOk());

        post(teacher, "/api/v1/quests/{id}/cancel", null, quest).andExpect(jsonPath("$.status").value("CANCELLED"));
        get(token(pending), "/api/v1/participations/{id}", participationOf(pending, quest))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(xpOf(done)).isEqualTo(2000);
        assertThat(xpOf(pending)).isZero();
    }

    @Test
    void studentsWhoVerifyLateCatchUpOnOpenAssignments() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        String quest = publishedAssignment(teacher, 1, cohort);

        String email = email();
        post(null, "/api/v1/auth/register", """
                {"email":"%s","password":"%s","firstName":"Late","lastName":"Comer","cohortId":"%s"}
                """.formatted(email, PASSWORD, cohort.getId())).andExpect(status().isAccepted());
        post(null, "/api/v1/auth/verify-email",
                "{\"email\":\"" + email + "\",\"code\":\"" + mail.awaitVerificationCode(email) + "\"}")
                .andExpect(status().isNoContent());

        User late = users.findByEmail(email).orElseThrow();
        assertThat(participationOf(late, quest)).isNotBlank();
    }

    @Test
    void movingCohortSwapsOpenAssignments() throws Exception {
        Cohort from = cohort();
        Cohort to = cohort();
        String teacher = token(teacherOf(from, to));
        User student = student(from);
        String oldQuest = publishedAssignment(teacher, 1, from);
        String newQuest = publishedAssignment(teacher, 1, to);

        patch(token(user(Role.ADMIN)), "/api/v1/admin/users/{id}", "{\"cohortId\":\"" + to.getId() + "\"}", student.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.cohort.id").value(to.getId().toString()));

        String studentToken = token(student);
        get(studentToken, "/api/v1/participations/{id}", participationOf(student, oldQuest))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        get(studentToken, "/api/v1/participations/{id}", participationOf(student, newQuest))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void staffReviewChallengesButNotAssignments() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        String staff = token(user(Role.STAFF));
        User student = student(cohort);
        String studentToken = token(student);

        String challenge = publishedChallenge(teacher, 2, null);
        post(studentToken, "/api/v1/quests/{id}/join", null, challenge).andExpect(status().isCreated());
        String challengeWork = participationOf(student, challenge);
        submitWork(studentToken, challengeWork).andExpect(status().isOk());

        String assignmentWork = participationOf(student, publishedAssignment(teacher, 2, cohort));
        submitWork(studentToken, assignmentWork).andExpect(status().isOk());

        post(staff, "/api/v1/participations/{id}/approve", "{}", assignmentWork).andExpect(status().isNotFound());
        post(staff, "/api/v1/participations/{id}/approve", "{}", challengeWork).andExpect(status().isOk())
                .andExpect(jsonPath("$.xpAwarded").value(3000));
    }

    @Test
    void publishedQuestsOnlyAcceptChangesInTheStudentsFavour() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        String quest = publishedAssignment(teacher, 2, cohort);

        patch(teacher, "/api/v1/quests/{id}", "{\"tier\":5}", quest).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIELD_LOCKED_AFTER_PUBLISH"));
        patch(teacher, "/api/v1/quests/{id}", "{\"dueAt\":\"" + inDays(1) + "\"}", quest)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUE_DATE_CAN_ONLY_EXTEND"));
        patch(teacher, "/api/v1/quests/{id}", "{\"dueAt\":\"" + inDays(14) + "\",\"title\":\"Renamed\"}", quest)
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed"));
        delete(teacher, "/api/v1/quests/{id}", quest).andExpect(status().isConflict());
    }

    @Test
    void boostMultipliesChallengeRewardsBasedOnSubmissionTime() throws Exception {
        String admin = token(user(Role.ADMIN));
        String staff = token(user(Role.STAFF));
        User student = user(Role.STUDENT);
        String studentToken = token(student);
        String challenge = publishedChallenge(staff, 2, null);
        post(studentToken, "/api/v1/quests/{id}/join", null, challenge).andExpect(status().isCreated());
        String work = participationOf(student, challenge);

        String boost = read(post(admin, "/api/v1/admin/boosts", """
                {"multiplier":1.50,"endsAt":"%s","reason":"Exam week"}
                """.formatted(Instant.now().plusSeconds(3600))).andExpect(status().isCreated()), "$.id");
        try {
            submitWork(studentToken, work).andExpect(status().isOk());
        } finally {
            post(admin, "/api/v1/admin/boosts/{id}/stop", null, boost).andExpect(status().isOk());
        }
        // The boost is over by review time, but the student submitted while it was live.
        post(staff, "/api/v1/participations/{id}/approve", "{}", work).andExpect(status().isOk())
                .andExpect(jsonPath("$.xpAwarded").value(4500));
    }
}
