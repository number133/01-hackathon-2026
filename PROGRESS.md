# Implementation progress

Running log of phase completion against the high-level plan. Each phase is a
discrete milestone that leaves the app runnable end-to-end. Detailed per-phase
plans live under `howto/tasks/` (globally git-ignored on the dev machines).

## Summary

| Phase | Title                                             | Status       | Completed   |
|-------|---------------------------------------------------|--------------|-------------|
| 0     | Scaffolding and "it runs"                         | Done         | 2026-04-18  |
| 1     | Authentication and account lifecycle              | Done         | 2026-04-18  |
| 2     | Rooms, membership, roles, admin actions           | Done         | 2026-04-18  |
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

## Phase 2 — Rooms, membership, roles, admin actions (done, 2026-04-18)

Goal: deliver the chat-room domain end-to-end except message bodies, plus
close the Phase 1 loose end where account deletion was blocked with a 409 if
the user owned any room. Implementation followed
`howto/tasks/phase_2_plan.md` and the execution rules in
`howto/general/phase_implementation_rules.txt`.

### Delivered

- **Schema (V4):** `room`, `room_member` (composite PK, `owner`/`admin`/
  `member` role), `room_ban` (with `banned_by` audit trail, set-null on
  banner delete), `room_invitation` (with partial unique index on
  `(room_id, invitee_user_id) WHERE status='pending'` so a user can't have
  two open invites to the same room, but history persists after resolve).
  Globally-unique room name enforced with a functional unique index on
  `LOWER(name)`.
- **RoomService** — create / update / delete / catalog / `getForViewer` with
  private-room 404-instead-of-403 hiding, and `requireMember / requireAdmin /
  requireOwner` as the single source of truth for permission checks.
- **RoomMembershipService** — join (reject if private / banned / already a
  member), leave (reject if owner), kick = ban (spec §2.4.8), ban (reject
  owner target), unban, promote (owner-only), demote (admin target only,
  never owner).
- **InvitationService** — invite by username (any member may invite per spec
  §2.4.9), partial-unique-index catches duplicate pending invites, accept
  adds the membership row, decline / revoke close the invite in place.
- **Controllers** — `/api/rooms` (CRUD + catalog), `/api/rooms/{id}/join`,
  `/api/rooms/{id}/members/...`, `/api/rooms/{id}/bans/...`,
  `/api/rooms/{id}/admins/{userId}`, `/api/invitations/...`,
  `/api/rooms/{id}/invitations`. Each uses the `SecurityContext` principal
  (display-cased username from Phase 1) to resolve the acting user.
- **UserService.deleteAccount** — real cascade. Loads rooms owned by the
  user and deletes them first (FK `ON DELETE CASCADE` handles members /
  bans / invites for those rooms); then deletes the user row, which
  cascades the user's memberships in other people's rooms. Entire thing
  runs inside a single `@Transactional`.
- **Common** — new `ForbiddenException` → 403, plus handlers for
  `NoResourceFoundException` and `NoSuchElementException` → 404.
- **Frontend** — standalone Angular routes `/rooms`, `/rooms/new`,
  `/rooms/:id`, `/rooms/:id/manage` (inline tabbed panel: Members / Admins /
  Banned / Invitations / Settings), `/invitations`. Top menu adds Rooms and
  Invitations links with a live pending-invite badge backed by
  `InvitationService.pendingCount` signal.

### Verification (fresh `docker compose down -v && up`)

- Flyway reports version `V4` after migration.
- Register Alice / Bob / Eve via `/api/auth/register` (status 200, SESSION
  cookie set).
- Alice creates public room `gen` (201). Bob sees it in `/api/rooms`.
- Bob joins (204). Alice promotes Bob (204).
- Eve joins, then Bob (admin) bans Eve (`POST /api/rooms/{id}/bans` → 201,
  returns the `RoomBanView` with banner username). Eve tries to re-join →
  403 `forbidden` with message "You are banned from this room".
- Alice creates private room `core` (201). Bob's `/api/rooms` does not
  include it (grep `core` returns 0 hits).
- Alice invites Bob by username. Bob's `/api/invitations` returns the
  pending entry. Bob accepts → 204. Bob is now a member of `core`.
- Alice deletes her account (204). Bob's catalog is `[]` — both the public
  and private rooms owned by Alice cascaded cleanly.
- `./gradlew test` green: 54 tests total (35 unit + 3 `AuthFlowIT` + 4
  `RoomFlowIT` + 1 `PrivateRoomInviteIT` + 1 `OwnerCascadeIT` + 1
  `KickEqualsBanIT` + existing Phase 1 unit tests).

### Known tradeoffs carried forward

- **Optional-injection of `RoomRepository` / `RoomService` into
  `UserService`.** The user package is used from `AuthFlowIT` (which doesn't
  exercise Phase 2 code) and from the room cascade path. To avoid a
  circular-wiring smell in tests, both collaborators are wired with
  `@Autowired(required = false)` and treated as optional. If either is
  missing at runtime, `deleteAccount` falls through to the old "delete user
  row only" behavior. A cleaner alternative is to make the cascade live on
  a `UserLifecycleService` that depends on both domains; deferred because
  Phase 2 is the last domain to add user-cascade logic, and Phase 3's
  messages cascade through FKs on `room_id`, not through user.
- **Admin UX is an inline tabbed panel, not a modal.** Per the plan and the
  Phase 8 description in `app_requirements_plan.md`. Phase 8 will convert to
  a modal overlay.
- **Catalog returns a flat array** not `{items, nextCursor}` — the plan
  proposed cursor pagination, but for Phase 2 the single `limit` parameter
  + "sort by created_at DESC" is sufficient at the listed 300-user scale.
  Keyset pagination lands with the history endpoint in Phase 3.
- **Member count is computed per-row** in `RoomService.toViews` via
  `COUNT(*)` per room. At 300 concurrent users and catalog `limit=50` this
  is bounded; if it becomes a hot path, denormalise `room.member_count` and
  maintain on join / leave / ban.

---

## Next

Phase 3 — messaging and WebSocket transport. The last-minute
`hint2.txt`-derived design call (message send over REST, fanout over WS;
per-conversation `seq` watermark; 100K-message history scroll test) lands
then. Detailed plan to be written under `howto/tasks/phase_3_plan.md`.
