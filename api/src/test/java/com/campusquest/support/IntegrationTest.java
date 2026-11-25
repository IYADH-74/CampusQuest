package com.campusquest.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusquest.cohort.Cohort;
import com.campusquest.cohort.CohortRepository;
import com.campusquest.progression.LevelCurve;
import com.campusquest.security.AccessTokenService;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Full-stack tests against a real PostgreSQL (Testcontainers) through the real security filter chain.
 * Every test creates its own users and cohorts, so tests never depend on each other's data.
 * Each request comes from a random IP so per-IP rate limits only bite in the tests that target them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery-staple";
    private static volatile String passwordHash;

    @Autowired protected MockMvc mvc;
    @Autowired protected RecordingMailGateway mail;
    @Autowired protected UserRepository users;
    @Autowired protected CohortRepository cohorts;
    @Autowired protected AccessTokenService accessTokens;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected LevelCurve curve;
    @Autowired protected JdbcClient jdbc;

    // --- fixtures ---------------------------------------------------------------------------------

    protected static String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected static String email() {
        return "u" + uniq() + "@uni.test";
    }

    protected Cohort cohort() {
        return cohorts.save(new Cohort("C" + uniq().toUpperCase(), "Cohort " + uniq()));
    }

    protected User user(Role role) {
        return users.save(User.active(email(), hash(), "Test", "User" + uniq(), role));
    }

    protected User student(Cohort cohort) {
        User student = User.active(email(), hash(), "Stu", "Dent" + uniq(), Role.STUDENT);
        student.assignCohort(cohort);
        return users.save(student);
    }

    protected User teacherOf(Cohort... taught) {
        User teacher = user(Role.TEACHER);
        for (Cohort cohort : taught) {
            jdbc.sql("insert into teaching_assignments (cohort_id, teacher_id) values (?, ?)")
                    .params(cohort.getId(), teacher.getId()).update();
        }
        return teacher;
    }

    protected String token(User user) {
        return accessTokens.issue(users.findById(user.getId()).orElseThrow()).value();
    }

    protected void setXp(User student, long xp) {
        jdbc.sql("update users set xp = ?, level = ? where id = ?")
                .params(xp, curve.levelFor(xp), student.getId()).update();
    }

    protected long xpOf(User student) {
        return jdbc.sql("select xp from users where id = ?").params(student.getId()).query(Long.class).single();
    }

    protected String participationOf(User student, String questId) {
        return jdbc.sql("select id from quest_participations where student_id = ? and quest_id = ?")
                .params(student.getId(), UUID.fromString(questId)).query(UUID.class).single().toString();
    }

    protected static String inDays(int days) {
        return Instant.now().plus(Duration.ofDays(days)).toString();
    }

    private String hash() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        return passwordHash;
    }

    // --- quest helpers ----------------------------------------------------------------------------

    protected String createAssignment(String teacherToken, int tier, Cohort... targets) throws Exception {
        String ids = Arrays.stream(targets).map(c -> "\"" + c.getId() + "\"").collect(Collectors.joining(","));
        return read(post(teacherToken, "/api/v1/quests", """
                {"kind":"ASSIGNMENT","title":"Lab %s","description":"Do the lab","tier":%d,"dueAt":"%s","cohortIds":[%s]}
                """.formatted(uniq(), tier, inDays(7), ids)).andExpect(status().isCreated()), "$.id");
    }

    protected String createChallenge(String authorToken, int tier, String extraFields) throws Exception {
        String extra = extraFields == null || extraFields.isBlank() ? "" : "," + extraFields;
        return read(post(authorToken, "/api/v1/quests", """
                {"kind":"CHALLENGE","category":"COMPETITION","title":"Challenge %s","description":"Win it","tier":%d%s}
                """.formatted(uniq(), tier, extra)).andExpect(status().isCreated()), "$.id");
    }

    protected String publishedAssignment(String teacherToken, int tier, Cohort... targets) throws Exception {
        String id = createAssignment(teacherToken, tier, targets);
        post(teacherToken, "/api/v1/quests/{id}/publish", null, id).andExpect(status().isOk());
        return id;
    }

    protected String publishedChallenge(String authorToken, int tier, String extraFields) throws Exception {
        String id = createChallenge(authorToken, tier, extraFields);
        post(authorToken, "/api/v1/quests/{id}/publish", null, id).andExpect(status().isOk());
        return id;
    }

    protected ResultActions submitWork(String studentToken, String participationId) throws Exception {
        return post(studentToken, "/api/v1/participations/{id}/submit",
                "{\"text\":\"My work\",\"url\":\"https://example.org/work\"}", participationId);
    }

    // --- HTTP -------------------------------------------------------------------------------------

    protected ResultActions get(String token, String url, Object... vars) throws Exception {
        return mvc.perform(prepare(MockMvcRequestBuilders.get(url, vars), token, null));
    }

    protected ResultActions post(String token, String url, String body, Object... vars) throws Exception {
        return mvc.perform(prepare(MockMvcRequestBuilders.post(url, vars), token, body));
    }

    protected ResultActions patch(String token, String url, String body, Object... vars) throws Exception {
        return mvc.perform(prepare(MockMvcRequestBuilders.patch(url, vars), token, body));
    }

    protected ResultActions delete(String token, String url, Object... vars) throws Exception {
        return mvc.perform(prepare(MockMvcRequestBuilders.delete(url, vars), token, null));
    }

    protected MockHttpServletRequestBuilder prepare(MockHttpServletRequestBuilder request, String token, String body) {
        request.with(fromIp(randomIp())).accept(MediaType.APPLICATION_JSON);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request;
    }

    protected static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    protected static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    protected static String read(ResultActions result, String jsonPath) throws Exception {
        Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
        return value == null ? null : value.toString();
    }

    protected static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
