package com.campusquest.quest;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ParticipationRepository extends JpaRepository<Participation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participation p where p.id = :id")
    Optional<Participation> findByIdForUpdate(UUID id);

    @EntityGraph(attributePaths = {"quest", "quest.author", "student", "reviewer"})
    @Query("select p from Participation p where p.id = :id")
    Optional<Participation> findDetailed(UUID id);

    Optional<Participation> findByQuestIdAndStudentId(UUID questId, UUID studentId);

    @Query("select count(p) from Participation p where p.student.id = :studentId "
            + "and p.quest.kind = com.campusquest.quest.QuestKind.CHALLENGE and p.status in :statuses")
    long countChallengesForStudent(UUID studentId, Set<ParticipationStatus> statuses);

    long countByQuestIdAndStatusNotIn(UUID questId, Set<ParticipationStatus> statuses);

    interface QuestCount {
        UUID getQuestId();

        long getParticipants();
    }

    @Query("select p.quest.id as questId, count(p) as participants from Participation p "
            + "where p.quest.id in :questIds and p.status not in :excluded group by p.quest.id")
    List<QuestCount> countParticipants(Collection<UUID> questIds, Set<ParticipationStatus> excluded);

    @Query("select p from Participation p where p.student.id = :studentId and p.quest.id in :questIds")
    List<Participation> findForStudentAndQuests(UUID studentId, Collection<UUID> questIds);

    @Query("select p.quest.id from Participation p where p.student.id = :studentId and p.quest.id in :questIds")
    Set<UUID> findQuestIdsForStudent(UUID studentId, Collection<UUID> questIds);

    @Query("select p from Participation p where p.quest.id = :questId and p.status in :statuses")
    List<Participation> findByQuestAndStatuses(UUID questId, Set<ParticipationStatus> statuses);

    @Query("""
            select p from Participation p join fetch p.quest q
            where p.student.id = :studentId and p.status = com.campusquest.quest.ParticipationStatus.ACTIVE
              and q.kind = com.campusquest.quest.QuestKind.ASSIGNMENT
            """)
    List<Participation> findActiveAssignmentsForStudent(UUID studentId);

    @EntityGraph(attributePaths = {"quest", "quest.author", "student", "reviewer"})
    @Query("""
            select p from Participation p
            where p.student.id = :studentId
              and (:status is null or p.status = :status)
              and (:kind is null or p.quest.kind = :kind)
            order by p.joinedAt desc, p.id
            """)
    Page<Participation> findForStudent(UUID studentId, ParticipationStatus status, QuestKind kind, Pageable pageable);

    @EntityGraph(attributePaths = {"quest", "quest.author", "student", "reviewer"})
    @Query("""
            select p from Participation p
            where p.quest.id = :questId and (:status is null or p.status = :status)
            order by p.joinedAt, p.id
            """)
    Page<Participation> findForQuest(UUID questId, ParticipationStatus status, Pageable pageable);

    /** Submissions waiting on this reviewer: their own quests, plus every challenge if they are staff. */
    @EntityGraph(attributePaths = {"quest", "quest.author", "student"})
    @Query("""
            select p from Participation p
            where p.status = com.campusquest.quest.ParticipationStatus.SUBMITTED
              and (p.quest.author.id = :reviewerId
                   or (:includeAllChallenges = true and p.quest.kind = com.campusquest.quest.QuestKind.CHALLENGE))
            order by p.submittedAt, p.id
            """)
    Page<Participation> findReviewQueue(UUID reviewerId, boolean includeAllChallenges, Pageable pageable);
}
