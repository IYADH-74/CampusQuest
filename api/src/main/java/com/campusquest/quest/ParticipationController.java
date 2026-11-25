package com.campusquest.quest;

import com.campusquest.common.web.PageResponse;
import com.campusquest.quest.QuestApi.FeedbackRequiredReviewRequest;
import com.campusquest.quest.QuestApi.ParticipationView;
import com.campusquest.quest.QuestApi.ReviewRequest;
import com.campusquest.quest.QuestApi.SubmitRequest;
import com.campusquest.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParticipationController {

    private final ParticipationService participations;

    public ParticipationController(ParticipationService participations) {
        this.participations = participations;
    }

    @GetMapping("/api/v1/participations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ParticipationView get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return participations.get(me, id);
    }

    @PostMapping("/api/v1/participations/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ParticipationView submit(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody SubmitRequest request) {
        return participations.submit(me, id, request);
    }

    @PostMapping("/api/v1/participations/{id}/withdraw")
    @PreAuthorize("hasRole('STUDENT')")
    public ParticipationView withdraw(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return participations.withdraw(me, id);
    }

    @PostMapping("/api/v1/participations/{id}/approve")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public ParticipationView approve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReviewRequest request) {
        return participations.approve(me, id, request == null ? null : request.feedback());
    }

    @PostMapping("/api/v1/participations/{id}/request-changes")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public ParticipationView requestChanges(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody FeedbackRequiredReviewRequest request) {
        return participations.requestChanges(me, id, request.feedback());
    }

    @PostMapping("/api/v1/participations/{id}/reject")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public ParticipationView reject(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody FeedbackRequiredReviewRequest request) {
        return participations.reject(me, id, request.feedback());
    }

    @GetMapping("/api/v1/me/participations")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<ParticipationView> mine(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) ParticipationStatus status,
            @RequestParam(required = false) QuestKind kind,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return participations.mine(me, status, kind, page, size);
    }

    @GetMapping("/api/v1/reviews/pending")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public PageResponse<ParticipationView> reviewQueue(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return participations.reviewQueue(me, page, size);
    }
}
