package com.campusquest.auth.token;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.tokenHash = :tokenHash")
    Optional<OneTimeToken> findByHashForUpdate(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OneTimeToken t where t.userId = :userId and t.purpose = :purpose "
            + "and t.consumedAt is null order by t.createdAt desc")
    List<OneTimeToken> findOpenForUpdate(UUID userId, TokenPurpose purpose);

    @Modifying(flushAutomatically = true)
    @Query("update OneTimeToken t set t.consumedAt = :now where t.userId = :userId "
            + "and t.purpose in :purposes and t.consumedAt is null")
    int consumeAll(UUID userId, Collection<TokenPurpose> purposes, Instant now);
}
