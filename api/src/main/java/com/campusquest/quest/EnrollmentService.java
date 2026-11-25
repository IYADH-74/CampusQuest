package com.campusquest.quest;

import com.campusquest.cohort.Cohort;
import com.campusquest.user.User;
import com.campusquest.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps assignment participations in sync with cohort membership:
 * publishing enrolls the cohort, activation enrolls late joiners, and moving cohort swaps open work.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EnrollmentService {

    private final QuestRepository quests;
    private final ParticipationRepository participations;
    private final UserRepository users;
    private final Clock clock;

    public EnrollmentService(QuestRepository quests, ParticipationRepository participations, UserRepository users,
            Clock clock) {
        this.quests = quests;
        this.participations = participations;
        this.users = users;
        this.clock = clock;
    }

    /** Called when an assignment is published: every active student in the target cohorts gets it. */
    public int enrollTargetCohorts(Quest assignment) {
        List<UUID> cohortIds = assignment.getTargetCohorts().stream().map(Cohort::getId).toList();
        if (cohortIds.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        List<Participation> created = users.findActiveStudentsInCohorts(cohortIds).stream()
                .map(student -> new Participation(assignment, student, now))
                .toList();
        participations.saveAll(created);
        return created.size();
    }

    /** Called when a student becomes active: catch up on open assignments for their cohort. */
    public void enrollInOpenAssignments(User student) {
        if (!student.isStudent() || !student.isActive() || student.getCohort() == null) {
            return;
        }
        Instant now = clock.instant();
        List<Quest> open = quests.findOpenAssignmentsForCohort(student.getCohort().getId(), now);
        if (open.isEmpty()) {
            return;
        }
        Set<UUID> already = participations.findQuestIdsForStudent(student.getId(),
                open.stream().map(Quest::getId).toList());
        participations.saveAll(open.stream()
                .filter(q -> !already.contains(q.getId()))
                .map(q -> new Participation(q, student, now))
                .toList());
    }

    /**
     * Moves a student to another cohort. Untouched work for assignments the new cohort doesn't share is
     * cancelled with no penalty; submitted work stays in review; the new cohort's open assignments are added.
     */
    public void moveStudent(User student, Cohort newCohort) {
        Cohort previous = student.getCohort();
        student.assignCohort(newCohort);
        if (Objects.equals(previous, newCohort)) {
            return;
        }
        for (Participation p : participations.findActiveAssignmentsForStudent(student.getId())) {
            if (newCohort == null || !p.getQuest().getTargetCohorts().contains(newCohort)) {
                p.cancelIfOpen();
            }
        }
        enrollInOpenAssignments(student);
    }
}
