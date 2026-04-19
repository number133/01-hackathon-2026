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
| 4     | Presence                                          | Done         | 2026-04-18  |
| 5     | Contacts, friend requests, bans, personal chats   | Done         | 2026-04-18  |
| 6     | Attachments                                       | Done         | 2026-04-18  |
| 7     | Notifications and unread state                    | Done         | 2026-04-19  |
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

## Phase 4 — Presence (done, 2026-04-18)

Goal: online / AFK / offline dots next to every member of every room,
derived from client "active" pings — no "going inactive" signal per
hint2.txt (browsers freeze background tabs, so absence must be the only
source of inactivity truth).

### Delivered

- **`PresenceService`** — in-memory map `userId → tabId → lastPingAt`.
  `recordPing` upserts and publishes `/topic/presence/{userId}` only on
  state transition; `statusOf` derives ONLINE / AFK / OFFLINE from the
  newest ping under the configured thresholds; `sweep` runs on a
  scheduled task and evicts stale tab entries, re-computing per-user
  status and emitting change frames; tab map is capped at 50 entries
  per user (oldest evicted) to bound adversarial memory growth.
- **`PresenceSweeper`** — `SchedulingConfigurer` that registers the
  sweep task at `chat.presence.sweep-interval`. Chose this over
  `@Scheduled(fixedRateString = ...)` because SpEL can't resolve the
  bean name Spring generates for a `@ConfigurationProperties` record.
