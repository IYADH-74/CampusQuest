package com.campusquest.user;

import com.campusquest.common.web.PageResponse;
import com.campusquest.security.AuthenticatedUser;
import com.campusquest.user.UserApi.AdminUserView;
import com.campusquest.user.UserApi.InviteUserRequest;
import com.campusquest.user.UserApi.UpdateUserRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService admin;

    public AdminUserController(AdminUserService admin) {
        this.admin = admin;
    }

    @GetMapping
    public PageResponse<AdminUserView> search(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return admin.search(role, status, q, page, size);
    }

    @GetMapping("/{id}")
    public AdminUserView get(@PathVariable UUID id) {
        return admin.get(id);
    }

    @PostMapping
    public ResponseEntity<AdminUserView> invite(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody InviteUserRequest request) {
        AdminUserView created = admin.invite(me, request);
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public AdminUserView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return admin.update(me, id, request);
    }

    @PostMapping("/{id}/disable")
    public AdminUserView disable(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return admin.disable(me, id);
    }

    @PostMapping("/{id}/enable")
    public AdminUserView enable(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return admin.enable(me, id);
    }

    @PostMapping("/{id}/resend-invite")
    public ResponseEntity<Void> resendInvite(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        admin.resendInvite(me, id);
        return ResponseEntity.accepted().build();
    }
}
