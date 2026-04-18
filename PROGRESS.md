# Implementation progress

Running log of phase completion against the high-level plan. Each phase is a
discrete milestone that leaves the app runnable end-to-end. Detailed per-phase
plans live under `howto/tasks/` (globally git-ignored on the dev machines).

## Summary

| Phase | Title                                             | Status       | Completed   |
|-------|---------------------------------------------------|--------------|-------------|
| 0     | Scaffolding and "it runs"                         | Done         | 2026-04-18  |
| 1     | Authentication and account lifecycle              | Not started  | —           |
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

## Next

Phase 1 — authentication and account lifecycle. Detailed plan to be written
under `howto/tasks/phase_1_plan.md` before implementation starts.
