package com.campusquest.progression;

import com.campusquest.audit.AuditAction;
import com.campusquest.audit.AuditService;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.PageResponse;
import com.campusquest.progression.ProgressionApi.BoostView;
import com.campusquest.progression.ProgressionApi.CreateBoostRequest;
import com.campusquest.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoostService {

    static final Duration MAX_DURATION = Duration.ofDays(7);
    static final Duration MAX_SCHEDULE_AHEAD = Duration.ofDays(90);
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final XpBoostRepository boosts;
    private final AuditService audit;
    private final Clock clock;

    public BoostService(XpBoostRepository boosts, AuditService audit, Clock clock) {
        this.boosts = boosts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public BoostView create(AuthenticatedUser admin, CreateBoostRequest request) {
        Instant now = clock.instant();
        Instant startsAt = request.startsAt() == null ? now : request.startsAt();
        if (startsAt.isBefore(now.minus(CLOCK_SKEW)) || startsAt.isAfter(now.plus(MAX_SCHEDULE_AHEAD))) {
            throw ApiException.badRequest("INVALID_BOOST_WINDOW", "A boost must start now or within 90 days");
        }
        if (!request.endsAt().isAfter(startsAt) || Duration.between(startsAt, request.endsAt()).compareTo(MAX_DURATION) > 0) {
            throw ApiException.badRequest("INVALID_BOOST_WINDOW", "A boost must last between a moment and 7 days");
        }
        if (boosts.existsOverlapping(startsAt, request.endsAt())) {
            throw ApiException.conflict("BOOST_OVERLAP", "Another boost is scheduled in that window");
        }
        XpBoost boost = new XpBoost(request.multiplier(), startsAt, request.endsAt(), request.reason().strip(), admin.id());
        try {
            boosts.saveAndFlush(boost);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another admin: the exclusion constraint is the source of truth.
            throw ApiException.conflict("BOOST_OVERLAP", "Another boost is scheduled in that window");
        }
        audit.record(AuditAction.BOOST_CREATED, admin.id(), "xp_boost", boost.getId(),
                Map.of("multiplier", boost.getMultiplier().toPlainString(),
                        "startsAt", startsAt.toString(), "endsAt", request.endsAt().toString()));
        return BoostView.of(boost, now);
    }

    @Transactional
    public BoostView stop(AuthenticatedUser admin, UUID boostId) {
        Instant now = clock.instant();
        XpBoost boost = boosts.findById(boostId).orElseThrow(() -> ApiException.notFound("Boost"));
        if (boost.isOver(now)) {
            throw ApiException.conflict("BOOST_ALREADY_OVER", "This boost has already ended");
        }
        boost.stop(now);
        audit.record(AuditAction.BOOST_CANCELLED, admin.id(), "xp_boost", boost.getId());
        return BoostView.of(boost, now);
    }

    @Transactional(readOnly = true)
    public List<BoostView> currentAndUpcoming() {
        Instant now = clock.instant();
        return boosts.findCurrentAndUpcoming(now).stream().map(b -> BoostView.of(b, now)).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BoostView> history(int page, int size) {
        Instant now = clock.instant();
        return PageResponse.of(boosts.findAllByOrderByStartsAtDesc(PageRequest.of(page, size)), b -> BoostView.of(b, now));
    }
}
