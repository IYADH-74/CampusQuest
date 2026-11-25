package com.campusquest.progression;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface XpBoostRepository extends JpaRepository<XpBoost, UUID> {

    @Query("select b from XpBoost b where b.cancelledAt is null and b.startsAt <= :at and b.endsAt > :at")
    Optional<XpBoost> findActiveAt(Instant at);

    @Query("select count(b) > 0 from XpBoost b where b.cancelledAt is null and b.startsAt < :end and b.endsAt > :start")
    boolean existsOverlapping(Instant start, Instant end);

    @Query("select b from XpBoost b where b.cancelledAt is null and b.endsAt > :now order by b.startsAt")
    List<XpBoost> findCurrentAndUpcoming(Instant now);

    Page<XpBoost> findAllByOrderByStartsAtDesc(Pageable pageable);
}
