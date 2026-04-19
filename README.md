# Chat Server

A classic web-based online chat: user accounts, public and private rooms, one-to-one
personal messaging, contacts/friends, file and image sharing, moderation, persistent
message history, and presence (online / AFK / offline). Targets ~300 concurrent
users and rooms up to 1,000 members.

Built for a hackathon. Grading requires `docker compose up` to work out of the box.

## Stack

- **Backend:** Java 21, Spring Boot 3.3, Gradle (Kotlin DSL), PostgreSQL 16,
  Flyway, JUnit 5 + Mockito. Session-based auth (no JWT).
- **Frontend:** Angular 17 (standalone components), strict TypeScript,
  STOMP-over-WebSocket client, `@ctrl/ngx-emoji-mart` emoji picker.
- **Transport:** HTTP for request/response and message sends, STOMP-over-
  WebSocket for message/presence/unread fan-out and per-user notifications.
- **Packaging:** multi-stage Dockerfile. The Angular production build is baked
  into the Spring Boot jar as static resources, so one container serves everything.

## Run it

Requires Docker Desktop (or equivalent) with Compose v2. Nothing else.

```
docker compose up --build
```

Then open [http://localhost:8080/](http://localhost:8080/).

The landing page shows a **Demo accounts** panel with three pre-seeded
users (`alice`, `bob`, `carol`) sharing the password `DemoPass123!`.
Copy buttons are provided on each row. The seed also populates a public
room with a short conversation, a private room with a pending
invitation, and a one-to-one dialog — so clicking around exercises the
full feature set immediately after the first boot.

The seed is gated by `CHAT_DEMO_SEED_ENABLED=true`, which
`docker-compose.yml` sets automatically. A deployment that omits that
env var boots with an empty database, and `GET /api/dev/demo-accounts`
returns 404.

Useful endpoints:

- `GET /api/health` — reports `status`, `db`, and `flywayVersion`. Shows
  `status: degraded` and `db: down` if PostgreSQL is unreachable rather than
  hanging.
- `GET /api/dev/demo-accounts` — dev-only; returns the seeded credentials
  the landing page renders. 404 when the demo seed is disabled.

Stop the stack:

```
docker compose down          # keeps the db volume
docker compose down -v       # wipes the db volume (fresh migrations next start)
```

## Repo layout

```
backend/    Spring Boot service. Gradle Kotlin DSL. Flyway migrations under
            src/main/resources/db/migration.
frontend/   Angular application. Built as static assets and copied into the
            backend jar by the Docker build.
shared/     Placeholder for code shared between the two (unused to date).
scripts/    docker-smoke.sh — end-to-end probe run once per phase.
howto/      Plans and guidance notes (globally git-ignored on this machine).
```

## Local development (hot reload)

Docker is the canonical path for grading, but for day-to-day iteration run the
backend and frontend directly on the host — Spring Boot DevTools auto-restarts
on class changes and the Angular dev server live-reloads on file changes, so a
code edit lands in the browser in seconds without rebuilding the Docker image.

Layout: Postgres stays in Docker, backend runs via `gradle bootRun`, frontend
runs via `ng serve` on port 4200 with a proxy config that forwards `/api/**`
and `/ws` to the backend on 8080.

### One-time setup

```
docker compose up -d db    # host-exposes 5432
cd frontend && npm install
```

### Run (three or four terminals)

```
# 1. Postgres (already up from setup)
docker compose up -d db

# 2. Backend
cd backend && ./gradlew bootRun

# 3. Frontend (Angular dev server + proxy to :8080)
cd frontend && npm start

# 4. (optional) Java continuous compile — triggers DevTools restart on .java edits
cd backend && ./gradlew -t classes
```

Open [http://localhost:4200/](http://localhost:4200/). Save a `.ts` / `.html`
/ `.css` file — the browser reloads in under a second. Save a `.java` file —
terminal 4 recompiles, Spring Boot DevTools sees new `.class` files and
restarts the context in ~1–2 s, and the next request hits the new code.

Terminal 4 is optional. Without it, trigger a backend restart by running
`./gradlew classes` once after a batch of edits.

Run the backend tests:

```
cd backend && ./gradlew test
```

Smoke-test the packaged Docker image — once per phase, not on every
change. Wipes the db volume, rebuilds the image, starts the stack, and
walks every shipped phase's primary endpoints with curl:

```
scripts/docker-smoke.sh
```

Requires JDK 21 on `JAVA_HOME` and Node.js 20+ (Node 19 is unsupported by
Angular).

## End-to-end UI tests (Playwright)

The suite under `frontend/e2e/` covers every UI user story in
[`howto/tasks/test_scenarios.md`](howto/tasks/test_scenarios.md) except the
stretch Jabber section. Current state: **78 tests passing, 16 documented
skips** (perf, wall-clock AFK, large-file uploads, and a handful of
cross-referenced cases — each with an inline reason).

Bring up the stack first (the tests hit the real backend + Postgres):

```
docker compose up -d
cd frontend
npm run e2e:install      # installs the Chromium browser (one-time)
npm run e2e              # headless, list reporter
npm run e2e:ui           # interactive watch mode
```

`playwright.config.ts` auto-starts `ng serve` via `webServer` and reuses an
already-running one locally. `baseURL` defaults to `http://localhost:4200`.

### Spec layout

```
frontend/e2e/
  helpers/auth.ts              CSRF-aware API wrappers, login/register/logout,
                               friend/room/message/attachment helpers
  login.spec.ts                §2.1.3 sign-in paths
  auth-register.spec.ts        §2.1.1–§2.1.2 registration
  auth-session.spec.ts         §2.1.3 + §2.2.4 keep-me-signed-in, scoped sign-out
  auth-password-reset.spec.ts  §2.1.4 forgot → dev token → reset
  auth-password-change.spec.ts §2.1.4 change password + other-session revocation
  auth-account.spec.ts         §2.1.2 + §2.1.5 username immutable, delete cascade
  presence-sessions.spec.ts    §2.2 presence dot + sessions list/revoke
  contacts.spec.ts             §2.3 friend requests, unfriend, block/unblock, DM gating
  rooms.spec.ts                §2.4 create/join/leave/catalog/search/invitations
  rooms-management.spec.ts     §2.4 admin: delete message, ban/unban, promote/demote
  messaging.spec.ts            §2.5 send/reply/edit/delete/ordering/infinite scroll
  attachments.spec.ts          §2.6 upload, download gating, ban lockout, cascade
  notifications.spec.ts        §2.7 unread badge appears and clears
  ui-layout.spec.ts            §4 landmarks, top menu, manage-room modal tabs
  non-functional.spec.ts       §3 multi-tab; perf + wall-clock cases skipped
```

Every test case carries a `TC-…` ID that maps back to
`howto/tasks/test_scenarios.md`, which in turn points to the requirement
clauses in `howto/tasks/app_requirements.txt`. Coverage gaps surface by
diffing the two.

### Quirks worth knowing

- **CSRF**: mutating API calls from Playwright's `APIRequestContext` must warm
  the `XSRF-TOKEN` cookie via a GET first and then include `X-XSRF-TOKEN` on
  the POST/PATCH/DELETE. `helpers/auth.ts` exposes `apiPost` / `apiDelete`
  that handle this — prefer them over raw `request.post` calls.
- **Disposable users**: tests generate unique `<prefix>_<ts>@e2e.test` users
  to isolate state. After registering via API, call
  `logoutViaApi(ctx.request)` before re-logging via the UI so session counts
  remain predictable.
- **STOMP reconnect race**: where a test sends via the UI and needs the
  bubble to appear in the list, we `page.reload()` to rehydrate from REST —
  live auto-scroll assertions (TC-MSG-016/017) are intentionally skipped for
  the same reason.
- **Dev-only endpoints**: `GET /api/dev/password-reset-tokens` (used by the
  reset spec) is only mounted under the `dev` Spring profile, which is what
  `docker-compose.yml` activates.

## Implementation progress

See [`PROGRESS.md`](PROGRESS.md) for the status of each phase, what's delivered,
and known tradeoffs.

## Conventions

See `CLAUDE.md` for the conventions we follow in this repo. Short version:

- Don't add Javadoc unless the method is genuinely complex.
- Prefer meaningful names to comments.
- Never guess database object names — verify against migrations and code.
- No JWT. Session cookies only.