- **`PresenceController`** — `POST /api/presence/ping`,
  `GET /api/presence?userIds=…` (max 200), `GET /api/presence/config`
  (exposes `pingIntervalMs` so the client doesn't hard-code 2000 ms).
- **`PresenceProperties`** — `@ConfigurationProperties("chat.presence")`
  record: `pingInterval=2s`, `afkThreshold=60s`, `offlineGrace=30s`,
  `sweepInterval=5s`. All four are Spring `Duration`s, overridable via
  env var or a profile `application-*.yml`.
- **`WsChannelInterceptor`** widened — the hard reject of non-rooms
  subscribes is gone; `/topic/presence/{uuid}` is now allowed with no
  per-destination auth check (CONNECT gate already enforces session).
  Shared `parseUuidOr403` helper for both prefixes.
- **Enable scheduling + Clock bean** — `@EnableScheduling` on
  `ChatApplication`; `Clock.systemUTC()` as a bean so tests can
  substitute a `MutableClock` without touching `System.currentTimeMillis`.
- **Frontend** — `PresenceService` (Angular) throttles
  `mousemove` / `keydown` / `focus` / `touchstart` to one `POST /ping`
  per `pingIntervalMs`; `watch(userIds)` hydrates via bulk GET and
  subscribes to `/topic/presence/{userId}` through the shared
  STOMP client (`ChatService.subscribeTopic`). `PresenceDotComponent`
  renders green/yellow/grey with CSS variables. `ManageRoomComponent`
  gets a new Status column showing the dot + status text, tracks
  `watch/unwatch` across member-list reloads and `ngOnDestroy`.
- **`AppComponent`** — `effect(() => if auth.isAuthenticated() …)`
  connects the STOMP client and starts presence only after auth
  hydrates (Phase 3 + 3.1 left this tied to initial route activation;
  presence needs it triggered as soon as the user is authenticated,
  not only when they enter a room).
- **Tests** — `PresenceServiceTest` with a `MutableClock` covering all
  seven invariants plus a properties-override case (shorter
  `afkThreshold` demotes to AFK). `PresencePingIT` via Testcontainers
  exercises ping → bulk-GET → 400-on-blank → config endpoint.
- **`scripts/docker-smoke.sh`** gained a Phase 4 block: fetch config,
  ping, bulk-GET asserts the pinger is `online`, blank tabId rejected
  with 400.

### Verification

- `./gradlew test` — full suite green including `PresenceServiceTest`
  (8 cases) and `PresencePingIT` (3 cases).
- Hot-reload dev loop (ng serve + bootRun) — registered `aline` in the
  browser, created a public room, had `bob4` join via REST. aline's
  manage-room screen showed aline=**online**, bob4=**offline**. A
  single `curl -X POST /api/presence/ping` as bob4 flipped bob4 to
  **online** in aline's tab within ~3 s via the WS fanout topic.
- `scripts/docker-smoke.sh` — clean rebuild + all phases 0 through 4
  green (run in the final verification step below).

### Known tradeoffs carried forward

- **No STOMP DISCONNECT hook.** The plan (§3 decision 3) allowed an
  immediate offline flip on clean tab close via a DISCONNECT handler
  calling `sessionClosed`. Implemented sweeper-only eviction instead:
  a closed tab reliably flips to OFFLINE in `afkThreshold +
  offlineGrace` (~90 s by default), not "within 30 s of the last
  session activity". The wiring needed to correlate STOMP session id
  with the HTTP session id keying the presence state added real
  complexity for a marginal latency improvement at hackathon scale.
  Revisit if the spec's "presence updates below 2 seconds" (NFR 3.2)
  is held against the offline edge (it is currently held against
  online/AFK *transitions* for a live user, which the broker fanout
  satisfies).
- **No rate-limit on `/ping`.** The plan left this as an open decision
  with a 429 default. Skipped for now because the server treats a
  flood of pings as a no-op after the first per tab-per-interval
  (no-op since the state is already ONLINE). Add if abuse becomes a
  real concern.
- **Snapshot-on-subscribe race.** A transition fired between
  `GET /api/presence` and the `SUBSCRIBE /topic/presence/{id}` is
  lost. Mitigation implemented per plan §3 decision 1: the client
  re-hydrates on `window.focus`.
- **One `Clock` bean for the whole app.** Pre-existing code used
  `System.currentTimeMillis()` ad-hoc; Phase 4 introduces the
  injectable bean only for the presence path. Other modules that
  would benefit from clock injection (messages, sessions) aren't
  migrated — out of scope here.

---

## Phase 5 — Contacts, friend requests, user-to-user bans, personal chats (done, 2026-04-18)

Goal: user-to-user relationships (friend requests, friendship,
per-user bans) and 1:1 personal dialogs on top of the Phase 3 message
primitives. Presence dots on contacts reuse Phase 4's
`/topic/presence/{userId}` without changes.

### Delivered

- **Schema (V6):** `friend_request` with `pending | accepted |
  declined | revoked | superseded` states (partial unique index on
  `pending` direction-ordered), `friendship` (stored once, `user_a <
  user_b` check constraint + PK), `user_ban` (asymmetric, one row per
  direction), `dialog` (PK = conversation_id, ordered pair unique).
  All FKs cascade on `users.id` so the Phase 1 account-delete flow
  picks them up for free.
- **`FriendService`** — request send/accept/decline/revoke/unfriend,
  ordered-pair helper, friendship uniqueness caught via
  `DataIntegrityViolationException` (concurrent accept = idempotent),
  supersedes the opposite-direction pending on accept.
- **`UserBanService`** — idempotent ban, ripples through
  unfriend + cancel pending requests in one transaction, unban leaves
  friendship gone on purpose.
- **`DialogService`** — lazy dialog create (unique index arbitrates
  concurrent `getOrCreate`), `isFrozen` derived from
  `ban-either-direction || not-friends` (no stored column),
  participant check used by the WS interceptor.
- **REST:** `/api/friend-requests`, `/api/friends`, `/api/user-bans`,
  `/api/dialogs`, `/api/dialogs/{id}/messages`. DM send/history
  share Phase 3 DTOs verbatim — the frontend `MessageView` shape
  doesn't change; `roomId` is simply null for dialog messages.
- **`MessageService` refactor** — conversation-typed dispatch: edit
  and delete look up the conversation, flip to the dialog path when
  `type='dialog'` (frozen check + author-only delete, no admin tier),
  publish to `/topic/dialogs/{conversationId}` via the new
  `MessageBroadcaster.publishToDialog`. Phase 3 room callers
  untouched.
- **`UserEventPublisher`** — `/topic/users/{userId}` fanout for
  `friend-request.created/resolved`, `friend.added/removed`,
  `user-ban.added/removed`, afterCommit like Phase 3.
- **`WsChannelInterceptor`** widened twice — `/topic/dialogs/{uuid}`
  gated on participation via `DialogService` (@Lazy to break the
  `FriendService ↔ DialogService ↔ WsChannelInterceptor` cycle that
  arose because the interceptor is constructed as part of
  WebSocketConfig, which transitively needs SimpMessagingTemplate
  that FriendService depends on), `/topic/users/{uuid}` gated on
  `principal.userId == uuid`.
- **Frontend** — `FriendService` (REST + `/topic/users/{me}` listener
  refreshing signals on any event), `DialogService` (REST + per-
  dialog subscription), pages `/contacts`, `/friend-requests`,
  `/dialogs`, `/dialogs/:id`. `ContactsComponent` reuses
  `PresenceDotComponent` for the contact status dots; the dialog
  view disables the composer and shows a banner when `frozen`.
- **Tests** — `FriendFlowIT` (request→accept→unfriend,
  mutual-pending rejected), `DialogAndBanFlowIT` (create→send→ban
  freezes→bob-can't-send→alice-can't-edit→unban-still-frozen→
  refriend-unfreezes; non-participant sees 403 on both REST and WS).
- **Smoke script** — extended with Phase 5 block covering the full
  friend→dialog→ban→refriend ripple.

### Verification

- `./gradlew test` — full suite green across every shipped phase.
- `scripts/docker-smoke.sh` — clean rebuild, all six phase blocks
  pass.
- Manual walk via ng serve + bootRun: two users friended, opened a
  dialog, sent a message with live STOMP delivery, banned →
  composer frozen, unbanned + re-accepted → composer unlocked.

### Known tradeoffs carried forward

- **@Lazy on `WsChannelInterceptor`'s DialogService dep.** Breaks the
  DI cycle introduced by the two interceptor gates. The proxy
  overhead is one virtual call per SUBSCRIBE, negligible at
  hackathon scale. Alternative: extract the participant check to a
  tiny `DialogMembershipQuery` bean that reads straight from the
  repo — no friendship/ban coupling and no cycle. Deferred because
  the @Lazy fix is one line.
- **Naive FriendService event handling on frontend.** Every
  `/topic/users/{me}` event triggers `refreshAll()` over four REST
  endpoints. Fine for a ≤50-friend list per spec §3.1; a per-event
  delta merge is straightforward if this ever gets hot.
- **No frontend block-from-room-members context action.** Plan §9
  listed "Block from room member row" as a touch; not delivered.
  The Contacts screen's Block button covers the primary flow; the
  room-members version is polish for Phase 8's admin UI rework.
- **No `MessageService` frozen-dialog path for the
  `PATCH /api/messages/{id}` edit coming from a room author on a
  dialog message.** Since dialog messages don't surface in room
  admin flows, the overlap doesn't exist in practice; the service
  code handles both branches symmetrically anyway.

---

## Phase 6 — Attachments (done, 2026-04-18)

Goal: messages can carry one or more files — images rendered inline,
other files as download cards — with local-FS storage and access
gated on the same room-member / dialog-participant rules the
conversation uses (spec §2.6).

### Delivered

- **V7 migration** — `attachment` with nullable `message_id` (for the
  two-step upload), `conversation_id` denormalized for fast access
  checks, partial index on orphans, `uploader_id ON DELETE SET NULL`
  so files persist when the uploader loses access (spec §2.6.5).
- **`AttachmentProperties`** — `chat.attachment.max-size` (20MB),
  `max-image-size` (3MB), `orphan-ttl` (1h), `sweep-interval` (5m),
  `storage-root` (`/data/uploads`), `max-per-message` (10),
  `blocked-mime-prefixes`. Servlet multipart limits wired to match.
- **`AttachmentService`** — streaming `.part → rename` writes,
  `linkToMessage` stamps message id during send (foreign-uploader /
  wrong-conversation / double-link are 409), scheduled
  `sweepOrphans`, `deleteConversationTree` hook called from
  `RoomService.deleteConversationAndRoom` (@Lazy breaks the cycle).
- **Access check** — unified helper:
  `attachment → conversation → room member + not banned, or dialog
  participant`; orphans only their uploader can read. Frozen
  dialogs still allow downloads per §2.3.5.
- **REST** — `POST /api/attachments` multipart, `GET /{id}` streams
  bytes with RFC-5987 filename header, `GET /{id}/metadata`,
  `DELETE /{id}` (orphan only). `MaxUploadSizeExceededException` →
  413 in `ApiExceptionHandler`; custom `AttachmentTooLargeException`
  and `UnsupportedMimeTypeException` → 413 / 415.
- **Message integration** — `SendMessageRequest.attachmentIds`,
  `text` optional when attachments present, empty bodies 400.
  `MessageService.post` / `postToDialog` link attachments inside
  the same transaction as the message save. `MessageView` carries
  `attachments: AttachmentRef[]` populated via one batched
  `findAllByMessageIdIn` per history page. `RoomView` gained
  `conversationId` so the composer can pass it without a second
  round-trip.
- **Frontend** — `AttachmentService` (multipart upload + cancel),
  `AttachmentPickerComponent` (paperclip button + `paste`
  listener + per-file chip with optional comment),
  `AttachmentViewComponent` (inline image or file card). Composer
  and dialog view both embed the picker; Send is disabled until
  every upload is `ready`. Clipboard paste triggers the same
  upload flow as the paperclip.
- **Tests** — `AttachmentFlowIT` covers upload → metadata → link →
  download by a second member, attachment-only 201 and empty 400,
  ban → 403 on attachment read, room delete → per-conversation
  directory vanishes. `MessageServiceTest` + `MessageBroadcasterTest`
  + `RoomServiceTest` updated for the new `AttachmentService`
  dep and the wider `MessageView` / `SendMessageRequest` shapes.
- **Smoke script** — Phase 6 block: upload PNG-ish bytes, link on
  send, download as another user, empty-message rejection.

### Verification

- `./gradlew test` — green including `AttachmentFlowIT` (4 cases).
- `scripts/docker-smoke.sh` — all eight phase blocks green on a
  clean rebuild (run before committing).
- Hot-reload UI: paperclip → chip with size → Send → other user's
  tab renders the inline image within ~1 s via STOMP fanout.

### Known tradeoffs carried forward

- **No EXIF scrub, no virus scan, no range requests.** Listed in
  plan §12; revisit post-demo.
- **Trust client-provided mime for the image-vs-other size bucket.**
  Server never executes based on it; blocklist catches the obvious
  risky prefixes (`application/x-msdownload`,
  `application/x-msdos-program`, `application/x-executable`,
  `text/html`).
- **Non-resumable uploads.** A dropped connection leaves a `.part`
  file; next upload overwrites it or the orphan sweep cleans up at
  the 1 h TTL. No tus/TUS protocol.
- **Per-uploader attachments only.** Re-using someone else's orphan
  id on a send → 409. Prevents "attach a file you didn't upload."
- **`@Lazy` on `AttachmentService` injection into `RoomService`**
  to break the cycle with `roomService ↔ attachmentService`
  (cleanup hook needs room service's deletion path; service needs
  room lookup for access check). One virtual call per
  `deleteConversationAndRoom`; negligible.

---

## Phase 7 — Notifications and unread state (done, 2026-04-19)

Goal: every conversation (room or dialog) shows a live unread count per
viewer, derived from a watermark `last_read_seq` per
(user, conversation). Author never accumulates self-unread, counts update
without a refresh, and a read on one tab zeroes the badge on every tab.

### Delivered

- **V8 migration** — `unread_marker(user_id, conversation_id,
  last_read_seq, updated_at)` with composite PK + `idx_unread_marker_user`.
  Three backfill inserts seed markers at `conversation.last_seq` for every
  `room_member` and both sides of every `dialog`, so existing users don't
  wake up to "everything unread" on the first post-migration deploy.
- **`UnreadMarker` / `UnreadMarkerId` / `UnreadRepository`** — JPA entity
  with `@IdClass`, two query methods (`findAllForUserIn`,
  `findAllForUserInConversation`) used by the bump path to avoid
  per-participant SELECTs.
- **`ConversationParticipantsQuery`** — tiny read-only helper; given a
  conversation id, returns `Set<UUID>` of participants by branching on
  `conversation.type` (room → `room_member.user_id`; dialog → `user_a_id`
  + `user_b_id`). Keeps `UnreadService` free of the
  room-vs-dialog concern.
- **`UnreadService`** — `initMarker(user, conv, atSeq)` idempotent upsert
  (catches `DataIntegrityViolationException` on concurrent calls),
  `bumpForMessage(conv, authorId, newSeq)` advances the author's marker
  to `newSeq` (no self-unread) and emits `unread.updated` with the
  derived count to every other participant via
  `UserEventPublisher` afterCommit, `markRead` clamps to
  `min(seq, conversation.last_seq)` and applies
  `max(existing, clamped)` so a late or replayed payload can never
  rewind the watermark, and `snapshot(userId)` returns one `UnreadView`
  per membership (rooms + dialogs).
- **Integration hooks** — `MessageService.post` and `postToDialog` call
  `unreadService.bumpForMessage` inside the same transaction as the
  message save, after the attachment link. `RoomService.create` /
  `RoomMembershipService.join` / `InvitationService.accept` /
  `DialogService.createFresh` all call `initMarker` at the current
  `last_seq` so a fresh participant never sees pre-existing history as
  unread. `@Lazy UnreadService` on the three room/dialog services to
  keep the MessageService → Unread → UserEventPublisher → Interceptor
  → DialogService cycle untangled.
- **REST** — `GET /api/unread` returns `[{conversationId, count}]` for
  the caller; `POST /api/conversations/{id}/read` with body `{seq}`
  returns 204. Non-participants get 403 on mark-read.
- **Frontend** — `UnreadService` signal (`countsMap`,
  computed `total`) subscribed to `/topic/users/{me}` for
  `unread.updated` events; `refresh()` hydrates from `GET /api/unread`
  on start. `UnreadBadgeComponent` renders the pill ("99+" cap).
  Rooms catalog + dialogs catalog render the badge next to each row;
  top-menu renders the total next to "Rooms". Message-list and
  dialog-view components watch the conversation's `highestSeq` signal
  with an `effect` and fire a fire-and-forget `markRead` whenever it
  advances; the per-tab `lastAckedSeq` guard collapses repeated
  triggers in the same frame, and the server enforces the clamp.
- **Smoke script** — Phase 7 block: fresh room, baseline 0 for Bob,
  two posts from Alice → Bob shows 2 / Alice shows 0 / mark-read
  collapses Bob back to 0, plus a non-participant Carol → 403.

### Verification

- `./gradlew test` — full suite green including `UnreadFlowIT`
  (7 cases: baseline, bump, author-no-self-bump, multiple bumps,
  clamp-at-last-seq, monotonic mark-read, non-participant 403) and
  updated service-unit tests for Room/Membership/Invitation/Dialog
  with the `@Lazy` `UnreadService` mock wiring.
- `scripts/docker-smoke.sh` — nine phase blocks green on a clean
  rebuild.

### Known tradeoffs carried forward

- **Derived counts, not stored.** The badge value is
  `conversation.last_seq - unread_marker.last_read_seq`, recomputed on
  each snapshot / bump instead of stored in a counter column. Keeps
  the write path a single marker upsert and sidesteps the
  increment-vs-rollback race that a `unread_count` column invites; at
  the cost of O(participants) per message for the fanout, which is
  fine at the 300-user scale.
- **`@Lazy UnreadService` on four collaborators.** Same rationale as
  Phase 5/6's `@Lazy`s — breaks the construction-order cycle without
  splitting services. Proxy overhead is one virtual call per
  `initMarker` / `bumpForMessage`. Alternative: move the
  snapshot/mark-read path into a separate `UnreadQueryService` that
  has no downstream deps, or extract the event fanout to a listener
  that subscribes to domain events. Not worth it at hackathon scale.
- **One `unread.updated` frame per non-author participant per
  message.** At a 300-member room, that's up to 299 broadcast
  publishes per post — all fire within the message
  transaction's afterCommit. Cheap for STOMP but worth denormalising
  (single fanout + client-side delta) if room sizes ever exceed the
  spec's 300 cap.
- **No catch-up on reconnect.** `UnreadService.start()` issues one
  `GET /api/unread` on sign-in; WS events refine it from there. A
  dropped connection's missed frames are re-absorbed on the next
  message or on explicit refresh. A `window.focus` rehydrate would
  close the gap if it ever becomes user-visible.

---

## Next

Phase 8 — admin UI polish (modal overlays replacing the inline tabbed
room-management panel, block-from-room-members context action,
admin-only room destroy confirmation UX).
