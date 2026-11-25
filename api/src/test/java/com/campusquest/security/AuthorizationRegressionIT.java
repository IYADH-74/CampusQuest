package com.campusquest.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusquest.cohort.Cohort;
import com.campusquest.support.IntegrationTest;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * One test per legacy vulnerability (see docs/legacy-vulnerabilities.md), so regressions fail loudly by name.
 */
class AuthorizationRegressionIT extends IntegrationTest {

    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtKeys jwtKeys;

    @Test
    void lv01_studentsCannotApproveTheirOwnSubmission() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        User student = student(cohort);
        String studentToken = token(student);
        String questId = publishedAssignment(teacher, 5, cohort);
        String participation = participationOf(student, questId);
        submitWork(studentToken, participation).andExpect(status().isOk());

        post(studentToken, "/api/v1/participations/{id}/approve", "{}", participation)
                .andExpect(status().isForbidden());
        assertThat(xpOf(student)).isZero();
    }

    @Test
    void lv02_lv11_studentsCannotSeeOrTouchAnotherStudentsWork() throws Exception {
        Cohort cohort = cohort();
        String teacher = token(teacherOf(cohort));
        User alice = student(cohort);
        User mallory = student(cohort);
        String questId = publishedAssignment(teacher, 1, cohort);
        String alicesWork = participationOf(alice, questId);
        String malloryToken = token(mallory);

        get(malloryToken, "/api/v1/participations/{id}", alicesWork).andExpect(status().isNotFound());
        submitWork(malloryToken, alicesWork).andExpect(status().isNotFound());
        post(malloryToken, "/api/v1/participations/{id}/withdraw", null, alicesWork).andExpect(status().isNotFound());
    }

    @Test
    void lv02_identityParametersAreIgnored() throws Exception {
        User alice = user(Role.STUDENT);
        User mallory = user(Role.STUDENT);
        String staff = token(user(Role.STAFF));
        String challenge = publishedChallenge(staff, 1, null);

        // The legacy API trusted ?studentId=. v3 has no such parameter: the caller is always the token's subject.
        post(token(mallory), "/api/v1/quests/{id}/join?studentId=" + alice.getId(), null, challenge)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.student.id").value(mallory.getId().toString()));
        assertThat(jdbc.sql("select count(*) from quest_participations where student_id = ?")
                .params(alice.getId()).query(Long.class).single()).isZero();
    }

    @Test
    void lv03_publishingCannotSetServerControlledFields() throws Exception {
        String staff = token(user(Role.STAFF));
        post(staff, "/api/v1/quests", """
                {"kind":"CHALLENGE","category":"EVENT","title":"Hack","description":"x","tier":1,
                 "authorId":"00000000-0000-0000-0000-000000000000","status":"PUBLISHED"}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void lv04_studentsCannotManageCohorts() throws Exception {
        Cohort cohort = cohort();
        String student = token(student(cohort));
        post(student, "/api/v1/cohorts", "{\"code\":\"HACKED\",\"name\":\"x\"}").andExpect(status().isForbidden());
        patch(student, "/api/v1/cohorts/{id}", "{\"name\":\"pwned\"}", cohort.getId()).andExpect(status().isForbidden());
        post(student, "/api/v1/cohorts/{id}/archive", null, cohort.getId()).andExpect(status().isForbidden());
    }

    @Test
    void lv08_adminViewsNeverContainSecrets() throws Exception {
        user(Role.STUDENT);
        String json = body(get(token(user(Role.ADMIN)), "/api/v1/admin/users?size=200").andExpect(status().isOk()));
        assertThat(json).doesNotContainIgnoringCase("password").doesNotContain("$argon2")
                .doesNotContainIgnoringCase("tokenVersion").doesNotContainIgnoringCase("verificationCode")
                .doesNotContainIgnoringCase("\"tokens\"");
    }

    @Test
    void adminApiIsForAdminsOnly() throws Exception {
        for (Role role : List.of(Role.STUDENT, Role.TEACHER, Role.STAFF)) {
            get(token(user(role)), "/api/v1/admin/users").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        get(null, "/api/v1/admin/users").andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aRoleClaimInTheTokenGrantsNothing() throws Exception {
        User student = user(Role.STUDENT);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("campusquest").audience(List.of("campusquest-api")).subject(student.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("tv", student.getTokenVersion()).claim("role", "ADMIN").build();
        String escalated = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtKeys.keyId()).build(), claims)).getTokenValue();

        get(escalated, "/api/v1/admin/users").andExpect(status().isForbidden());
    }

    @Test
    void tokensForAnotherAudienceAreRejected() throws Exception {
        User admin = user(Role.ADMIN);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("campusquest").audience(List.of("some-other-service")).subject(admin.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("tv", 0).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtKeys.keyId()).build(), claims)).getTokenValue();
        get(token, "/api/v1/admin/users").andExpect(status().isUnauthorized());
    }

    @Test
    void teachersCanOnlyAssignToCohortsTheyTeach() throws Exception {
        Cohort mine = cohort();
        Cohort someoneElses = cohort();
        String teacher = token(teacherOf(mine));
        post(teacher, "/api/v1/quests", """
                {"kind":"ASSIGNMENT","title":"x","description":"x","tier":1,"dueAt":"%s","cohortIds":["%s"]}
                """.formatted(inDays(3), someoneElses.getId()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_TEACHING_COHORT"));
    }

    @Test
    void staffCannotSetAssignmentsAndAdminsCannotAuthorAnything() throws Exception {
        Cohort cohort = cohort();
        post(token(user(Role.STAFF)), "/api/v1/quests", """
                {"kind":"ASSIGNMENT","title":"x","description":"x","tier":1,"dueAt":"%s","cohortIds":["%s"]}
                """.formatted(inDays(3), cohort.getId()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ONLY_TEACHERS_ASSIGN"));
        post(token(user(Role.ADMIN)), "/api/v1/quests", """
                {"kind":"CHALLENGE","category":"EVENT","title":"x","description":"x","tier":1}
                """).andExpect(status().isForbidden());
    }

    @Test
    void draftsAreInvisibleToEveryoneButTheirAuthor() throws Exception {
        String teacher = token(user(Role.TEACHER));
        String draft = createChallenge(teacher, 2, null);
        get(token(user(Role.STUDENT)), "/api/v1/quests/{id}", draft).andExpect(status().isNotFound());
        get(token(user(Role.TEACHER)), "/api/v1/quests/{id}", draft).andExpect(status().isNotFound());
        get(teacher, "/api/v1/quests/{id}", draft).andExpect(status().isOk());
    }

    @Test
    void teachersCannotReviewOrManageAnotherTeachersQuest() throws Exception {
        Cohort cohort = cohort();
        String author = token(teacherOf(cohort));
        String otherTeacher = token(teacherOf(cohort));
        User student = student(cohort);
        String questId = publishedAssignment(author, 2, cohort);
        String participation = participationOf(student, questId);
        submitWork(token(student), participation).andExpect(status().isOk());

        post(otherTeacher, "/api/v1/participations/{id}/approve", "{}", participation).andExpect(status().isNotFound());
        post(otherTeacher, "/api/v1/quests/{id}/cancel", null, questId).andExpect(status().isNotFound());
        assertThat(xpOf(student)).isZero();
    }

    @Test
    void validationErrorsDoNotEchoInternals() throws Exception {
        String json = body(get(token(user(Role.STUDENT)), "/api/v1/quests/{id}", "not-a-uuid")
                .andExpect(status().isBadRequest()));
        assertThat(json).doesNotContain("java.").doesNotContain("Exception").contains("INVALID_PARAMETER");
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        get(token(user(Role.STUDENT)), "/api/v1/me")
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'none'")))
                .andExpect(header().exists("X-Request-Id"));
    }
}
