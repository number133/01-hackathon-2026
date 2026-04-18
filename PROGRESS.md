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
| 3     | Messaging and WebSocket transport                 | Done         | 2026-04-18  |
| 3.1   | Split "remove" from "ban" (fix-up of Phase 2)     | Done         | 2026-04-18  |
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

## Phase 3 — Messaging and WebSocket transport (done, 2026-04-18)

Goal: turn Phase 2's room shells into actual chat — text messages over REST,
per-conversation monotonic `seq`, STOMP-over-WebSocket fanout for every
member, edit / delete / reply, keyset-paginated history. Followed
`howto/tasks/phase_3_plan.md` and `howto/general/phase_implementation_rules.txt`.

### Delivered

- **Schema (V5):** `conversation(id, type, last_seq, created_at)` with a
  room-type per chat room, `room.conversation_id UNIQUE` (backfilled via
  CTE for any rooms predating V5), `message(id, conversation_id, seq,
  author_id, body, reply_to_id, created_at, edited_at, deleted_at)` with
  a `CHECK (octet_length(body) <= 3072)` and the load-bearing index
  `idx_message_conversation_seq_desc`.
- **Atomic `seq` assignment via `UPDATE … RETURNING` through
  `JdbcTemplate`.** Spring Data `@Modifying` queries can only return
  `void`/`int` — using JdbcTemplate instead keeps the single-statement
  row-level lock that serialises concurrent posters to one conversation.
- **MessageService** — post / edit / delete / history with the invariants
  from the plan: member gate, author gate for edit, author-or-admin gate
  for delete, idempotent delete (no re-broadcast), reply target must be
  in the same conversation, 3072-byte UTF-8 cap enforced at both API and
  DB (multi-byte emojis trip it correctly). Keyset pagination via
  `beforeSeq` + `limit` (default 50, capped 100).
- **`MessageBroadcaster`** wraps `SimpMessagingTemplate` and registers a
  `TransactionSynchronization.afterCommit` callback so a rolled-back
  transaction never leaks a phantom event to subscribers.
- **WebSocket** — `/ws` endpoint, `/topic/rooms/{id}` as the single push
  channel. `WsChannelInterceptor` gates `CONNECT` on a valid session and
  `SUBSCRIBE` on current membership *plus* a ban check (the ban trumps a
  stale `room_member` row). All `SEND` frames to topics are rejected.
- **Cascade.** Deleting a room now deletes the conversation, which
  cascades message rows via `message.conversation_id` FK. Deleting a
  user cascades owned rooms → conversations → messages. Messages a user
  posted elsewhere keep the row with `author_id = NULL`; the API renders
  null-author as `(deleted)`.
- **Frontend** — new `chat/` feature. `ChatService` owns one STOMP
  connection per browser tab, ref-counted per-room subscriptions, a
  per-conversation `RoomState` signal with gap detection (seq >
  expected+1 triggers a full history refetch). `MessageListComponent`
  does bottom-stick auto-scroll + infinite scroll upward;
  `ComposerComponent` does Enter-to-send + Shift+Enter newline +
  reply-preview chip; `MessageItemComponent` does inline edit / delete
  and emits reply events. Wired into `RoomViewComponent`, replacing the
  Phase-2 placeholder.
- **Dependencies.** Backend: `spring-boot-starter-websocket`. Frontend:
  `@stomp/stompjs@^7`, `@ctrl/ngx-emoji-mart@^9` (picker UI polish lands
  in Phase 6 along with attachments).

### Verification (fresh `docker compose down -v && up`)

- Flyway reports version `V5`.
- Register Alice → create public room `msgtest` → post three messages
  → seq values come back 1, 2, 3. History returns newest-first.
- `PATCH /api/messages/{id}` on seq 2 → 200 with `editedAt` set.
- `DELETE /api/messages/{id}` on seq 3 → 204. Subsequent history shows
  seq 3 with `body: null`, `deletedAt` populated, position in the list
  preserved (no hole in the seq sequence).
