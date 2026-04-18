# Chat Server

A classic web-based online chat: user accounts, public and private rooms, one-to-one
personal messaging, contacts/friends, file and image sharing, moderation, persistent
message history, and presence (online / AFK / offline). Targets ~300 concurrent
users and rooms up to 1,000 members.

Built for a hackathon. Grading requires `docker compose up` to work out of the box.

## Stack

- **Backend:** Java 21, Spring Boot 3.3, Gradle (Kotlin DSL), PostgreSQL 16,
  Flyway, JUnit 5 + Mockito. Session-based auth (no JWT).
- **Frontend:** Angular 17 (standalone components), strict TypeScript, STOMP
  WebSocket client (added in a later phase).
- **Transport:** HTTP for request/response, STOMP-over-WebSocket for live events.
- **Packaging:** multi-stage Dockerfile. The Angular production build is baked
  into the Spring Boot jar as static resources, so one container serves everything.

## Run it

Requires Docker Desktop (or equivalent) with Compose v2. Nothing else.

```
docker compose up --build
```

Then open [http://localhost:8080/](http://localhost:8080/).

Useful endpoints:

- `GET /api/health` — reports `status`, `db`, and `flywayVersion`. Shows
  `status: degraded` and `db: down` if PostgreSQL is unreachable rather than
  hanging.

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
shared/     Placeholder for code shared between the two (empty in Phase 0).
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

Requires JDK 21 on `JAVA_HOME` and Node.js 20+ (Node 19 is unsupported by
Angular).

## Implementation progress

See [`PROGRESS.md`](PROGRESS.md) for the status of each phase, what's delivered,
and known tradeoffs.

## Conventions

See `CLAUDE.md` for the conventions we follow in this repo. Short version:

- Don't add Javadoc unless the method is genuinely complex.
- Prefer meaningful names to comments.
- Never guess database object names — verify against migrations and code.
- No JWT. Session cookies only.
