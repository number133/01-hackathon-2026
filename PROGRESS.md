# Implementation progress

Running log of phase completion against the high-level plan. Each phase is a
discrete milestone that leaves the app runnable end-to-end. Detailed per-phase
plans live under `howto/tasks/` (globally git-ignored on the dev machines).

## Summary

| Phase | Title                                             | Status       | Completed   |
|-------|---------------------------------------------------|--------------|-------------|
| 0     | Scaffolding and "it runs"                         | Done         | 2026-04-18  |
| 1     | Authentication and account lifecycle              | Done         | 2026-04-18  |
| 2     | Rooms, membership, roles, admin actions           | Not started  | —           |
| 3     | Messaging and WebSocket transport                 | Not started  | —           |
| 4     | Presence                                          | Not started  | —           |
| 5     | Contacts, friend requests, bans, personal chats   | Not started  | —           |
| 6     | Attachments                                       | Not started  | —           |
| 7     | Notifications and unread state                    | Not started  | —           |
| 8     | Admin UI polish                                   | Not started  | —           |
| 9     | End-to-end hardening for the demo                 | Not started  | —           |
| 10    | Stretch: Jabber / XMPP federation                 | Not started  | —           |

---

## Phase 0 — Scaffolding and "it runs" (done, 2026-04-18)

Goal: `docker compose up --build` on a fresh checkout brings up a page that
proves Angular → Spring Boot → PostgreSQL → Flyway are all wired.

### Delivered

- Repo-root config: `.gitignore`, `.gitattributes` (forces LF on `gradlew`,
  `*.sh`, `Dockerfile`, `*.yml` to survive Windows → Linux build transitions),
  `.dockerignore`, `Dockerfile` (multi-stage: `node:20-alpine` → `gradle:8.10-jdk21`
  → `eclipse-temurin:21-jre-alpine`), `docker-compose.yml` with `db` + `app`
  services, named volumes `db_data` and `app_uploads`, health-gated startup.
- Backend skeleton under `backend/`: Gradle Kotlin DSL, Spring Boot 3.3.5, slim
  dependency set (web, actuator, jdbc, flyway-core, flyway-database-postgresql,
  postgresql, lombok, test). Gradle wrapper pinned to 8.10.
- `ChatApplication.java`, `HealthController.java` (returns `status`, `db`,
  `flywayVersion`), `application.yml` (env-driven datasource, Flyway enabled).
- Flyway baseline migration `V1__baseline.sql` creating a `schema_ready` marker
  row so the health endpoint can prove migrations actually ran.
- Backend tests: `ChatApplicationTests` (context loads with jdbc/flyway
  auto-config excluded and a `@MockBean JdbcTemplate`), `HealthControllerTest`
  (unit, asserts ok vs. degraded JSON shapes).
- Angular scaffold under `frontend/`: standalone components, strict TypeScript,
  `HealthService` calling `/api/health`, landing `AppComponent` rendering
  status / db / flywayVersion with explicit error state (no silent spinners).
- `shared/.gitkeep` placeholder.
- `README.md` covering purpose, stack, run command, local dev loops.

### Verification (all pass)

- `docker compose build` completes.
- Both containers reach running / healthy state.
- `GET /api/health` returns `{"status":"ok","db":"ok","flywayVersion":"1"}`.
- `GET /` serves the built Angular index.
- Restart of `app` does not rerun migrations — Flyway logs
  "Schema is up to date. No migration necessary.".
- Stopping `db` flips `/api/health` to `{"status":"degraded","db":"down", ... }`
  instead of hanging — visible-failure behavior confirmed.
- `./gradlew test` green (3 tests, 0 failures).

### Known tradeoffs carried forward

- **No committed `package-lock.json` yet.** Dockerfile uses `npm ci` when a
  lockfile exists, else `npm install` as a fallback. Run
  `npm install` under `frontend/` once and commit the lockfile to restore
  reproducible installs.
- **Context test uses `@MockBean JdbcTemplate` + autoconfig excludes.** Cheap
  and enough for Phase 0. Swap for Testcontainers if integration coverage grows.
- **No Spring Security on the classpath yet.** Added in Phase 1 when the first
  protected endpoint lands.

---

## Phase 1 — Authentication and account lifecycle (done, 2026-04-18)

Goal: registration, login, logout, password change, password reset, active
sessions, and account deletion — all session-backed (no JWT), all under the
same Spring Session JDBC row store, all behind the SPA CSRF scheme.

### Delivered

- **Auth:** register, login with configurable remember-me TTLs, logout, and a
  `GET /api/auth/me` probe that returns 401 JSON for anonymous callers.
