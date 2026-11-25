package com.campusquest.user;

import com.campusquest.security.AuthenticatedUser;
import com.campusquest.user.UserApi.ChangePasswordRequest;
import com.campusquest.user.UserApi.MeView;
import com.campusquest.user.UserApi.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final MeService me;

    public MeController(MeService me) {
        this.me = me;
    }

    @GetMapping
    public MeView me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return me.me(principal);
    }

    @PatchMapping
    public MeView updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return me.updateProfile(principal, request);
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        me.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
