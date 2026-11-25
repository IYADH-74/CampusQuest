package com.campusquest.dev;

import com.campusquest.cohort.Cohort;
import com.campusquest.cohort.CohortRepository;
import com.campusquest.quest.EnrollmentService;
import com.campusquest.quest.Quest;
import com.campusquest.quest.QuestCategory;
import com.campusquest.quest.QuestKind;
import com.campusquest.quest.QuestRepository;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for local development. Only active under the {@code dev} profile with {@code campusquest.dev.seed=true},
 * and only on an empty database.
 */
@Component
@Profile("dev")
@ConditionalOnBooleanProperty("campusquest.dev.seed")
public class DevDataSeeder implements ApplicationRunner {

    static final String DEV_PASSWORD = "CampusQuest-dev-2026";
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository users;
    private final CohortRepository cohorts;
    private final QuestRepository quests;
    private final EnrollmentService enrollment;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevDataSeeder(UserRepository users, CohortRepository cohorts, QuestRepository quests,
            EnrollmentService enrollment, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.cohorts = cohorts;
        this.quests = quests;
        this.enrollment = enrollment;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String hash = passwordEncoder.encode(DEV_PASSWORD);
        Instant now = clock.instant();

        Cohort cs2a = cohorts.save(new Cohort("CS-2A", "Computer Science, year 2, group A"));
        Cohort cs2b = cohorts.save(new Cohort("CS-2B", "Computer Science, year 2, group B"));

        users.save(User.active("admin@campusquest.dev", hash, "Ada", "Admin", Role.ADMIN));
        User staff = User.active("staff@campusquest.dev", hash, "Sam", "Staff", Role.STAFF);
        staff.assignDepartment("Student Life");
        users.save(staff);
        User teacher = User.active("teacher@campusquest.dev", hash, "Tariq", "Teacher", Role.TEACHER);
        teacher.assignSubject("Distributed Systems");
        users.save(teacher);
        cs2a.replaceTeachers(List.of(teacher));
        cs2b.replaceTeachers(List.of(teacher));

        String[][] students = {
            {"lina@campusquest.dev", "Lina", "Haddad"}, {"omar@campusquest.dev", "Omar", "Ben Ali"},
            {"sara@campusquest.dev", "Sara", "Mansour"}, {"youssef@campusquest.dev", "Youssef", "Trabelsi"},
        };
        for (int i = 0; i < students.length; i++) {
            User student = User.active(students[i][0], hash, students[i][1], students[i][2], Role.STUDENT);
            student.assignCohort(i % 2 == 0 ? cs2a : cs2b);
            users.save(student);
        }

        Quest assignment = new Quest(QuestKind.ASSIGNMENT, QuestCategory.COURSEWORK, "Raft leader election lab",
                "Implement leader election for a 5-node Raft cluster and document the failure cases you tested.", 3, teacher);
        assignment.retarget(List.of(cs2a, cs2b));
        assignment.changeDueAt(now.plus(Duration.ofDays(10)));
        assignment.publish(now);
        quests.save(assignment);
        enrollment.enrollTargetCohorts(assignment);

        Quest certification = new Quest(QuestKind.CHALLENGE, QuestCategory.CERTIFICATION, "Get AWS Cloud Practitioner",
                "Pass the AWS Certified Cloud Practitioner exam and submit your credential link.", 4, staff);
        certification.publish(now);
        quests.save(certification);

        Quest hackathon = new Quest(QuestKind.CHALLENGE, QuestCategory.COMPETITION, "Campus CTF night",
                "Join the campus capture-the-flag night and submit your team's write-up.", 2, teacher);
        hackathon.changeMaxParticipants(30);
        hackathon.changeDueAt(now.plus(Duration.ofDays(21)));
        hackathon.publish(now);
        quests.save(hackathon);

        log.info("Seeded dev data. Every account uses the password '{}' (dev profile only): "
                + "admin@, staff@, teacher@, lina@, omar@, sara@, youssef@campusquest.dev", DEV_PASSWORD);
    }
}
