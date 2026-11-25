package com.campusquest.common.error;

import com.campusquest.common.web.RequestMetadata;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every failure into an RFC 9457 problem with a stable {@code code}. Client errors never echo
 * internals (class names, SQL, parser messages); server errors only expose the request id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail problem = problem(ex.status(), ex.code(), ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        if (ex.retryAfter() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())));
        }
        return ResponseEntity.status(ex.status()).headers(headers).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION", "The resource was modified concurrently. Reload and try again."));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handlePessimisticLock(PessimisticLockingFailureException ex) {
        log.warn("Lock conflict: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT,
                "TRY_AGAIN", "The resource is busy. Try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT,
                "CONFLICT", "The request conflicts with the current state of the resource"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
        problem.setProperty("requestId", RequestMetadata.currentRequestId());
        return ResponseEntity.internalServerError().body(problem);
    }

    // --- Spring MVC built-ins, re-shaped so they never echo parser / converter internals ---

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(e -> Map.of(
                        "field", String.valueOf(r.getMethodParameter().getParameterName()),
                        "message", String.valueOf(e.getDefaultMessage()))))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is malformed or contains unknown fields"));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "A request parameter has an invalid format"));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && (problem.getProperties() == null
                || !problem.getProperties().containsKey("code"))) {
            HttpStatus resolved = HttpStatus.resolve(statusCode.value());
            problem.setProperty("code", resolved != null ? resolved.name() : "ERROR");
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
