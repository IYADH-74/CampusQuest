package com.campusquest.progression;

import com.campusquest.common.web.PageResponse;
import com.campusquest.progression.ProgressionApi.BoostView;
import com.campusquest.progression.ProgressionApi.CreateBoostRequest;
import com.campusquest.progression.ProgressionApi.LeaderboardEntry;
import com.campusquest.progression.ProgressionApi.XpAdjustmentRequest;
import com.campusquest.progression.ProgressionApi.XpTransactionView;
import com.campusquest.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgressionController {

    private final LeaderboardService leaderboard;
    private final BoostService boosts;
    private final XpAdjustmentService adjustments;

    public ProgressionController(LeaderboardService leaderboard, BoostService boosts, XpAdjustmentService adjustments) {
        this.leaderboard = leaderboard;
        this.boosts = boosts;
        this.adjustments = adjustments;
    }

    @GetMapping("/api/v1/leaderboard")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<LeaderboardEntry> leaderboard(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) UUID cohortId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return leaderboard.page(me.id(), cohortId, page, size);
    }

    @GetMapping("/api/v1/boosts")
    @PreAuthorize("isAuthenticated()")
    public List<BoostView> currentAndUpcomingBoosts() {
        return boosts.currentAndUpcoming();
    }

    @GetMapping("/api/v1/me/xp-transactions")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<XpTransactionView> myXpHistory(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adjustments.history(me.id(), page, size);
    }

    // --- admin ------------------------------------------------------------------------------------

    @GetMapping("/api/v1/admin/boosts")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<BoostView> boostHistory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return boosts.history(page, size);
    }

    @PostMapping("/api/v1/admin/boosts")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BoostView createBoost(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody CreateBoostRequest request) {
        return boosts.create(me, request);
    }

    @PostMapping("/api/v1/admin/boosts/{id}/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public BoostView stopBoost(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return boosts.stop(me, id);
    }

    @PostMapping("/api/v1/admin/students/{studentId}/xp-adjustments")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public XpTransactionView adjustXp(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID studentId,
            @Valid @RequestBody XpAdjustmentRequest request) {
        return adjustments.adjust(me, studentId, request);
    }

    @GetMapping("/api/v1/admin/students/{studentId}/xp-transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<XpTransactionView> studentXpHistory(@PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return adjustments.history(studentId, page, size);
    }
}
