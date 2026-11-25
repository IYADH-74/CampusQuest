# Same app, two developers

CampusQuest was built twice by the same person, a few years apart.

- **v2** was a second-year university project. It had a deadline, a demo, and a developer who had just
  discovered Spring Security and was going to use *all* of it. It is preserved, lovingly and unedited, in
  [`legacy/`](../legacy) and on the `legacy-v2` branch.
- **v3** is the rewrite, built the way an experienced software engineer *and* a security specialist would
  build it.

This page puts them side by side. It's not here to mock the student. Every senior engineer wrote a v2 once,
and most of them just had the good sense to delete it. It's here because the gap between the two is the
most useful thing this repository can teach.

> **Three questions, three developers**
>
> | The student asks | The engineer asks | The security specialist asks |
> |---|---|---|
> | Does it work? | Does it *keep* working? | How do I break it? |
> | Did the demo pass? | What happens at 10× the load, or on the second instance? | What happens if the client is lying? |
> | It compiles, ship it. | Is there a test that would catch a regression? | Is there a test that would catch an *attacker*? |

---

## 1. "Who am I?"

**v2** asked the client. Politely.

```java
// legacy TaskController
@PostMapping("/global/{taskId}/accept")
public ResponseEntity<StudentTask> acceptGlobalTask(@PathVariable Integer taskId,
                                                    @RequestParam Integer studentId) {
    return ResponseEntity.ok(taskService.acceptGlobalTask(taskId, studentId));
}
```

Anyone could accept, withdraw or publish as anyone else by changing a number in the URL. Since ids were
sequential, "anyone else" meant "everyone, one at a time".

**v3** never asks. The caller is whoever the cryptographically verified token says, re-checked against the
database on every request (still active? password not changed since the token was issued?).

```java
@PostMapping("/{id}/join")
@PreAuthorize("hasRole('STUDENT')")
public ResponseEntity<ParticipationView> join(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id)
```

There is even a test that sends `?studentId=<someone else>` just to prove nobody is listening
(`AuthorizationRegressionIT.lv02_identityParametersAreIgnored`).

## 2. "Who is allowed to do this?"

**v2** had forty lines of security rules. They looked very professional.

```java
.requestMatchers(POST,"/api/v1/tasks/**").hasAnyRole(STAFF.name(),ADMIN.name(),TEACHER.name(),STUDENT.name())
// ...
.requestMatchers(POST, "/api/v1/tasks/student/{studentTaskId}/validate").hasAuthority("global_task:validate")
```

Spring Security uses the **first** matching rule. The first rule lets students `POST` anything under
`/tasks/**`, so the line that was supposed to stop them from validating their own work never ran. Students
could approve their own submissions and print XP. Elsewhere, the rules checked for an authority called
`"ADMIN_READ"` while users were granted `"admin:read"`, so those rules could never match anyone either.

The rules weren't wrong because the student didn't know the syntax. They were wrong because nothing ever
**tested the negative case**: "a student tries to approve and gets refused".

**v3** checks access at three independent layers, and each layer has tests written from the attacker's side:

1. URL rules, **deny by default** (`anyRequest().denyAll()`).
2. `@PreAuthorize` on every single endpoint.
3. Object-level checks in services: *is this your participation? are you the author of this quest? do you
   teach this cohort?* If not, the answer is **404**, so you can't even learn that the object exists.

## 3. Taking input

**v2** bound the database entity straight from the request body:

```java
@PostMapping("/global")
public ResponseEntity<Task> publishGlobalTask(@RequestBody Task task, @RequestParam Long publisherId)
```

Every column was writable by the client, including `id`. Sending an existing id overwrote someone else's
task. This is called *mass assignment*, and it is one of the oldest bugs on the web.

**v3** binds to small, validated request records that only contain the fields a client may set. Unknown
fields make the whole request fail with `400`, so you can't sneak `"role":"ADMIN"` into a signup.

## 4. Secrets

| | v2 | v3 |
|---|---|---|
| Email code generator | `new Random().nextInt(900000) + 100000` | `SecureRandom` |
| Code storage | Plaintext column | HMAC-SHA256 with a server-side pepper |
| Wrong guesses allowed | Unlimited (900,000 codes, 15 minutes: easy to brute-force) | 5, then the code is burned |
| Access before verifying email | Yes: `/register` returned a working token | No token until the account is active |
| Refresh token | Same lifetime as the access token, never stored, and the access token worked as one | Opaque, stored hashed, single use, rotated, and replaying one kills the whole session |
| Passwords | bcrypt | Argon2id, upgradable in place |
| What admins saw | `User` entity, **including the password hash and verification code** | A view record that has no such fields |

## 5. "Does this email have an account?"

**v2** told you, in three different ways: `"User not found"`, `"Account not verified"`, `"Invalid
verification code"`. That's a free list of valid students for anyone with a script.

**v3** answers registration, verification, login and password reset identically whether or not the account
exists. It even runs a password hash for non-existent users, so the *response time* doesn't give it away
either.

## 6. Errors