- Non-member `GET /api/rooms/{id}/messages` → 403.
- Oversized body (`"a".repeat(4000)`) → 400.
- `./gradlew test` green: 77 pass + 1 skipped (`LargeHistoryScrollIT`
  gated by `RUN_LARGE_HISTORY_IT=true`).

### Known tradeoffs carried forward

- **`@Modifying` return-type workaround.** Spring Data JPA won't let a
  `@Modifying` query return `Long`, so the `UPDATE conversation SET …
  RETURNING last_seq` goes through `JdbcTemplate` rather than the
  repository. Trivial, worth a code-review note so nobody later "cleans
  this up" back into the repo.
- **`LargeHistoryScrollIT` gated on `RUN_LARGE_HISTORY_IT`.** Seeding
  100,000 rows takes ~10–15 s and isn't wanted on every run. Enable in
  CI or locally (`RUN_LARGE_HISTORY_IT=true ./gradlew test`) when
  validating the `idx_message_conversation_seq_desc` index.
- **No `WebSocketFanoutIT` yet.** The message-send path + topic
  publishing is covered by unit tests (`MessageServiceTest` +
  `MessageBroadcasterTest`) and by manual demo. An end-to-end STOMP
  subscribe → post → receive test with `StandardWebSocketClient` lands
  alongside Phase 4's presence fanout, which needs the same harness.
- **Emoji picker library pulled in but UI polish deferred.** The
  composer relies on OS-level emoji entry for now; the picker overlay
  ships in Phase 6 when attachment UI gets the same treatment.
- **`room.conversation_id` FK direction.** Conversation→room cascade,
  which means the room-deletion flow has to delete the conversation row
  (which then cascades messages + the room itself). The opposite
  direction would be equally correct; this direction keeps the
  `message.conversation_id` FK the simplest ON DELETE CASCADE.

---

## Phase 3.1 — Split "remove" from "ban" (done, 2026-04-18)

Goal: reverse the Phase 2 decision that collapsed "kick" into "ban". Per
`hint3.txt`, a removed user can rejoin (public rooms) or accept a fresh
invite (private rooms); a banned user cannot rejoin until unbanned.

### Delivered

- **`RoomMembershipService.remove(roomId, targetId, actorId)`** — new service
  method. Deletes the `room_member` row only; no `room_ban` insert. Admin
  actor, non-owner target, must be a current member.
- **`DELETE /api/rooms/{id}/members/{userId}` semantics flipped.** Still 204,
  same path, same auth — but no longer inserts a ban row. The controller
  handler was renamed from `kick` to `remove`.
- **Frontend `manage-room`** — two distinct buttons per member row,
  "Remove" and "Ban", each with its own `confirm()` copy stating exactly
  what the target can do next (rejoin vs cannot rejoin until unbanned).
  `RoomService.kick()` renamed to `remove()`.
- **Test swap** — `KickEqualsBanIT` deleted (it encoded the reversed
  invariant); `RemoveVsBanIT` added — walks remove → rejoin succeeds;
  ban → rejoin 403; unban → rejoin succeeds; ban list reflects only the
  ban-path action.
- **Unit tests** — `RoomMembershipServiceTest` gains `remove`-path cases:
  deletes member row without a ban insert, rejects owner target, rejects
  non-admin actor, rejects non-member target.

### Verification

- `./gradlew test --tests '*RoomMembershipServiceTest' --tests '*RemoveVsBanIT'`
  green.
- Phase 2's `Known tradeoffs carried forward` note ("kick = ban per spec
  §2.4.8") is superseded by this phase; the Phase 2 entry itself is left
  unchanged as a historical record of what shipped at the time.

### Known tradeoffs carried forward

- **No remove audit trail.** The split means a removed user leaves no
  trace in `room_ban` or anywhere else. If a per-room "members who left /
  were removed" log is ever needed (for moderation history), it is a new
  table, not a repurposing of `room_ban`. Out of scope here.
- **WebSocket fanout of member changes** still missing — already tracked as
  a Phase 3 carried gap; unchanged by this phase.

---

## Next

Phase 4 — presence. Cursor-move / keydown / focus `active` pings
throttled to 1–2 s; server infers AFK from absence of signals (tabs may
hibernate, so no "going inactive" message is ever trusted).
