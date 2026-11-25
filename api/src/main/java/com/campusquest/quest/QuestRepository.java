package com.campusquest.quest;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface QuestRepository extends JpaRepository<Quest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quest q where q.id = :id")
    Optional<Quest> findByIdForUpdate(UUID id);

    @EntityGraph(attributePaths = {"author"})
    @Query("""
            select q from Quest q
            where q.kind = com.campusquest.quest.QuestKind.CHALLENGE
              and q.status = com.campusquest.quest.QuestStatus.PUBLISHED
              and (q.dueAt is null or q.dueAt > :now)
              and (:category is null or q.category = :category)
            order by q.publishedAt desc, q.id
            """)
    Page<Quest> findOpenChallenges(Instant now, QuestCategory category, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    @Query("""
            select q from Quest q
            where q.author.id = :authorId and (:status is null or q.status = :status)
            order by q.createdAt desc, q.id
            """)
    Page<Quest> findAuthoredBy(UUID authorId, QuestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    @Query("""
            select q from Quest q
            where (:status is null or q.status = :status) and (:kind is null or q.kind = :kind)
            order by q.createdAt desc, q.id
            """)
    Page<Quest> search(QuestStatus status, QuestKind kind, Pageable pageable);

    @Query("""
            select q from Quest q join q.targetCohorts c
            where c.id = :cohortId
              and q.kind = com.campusquest.quest.QuestKind.ASSIGNMENT
              and q.status = com.campusquest.quest.QuestStatus.PUBLISHED
              and q.dueAt > :now
            """)
    List<Quest> findOpenAssignmentsForCohort(UUID cohortId, Instant now);
}
