package com.campusquest.user;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Minimal per-request view used to validate access tokens. */
    interface AuthSnapshot {
        String getEmail();

        Role getRole();

        AccountStatus getStatus();

        int getTokenVersion();
    }

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select u.email as email, u.role as role, u.status as status, u.tokenVersion as tokenVersion "
            + "from User u where u.id = :id")
    Optional<AuthSnapshot> findAuthSnapshot(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(UUID id);

    @Query("select u from User u where u.cohort.id in :cohortIds "
            + "and u.role = com.campusquest.user.Role.STUDENT "
            + "and u.status = com.campusquest.user.AccountStatus.ACTIVE")
    List<User> findActiveStudentsInCohorts(Collection<UUID> cohortIds);

    @Query("select u from User u where u.cohort.id = :cohortId and u.role = com.campusquest.user.Role.STUDENT")
    Page<User> findStudentsInCohort(UUID cohortId, Pageable pageable);

    long countByRoleAndStatus(Role role, AccountStatus status);

    @Query("select count(u) from User u where u.role = com.campusquest.user.Role.STUDENT "
            + "and u.status = com.campusquest.user.AccountStatus.ACTIVE and u.xp > :xp")
    long countActiveStudentsWithMoreXp(long xp);
}