**v2**: `throw new RuntimeException("User not found")`, which reached the client as a `500` (or a `403`,
depending on the filter's mood).

**v3**: every failure is an RFC 9457 problem with a stable machine-readable `code` (`NO_FREE_SLOT`,
`PAST_DUE`, `INVALID_REFRESH_TOKEN`…). Nothing internal is ever echoed: no class names, no SQL, no parser
messages. Unexpected errors return only a request id, which matches the server log line.

## 7. Business rules that only existed in comments

**v2** had the right *ideas*:

```java
// Scheduled method to auto-fail overdue mandatory tasks
@Transactional
public void autoFailOverdueMandatoryTasks() { ... }
```

…with no `@Scheduled`, so it never ran. Nobody ever missed a deadline, because deadlines didn't exist. The
level-based limit on optional tasks was calculated by `getOptionalTaskSlotsForLevel()` and then never
checked. Penalties could push XP below zero.

**v3** puts every rule in one of two places that can't be skipped:

- **The entity's own methods**, which refuse illegal transitions (`submit()` throws if the work isn't active
  or is past due).
- **The database schema**, which refuses illegal data no matter which code path wrote it (XP can't be
  negative, a participation can't be rewarded twice, boosts can't overlap).

## 8. State that lived in RAM

**v2**'s global XP boost:

```java
private boolean globalBoostActive = false;
private LocalDateTime globalBoostActivatedAt = null;
```

A field on a singleton. Restart the server and the boost is gone. Run two instances and each one has its
own opinion about whether a boost is on.

**v3**'s boost is a row with a time window, and PostgreSQL refuses overlapping windows with an exclusion
constraint:

```sql
constraint ex_boost_overlap exclude using gist (tstzrange(starts_at, ends_at, '[)') with &&)
    where (cancelled_at is null)
```

## 9. XP as a number vs XP as a history

**v2**: `student.setXp(student.getXp() + xp)`. If two approvals landed at the same moment, one could
overwrite the other. If XP looked wrong, there was no way to know why. Admins could also just *set* a
student's XP to any value through the edit form.

**v3**: XP is an **append-only ledger**. Every change is a row with its reason, its source, the boost that
applied, who did it, and the balance afterwards. The student row is locked while it's updated, and a unique
constraint makes double-awarding impossible. Admin corrections are allowed, but they need a note, go through
the same ledger, and land in the audit log.

## 10. Races

**v2** never thought about two requests arriving at once. That is fair: most coursework never has two users.

**v3** has a test where 8 students click "join" on a 1-seat challenge at the same instant. Exactly one gets
in (`QuestGameplayIT.capacityHoldsUnderConcurrentJoins`). The overdue sweeper claims rows with
`FOR UPDATE SKIP LOCKED`, so you can run it on three servers at once and nobody gets penalised twice.

## 11. Performance by accident

**v2** filtered in Java, after loading the whole table:

```java
return userRepository.findAll().stream().filter(u -> u.getRole() == Role.TEACHER).toList();
```

That's fine with 12 rows in the demo database. With a real university's data, it means loading every user
into memory to find the teachers.

**v3** asks the database for exactly what it needs, uses indexes designed for those queries (including a
partial index for the leaderboard), paginates every list, and caps page sizes.

## 12. Logging

**v2**:

```properties
logging.level.org.hibernate.type.descriptor.sql=TRACE
spring.mail.properties.mail.debug=true
```

Every bound SQL parameter (password hashes, verification codes) and the full SMTP conversation went into
the logs. Logs are usually the *least* protected place in a company.

**v3** logs no secrets, writes structured JSON logs in production, tags every line with a request id, and
keeps a separate, queryable **audit trail** for security events (logins, failed logins, token theft
detection, role-sensitive admin actions).

## 13. Proof

| | v2 | v3 |
|---|---|---|
| Automated tests | 0 | 69 (unit + integration on a real PostgreSQL) |
| Tests written from the attacker's side | 0 | One per legacy vulnerability, named after it |
| Schema management | `ddl-auto=update` (Hibernate improvises) | Versioned Flyway migrations, and Hibernate only *validates* |
| CI | None | Build, test, image build, CodeQL, Dependabot |
| "Works on my machine" | Required MySQL, five env vars and faith | `docker compose --profile app up` |

## 14. The front end

`legacy/web` deserves a museum plaque:

- The JWT **and the user's role** in `localStorage`, so the UI decided what to show by reading a value any
  user could edit in the browser console.
- `http://localhost:8080` hardcoded in over 30 places, right next to a Vite proxy configured to make that
  unnecessary.
- The student and teacher dashboards, the entire point of the product: `🎓 Student interface placeholder`.
- And for anyone who reached the home page without a role: `HA nice try dude`.

To be fair to the student, the server didn't trust that role either. It just didn't check much else.

## 15. Git history

**v2**, a representative selection of commit messages:

```
Changes to be committed: modified: src/main/java/com/apex/campu_quest_v2/Config/SecurityConfig.java ...
Please enter the commit message for your changes. Lines starting with '#' will be ignored...
NEEDS UPDATING
COMPLETE API
```

(The commit after `COMPLETE API` modified the API.)

**v3**: small commits, each one a coherent unit with a message that says what changed and why.

---

## What actually changed

Not the language, the framework, or even most of the *ideas*. v2 already had roles, permissions, refresh
tokens, verification emails, XP tiers, levels, slots, boosts and penalties. The student knew what a good
system *has*.

What changed is the habits:

1. **Assume the client is hostile.** Every id, field and header it sends is a suggestion.
2. **Make illegal states unrepresentable**, in the code and in the schema.
3. **Write the test for the attack**, not just for the happy path.
4. **Keep state somewhere that survives a restart and a second server.**
5. **Leave a trail**: an audit log for people, a ledger for money-like values, a history for the code.

The v2 holes are kept on purpose. A future **CTF edition** will serve them as a deliberately vulnerable
target, with [the vulnerability inventory](legacy-vulnerabilities.md) as the answer key.