- **Password reset:** token-hashed storage (SHA-256), 15-minute expiry,
  single-use, reset links written to the server log. Dev-only endpoint
  `/api/dev/password-reset-tokens` (bean registered only under
  `spring.profiles.active=dev`) lists the plaintext tokens so demo users can
  exercise the flow without reading container logs.
- **Account:** password change invalidates *all other* sessions of the same
  user (Phase 1 decision); account deletion asks for a password confirm and
  blocks with 409 while the user owns any room — the Phase 2 cascade takes
  over that case.
- **Sessions:** `GET /api/sessions` returns rows with creation / last-access
  timestamps, IP, user-agent, and a `current` flag; `DELETE
  /api/sessions/{id}` revokes with an ownership check via
  `FindByIndexNameSessionRepository`.
- **Security:** `SecurityFilterChain` with explicit permit list, 401-JSON
  entry point (no form redirect), CSRF via `CookieCsrfTokenRepository` +
  `SpaCsrfTokenRequestHandler` + `CsrfCookieFilter` — a first GET writes
  `XSRF-TOKEN`, subsequent mutating calls must echo it in `X-XSRF-TOKEN`.
- **DB:** two new Flyway migrations. V2 enables `pgcrypto` + `citext` and
  creates `users` (email CITEXT unique, username_lower unique) +
  `password_reset_token`. V3 is the stock Spring Session JDBC Postgres schema
  copied verbatim so all DDL is Flyway-managed.
- **Config:** `chat.session.short-ttl` / `chat.session.long-ttl` as
  `Duration` values in `application.yml`, bound to `SessionProperties`
  record with `@Validated`. Override via environment variables works for free
  (`CHAT_SESSION_SHORT_TTL=6h`).
- **Frontend:** standalone Angular 17 screens for every auth action
  (login / register / forgot-password / reset-password / change-password),
  account + sessions screens, top-menu with signed-in badge, home route that
  still shows the Phase 0 health widget. Functional `authGuard`, HTTP error
  interceptor that redirects 401s to `/login` while leaving `/api/auth/me`
  alone. Angular's built-in `withXsrfConfiguration` hooks into Spring's
  `XSRF-TOKEN` cookie — no custom header code.

### Verification (against a fresh volume)

- `docker compose up --build` starts the stack. `/api/health` reports
  `flywayVersion: "3"` (V1 baseline, V2 users, V3 Spring Session).
- CSRF bootstrap: first GET `/` sets the `XSRF-TOKEN` cookie; mutating
  requests without `X-XSRF-TOKEN` are rejected by Spring Security.
- Register (`POST /api/auth/register`) → 200 with user JSON, SESSION cookie.
- Re-register same email → 409 `{"error":"conflict","field":"email"}`.
- Login (`POST /api/auth/login`) with wrong password → 401 `invalid_credentials`.
- `GET /api/auth/me` after login → 200 with the user; after logout → 401
  `{"error":"unauthorized"}`.
- `GET /api/sessions` → array with the current session flagged `current:true`
  and populated `ip` + `userAgent`.
- Password reset end-to-end: request → 204; `/api/dev/password-reset-tokens`
  surfaces the plaintext token; confirm with token + new password → 204;
  login with the new password → 200.
- `./gradlew test` green (19 unit tests + `AuthFlowIT` integration coverage
  against a PostgreSQL 16 container via Testcontainers).

### Known tradeoffs carried forward

- **Testcontainers 2.0.4 pin and artifact renames.** Spring Boot 3.3.5's
  dependency-management pulls Testcontainers 1.19.8, which can't parse the
  `/info` response from Docker Engine 29.x (Docker Desktop 4.52+). We
  override via `extra["testcontainers.version"] = "2.0.4"` and use the
  renamed 2.x coordinates (`testcontainers-junit-jupiter`,
  `testcontainers-postgresql`). Revisit when Spring Boot ships a BOM that
  tracks Testcontainers 2.x directly.
- **Principal name is the display-cased username.** Spring Session indexes
  `SPRING_SESSION.PRINCIPAL_NAME` against whatever name the
  `Authentication` carries; we pass `user.getUsername()` (display case) so
  `findByPrincipalName("Alice")` and `findByPrincipalName("alice")` return
  different sets. Fine in Phase 1 because we only ever look up the current
  principal's name, but room code in Phase 2 that filters by principal needs
  to remember this.
- **`AccountController#currentUser` does an extra DB round-trip per call.**
  We resolve the username from the SecurityContext, then look up the user by
  `username_lower`. A `CurrentUserArgumentResolver` would cut this; not worth
  the abstraction at Phase 1 volume.

---

## Next

Phase 2 — rooms, membership, roles, admin actions. Detailed plan to be
written under `howto/tasks/phase_2_plan.md` before implementation starts.
