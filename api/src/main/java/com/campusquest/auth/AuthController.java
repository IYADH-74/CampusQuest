package com.campusquest.auth;

import com.campusquest.auth.AuthApi.EmailRequest;
import com.campusquest.auth.AuthApi.LoginRequest;
import com.campusquest.auth.AuthApi.MessageResponse;
import com.campusquest.auth.AuthApi.RegisterRequest;
import com.campusquest.auth.AuthApi.ResetPasswordRequest;
import com.campusquest.auth.AuthApi.SessionResponse;
import com.campusquest.auth.AuthApi.VerifyEmailRequest;
import com.campusquest.common.error.ApiException;
import com.campusquest.common.web.RequestMetadata;
import com.campusquest.config.SecuritySettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Endpoints that read or set the refresh cookie require an
 * {@code X-Requested-With} header: browsers can't attach custom headers cross-site without a CORS preflight,
 * and that preflight fails for foreign origins. This sits on top of {@code SameSite=Strict}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String CSRF_HEADER = "X-Requested-With";
    private static final MessageResponse REGISTERED = new MessageResponse(
            "If this address can be registered, a verification code is on its way.");
    private static final MessageResponse CHECK_INBOX = new MessageResponse(
            "If an account matches, you will receive an email shortly.");

    private final AuthService auth;
    private final SecuritySettings.RefreshToken cookieSettings;

    public AuthController(AuthService auth, SecuritySettings settings) {
        this.auth = auth;
        this.cookieSettings = settings.refreshToken();
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest http) {
        auth.register(request, RequestMetadata.of(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(REGISTERED);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
        auth.verifyEmail(request, RequestMetadata.of(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email/resend")
    public ResponseEntity<MessageResponse> resendVerification(@Valid @RequestBody EmailRequest request,
            HttpServletRequest http) {
        auth.resendVerification(request.email(), RequestMetadata.of(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CHECK_INBOX);
    }

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader) {
        requireCsrfHeader(csrfHeader);
        AuthService.IssuedSession session = auth.login(request, RequestMetadata.of(http));
        return withRefreshCookie(session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(HttpServletRequest http,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
            @CookieValue(name = "${campusquest.security.refresh-token.cookie-name}", required = false) String refreshToken) {
        requireCsrfHeader(csrfHeader);
        AuthService.IssuedSession session = auth.refresh(refreshToken, RequestMetadata.of(http));
        return withRefreshCookie(session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
            @CookieValue(name = "${campusquest.security.refresh-token.cookie-name}", required = false) String refreshToken) {
        requireCsrfHeader(csrfHeader);
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody EmailRequest request,
            HttpServletRequest http) {
        auth.forgotPassword(request.email(), RequestMetadata.of(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CHECK_INBOX);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest http) {
        auth.resetPassword(request, RequestMetadata.of(http));
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<SessionResponse> withRefreshCookie(AuthService.IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), cookieSettings.ttl()).toString())
                .body(session.body());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieSettings.cookieName(), value)
                .httpOnly(true)
                .secure(cookieSettings.cookieSecure())
                .sameSite("Strict")
                .path(cookieSettings.cookiePath())
                .maxAge(maxAge)
                .build();
    }

    private static void requireCsrfHeader(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.forbidden("CSRF_HEADER_REQUIRED", "Missing " + CSRF_HEADER + " header");
        }
    }
}
