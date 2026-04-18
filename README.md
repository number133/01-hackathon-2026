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

## Local development (outside Docker)

Docker is the canonical path. Use these only when iterating quickly on one side.

### Backend

Requires JDK 21 on `JAVA_HOME` and a running PostgreSQL matching the defaults in
`backend/src/main/resources/application.yml` (database `chat`, user `chat`,
password `chat`, on `localhost:5432`). The simplest local loop is to run just
the `db` service from compose and point the backend at it:

```
docker compose up -d db
cd backend
./gradlew bootRun
```

Run the backend tests:

```
cd backend
./gradlew test
```

### Frontend

Requires Node.js 20+ (Node 18 works; Node 19 is unsupported by Angular and may
emit warnings).

```
cd frontend
npm install
npm start
```

Dev server runs on port 4200. Proxy `/api/**` to `http://localhost:8080` if you
point it at a locally running backend; otherwise the health widget will report a
failure — which is the intended visible behavior for a down backend.

Production build (what the Docker image runs):

```
cd frontend
npm run build
```

## Implementation progress

See [`PROGRESS.md`](PROGRESS.md) for the status of each phase, what's delivered,
and known tradeoffs.

## Conventions

See `CLAUDE.md` for the conventions we follow in this repo. Short version:

- Don't add Javadoc unless the method is genuinely complex.
- Prefer meaningful names to comments.
- Never guess database object names — verify against migrations and code.
- No JWT. Session cookies only.
