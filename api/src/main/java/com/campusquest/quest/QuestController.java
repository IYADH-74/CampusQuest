package com.campusquest.quest;

import com.campusquest.common.web.PageResponse;
import com.campusquest.quest.QuestApi.CreateQuestRequest;
import com.campusquest.quest.QuestApi.ParticipationView;
import com.campusquest.quest.QuestApi.QuestView;
import com.campusquest.quest.QuestApi.UpdateQuestRequest;
import com.campusquest.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quests")
public class QuestController {

    private final QuestService quests;
    private final ParticipationService participations;

    public QuestController(QuestService quests, ParticipationService participations) {
        this.quests = quests;
        this.participations = participations;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public ResponseEntity<QuestView> create(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody CreateQuestRequest request) {
        QuestView created = quests.create(me, request);
        return ResponseEntity.created(URI.create("/api/v1/quests/" + created.id())).body(created);
    }

    @GetMapping("/challenges")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<QuestView> openChallenges(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) QuestCategory category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return quests.openChallenges(me, category, page, size);
    }

    @GetMapping("/authored")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public PageResponse<QuestView> authored(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) QuestStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return quests.authoredBy(me, status, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public QuestView get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return quests.get(me, id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public QuestView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestRequest request) {
        return quests.update(me, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public ResponseEntity<Void> deleteDraft(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        quests.deleteDraft(me, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public QuestView publish(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return quests.publish(me, id);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public QuestView close(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return quests.close(me, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF')")
    public QuestView cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return quests.cancel(me, id);
    }

    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ParticipationView> join(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        ParticipationView joined = participations.join(me, id);
        return ResponseEntity.created(URI.create("/api/v1/participations/" + joined.id())).body(joined);
    }

    @GetMapping("/{id}/participations")
    @PreAuthorize("hasAnyRole('TEACHER', 'STAFF', 'ADMIN')")
    public PageResponse<ParticipationView> participations(@AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID id,
            @RequestParam(required = false) ParticipationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return participations.forQuest(me, id, status, page, size);
    }
}
