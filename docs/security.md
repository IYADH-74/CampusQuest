# Security design

The API is built to survive a hostile client. The rules below are pinned by integration tests: see
`AuthFlowIT`, `AuthorizationRegressionIT`, `QuestGameplayIT` and `ProgressionIT` under `api/src/test/java`.

## Principles

1. **Identity comes from the token, never from the request.** No endpoint accepts a `studentId`,
   `publisherId` or `teacherId` that says who the caller is. "Me" endpoints derive the user from the
   authenticated principal.
2. **Deny by default.** Unmatched routes are `denyAll`. Every controller method declares its roles, and
   every service method re-checks object-level ownership.
3. **Hide existence.** Objects you aren't allowed to see return `404`, not `403`.
4. **Never bind entities.** Requests bind to validated records, and unknown JSON fields are rejected (`400`).
   Responses are explicit view records, so password hashes, tokens and internal flags can't leak.
5. **The database enforces invariants too.** Check constraints, unique constraints, an exclusion
   constraint for boosts, optimistic locking (`@Version`) and pessimistic locks where races matter
   (slots, capacity, XP balance).

## Authentication

| Concern              | Control                                                                                                                                         |
|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Password storage     | Argon2id (Spring Security v5.8 defaults) behind a `DelegatingPasswordEncoder`, so hashes can be upgraded in place                               |
| Password policy      | 12–128 characters, must not contain the email's local part, no single repeated character (NIST 800-63B style: length over composition rules)   |
| Access token         | RS256 JWT, 15 min TTL, `iss`/`aud`/`exp`/`nbf` validated, signed with an RSA key from config. Production refuses to start with ephemeral keys |
| Revocation           | Every token carries the user's `token_version`. Each request reloads the user, so disabling an account or changing or resetting a password locks out existing access tokens immediately |
| Role source          | The role is read from the database on every request. The role claim in the JWT is informational only                                          |
| Refresh token        | 256-bit opaque random value, stored as SHA-256 only, sent in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`             |
| Refresh rotation     | Single use. Each refresh issues a new token in the same family. **Reusing a rotated token revokes the whole family** (stolen-token detection)   |
| Session lifetime     | 7-day sliding window, 30-day absolute cap per family                                                                                              |
| Cookie CSRF          | `SameSite=Strict`, plus refresh and logout require an `X-Requested-With` header (forces a CORS preflight)                                         |

### Account flows

* **Registration** always answers `202` with the same body, whether or not the email exists. If the
  email exists, the owner gets a "someone tried to register" notice instead. The password is hashed
  before the existence check so response timing doesn't leak either.
* **Email verification codes** are 6 digits from `SecureRandom`, stored as HMAC-SHA256 with a server-side
  pepper, valid for 15 minutes, and burned after 5 wrong attempts. The error is the same for an unknown
  email, a wrong code, an expired code or an exhausted code.
* **Password reset and invites** use a 256-bit single-use token in an emailed link, stored as SHA-256 and
  valid for 30 min (reset) or 72 h (invite). Completing a reset bumps `token_version` and revokes every
  refresh token.
* **Login** returns one generic `invalid credentials` error. Unknown emails are still checked against a dummy
  Argon2 hash so the timing matches. "Not verified" and "disabled" are only revealed after a correct password.

## Rate limiting

In-memory fixed windows backed by Caffeine, keyed by client IP and, where relevant, by normalised email.
Limits apply whether or not the account exists, so they can't be used to probe for accounts.

| Endpoint                     | Limit                                   |
|------------------------------|-----------------------------------------|
| All `/api/**`                | 600 / min / IP                          |
| `POST /auth/login`           | 20 / 5 min / IP, 5 failures / 15 min / email |
| `POST /auth/register`        | 5 / hour / IP                           |
| Verification / resend        | 10 / 15 min / IP and / email            |
| Forgot password              | 5 / hour / IP and 3 / hour / email      |

Limits are per instance. To scale horizontally, swap the `RateLimiter` implementation for Redis. Client IP
is `request.getRemoteAddr()`; `X-Forwarded-For` is only honoured when a trusted proxy is configured
(`server.forward-headers-strategy`).

## Transport and headers

`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`, and HSTS over HTTPS. CORS
allows only the configured origins, with credentials. Swagger UI is on in dev only, with its own relaxed CSP.

## Errors and logging

* Every error is an RFC 9457 `application/problem+json` with a stable `code`. No stack traces and no SQL.
* Each request gets a sanitised `X-Request-Id`, which shows up in every log line (MDC) and on 5xx responses.
* No secrets are logged: no SQL parameter logging, no tokens, no codes. Production uses JSON logs.

## Audit trail

Security-relevant events go to `audit_events` (JSONB details) in their own transaction, so failed actions
are recorded too: logins, failed logins, refresh-token reuse, password resets, invites, enable/disable,
roster changes, quest publish/cancel, approvals, rejections, XP adjustments and boosts. Admins can query it.

## Known trade-offs

* Access tokens stay valid for up to 15 minutes after **logout** from a single device. Disable, password
  change and reset revoke them immediately, via `token_version`.
* If a client fires two refreshes at once with the same cookie, the second one looks like token reuse and
  ends the session. Clients must serialise refreshes, and the web client will.
