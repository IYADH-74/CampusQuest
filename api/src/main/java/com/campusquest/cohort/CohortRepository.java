package com.campusquest.cohort;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CohortRepository extends JpaRepository<Cohort, UUID> {

    boolean existsByCode(String code);

    List<Cohort> findAllByArchivedFalseOrderByCode();

    List<Cohort> findAllByOrderByCode();

    @Query("select c from Cohort c join c.teachers t where t.id = :teacherId order by c.code")
    List<Cohort> findTaughtBy(UUID teacherId);

    @Query("select count(c) from Cohort c join c.teachers t where t.id = :teacherId and c.id in :cohortIds")
    long countTaughtBy(UUID teacherId, Collection<UUID> cohortIds);

    @Query("select count(c) > 0 from Cohort c join c.teachers t where t.id = :teacherId and c.id = :cohortId")
    boolean isTaughtBy(UUID teacherId, UUID cohortId);

    interface StudentCount {
        UUID getCohortId();

        long getStudents();
    }

    @Query("select u.cohort.id as cohortId, count(u) as students from User u "
            + "where u.role = com.campusquest.user.Role.STUDENT and u.cohort.id is not null group by u.cohort.id")
    List<StudentCount> countStudentsPerCohort();
}
