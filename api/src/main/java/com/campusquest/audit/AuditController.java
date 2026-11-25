package com.campusquest.audit;

import com.campusquest.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditService.AuditEventView> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return audit.search(action, actorId, targetId, page, size);
    }
}
