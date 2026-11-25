# Legacy (v2) vulnerability inventory

The original API is kept verbatim in [`legacy/api`](../legacy/api) and on the `legacy-v2` branch. This is
the catalogue of what was wrong with it: a record of why v3 exists, and seed material for a future CTF.
Each item names the v3 control that closes it.

Paths below are relative to `legacy/api/src/main/java/com/apex/campu_quest_v2/`.

| ID    | Severity | Class (OWASP API Top 10) | Finding | Where | v3 control |
|-------|----------|--------------------------|---------|-------|------------|
| LV-01 | Critical | API5 Broken function-level authz | `POST /api/v1/tasks/**` allows all four roles and is matched **before** the per-action authority rules, so a student can call `/tasks/student/{id}/validate` on their own submission and mint XP. Combined with LV-02, this means unlimited XP. | `Config/SecurityConfig.java` | Per-method `@PreAuthorize` plus an object-level reviewer check; `AuthorizationRegressionIT` |
| LV-02 | Critical | API1 BOLA / IDOR | Identity is taken from query params: `?studentId=` (accept/withdraw), `?publisherId=` (publish), `?teacherId=` (assign). Anyone can act as anyone. | `Controllers/TaskController.java` | Identity only from the principal |
| LV-03 | High | API3 Mass assignment | `POST /tasks/global` binds the JPA `Task` entity directly, so `id`, `assignedByUserId`, `baseXP` and `classes` are all client-controlled. Setting `id` overwrites someone else's task. | `Controllers/TaskController.java` | Request records + `FAIL_ON_UNKNOWN_PROPERTIES` |
| LV-04 | High | API5 | `PUT`/`DELETE /api/v1/classes/{id}` have no role check, so any logged-in student can rename or delete classes. | `Controllers/ClasseController.java` | Role checks; cohorts archive instead of delete |
| LV-05 | High | API2 Broken authentication | `/auth/register` returns a working access token **before** email verification, and the JWT filter never checks `enabled`. | `Services/AuthenticationService.java`, `Config/JwtAuthFilter.java` | No tokens until `ACTIVE`; status re-checked per request |
| LV-06 | High | API2 | Verification codes come from `java.util.Random`, are stored in plaintext, and have no attempt limit: 900k possibilities in a 15-minute window can be brute-forced. | `Services/AuthenticationService.java` | `SecureRandom` + HMAC + 5-attempt burn + rate limits |
| LV-07 | Medium | API2 | User enumeration: login, resend and verify return "User not found" vs "not verified" vs "invalid code". | `Services/AuthenticationService.java` | Uniform responses + dummy-hash timing |
| LV-08 | High | API3 Excessive data exposure | Admin endpoints return the `User` entity, including `password` (bcrypt hash), `verificationCode` and tokens. `/tasks/global` returns entities with lazy proxies. | `Controllers/AdminController.java` | View records only; leak test on every admin response |
| LV-09 | Medium | API2 | Refresh tokens aren't stored or rotated, last as long as access tokens (1 h), and the access token itself is accepted at `/refresh-token`. | `Services/AuthenticationService.java` | Opaque rotating refresh tokens with reuse detection |
| LV-10 | Medium | Injection | `firstName` is concatenated into the verification email's HTML, so an attacker-chosen name injects markup into a mail sent from the university's address. | `Services/AuthenticationService.java` | HTML-escaped templates |
| LV-11 | High | API1 | `POST /tasks/student/{studentTaskId}/submit` doesn't check ownership, so anyone can overwrite another student's submission. IDs are sequential and easy to guess. | `Services/TaskService.java` | Owner check + UUIDv7 IDs + 404 on foreign objects |
| LV-12 | Low | Misconfiguration | The authority rules use `ADMIN_READ.name()` (`"ADMIN_READ"`) while authorities are granted as `"admin:read"`, so every one of those rules is dead code and gives a false sense of protection. | `Config/SecurityConfig.java` | Role model rewritten; deny by default |
| LV-13 | Medium | API8 Misconfiguration | `logging.level.org.hibernate.type.descriptor.sql=TRACE` and `mail.debug=true` write bound SQL parameters (hashes, codes) and the SMTP conversation to logs. | `resources/application.properties` | No parameter logging; JSON logs in production |
| LV-14 | Medium | API4 Unrestricted resource consumption | No rate limiting anywhere: login, verify, resend and register can all be hammered. | n/a | `RateLimitFilter` + per-flow limits |
| LV-15 | Medium | Business logic | The overdue-task job has no `@Scheduled`, so penalties never happen. The level-based slot limit is calculated but never enforced. The XP boost lives in a field of a singleton, so it is lost on restart and differs per instance. | `Services/TaskService.java` | Scheduled expiry job with `SKIP LOCKED`; slots enforced under lock; boosts in DB |
| LV-16 | Medium | Business logic | Penalties can push XP negative. Admin "update student" sets arbitrary `xp`/`level` with no audit trail. | `Services/TaskService.java`, `Controllers/AdminController.java` | Ledger with zero floor; audited adjustments |
| LV-17 | Low | Availability | Admin updates require and re-hash the password on every edit; a `null` password throws an NPE (500). | `Controllers/AdminController.java` | Invite/reset flows; no admin-set passwords |
| LV-18 | Low | API8 | `CORS` with credentials allows `http://localhost:8080` alongside production. Three conflicting `@CrossOrigin` annotations, with a trailing slash that never matches. | Several controllers | Single CORS config from properties |
| LV-19 | Low | API8 | Malformed whitelist entry `"api/v1/classes/all"` (no leading slash), and `"/api/v1/auth**"` duplicates. | `Config/SecurityConfig.java` | Explicit permit list |
| LV-20 | Medium | Client | The front end keeps the JWT and role in `localStorage` (XSS → token theft) and decides what to show from the stored role. Logout is a `GET`. | `legacy/web/src` | Access token in memory; refresh in `HttpOnly` cookie (web rewrite) |
