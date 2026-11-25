package com.campusquest.quest;

import com.campusquest.common.web.PageResponse;
import com.campusquest.quest.QuestApi.QuestView;
import com.campusquest.security.AuthenticatedUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only oversight of every quest on the platform. */
@RestController
@RequestMapping("/api/v1/admin/quests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestController {

    private final QuestService quests;

    public AdminQuestController(QuestService quests) {
        this.quests = quests;
    }

    @GetMapping
    public PageResponse<QuestView> search(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) QuestStatus status,
            @RequestParam(required = false) QuestKind kind,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return quests.search(me, status, kind, page, size);
    }
}
