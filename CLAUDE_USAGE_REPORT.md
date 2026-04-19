# How Claude Was Used on This Project

A concise, step-by-step account of the workflow that produced this repo with
Claude Code (Opus 4.7, 1M context). Every file under `backend/`, `frontend/`,
and `frontend/e2e/` was authored in-session; this document explains how.

## Inputs Claude worked from

- **Requirements** — `howto/tasks/app_requirements.txt` (spec + wireframes).
- **Stack constraints** — `howto/general/*.txt` (fixed Java 21 / Spring Boot /
  Angular / Postgres / Flyway / no-JWT; `docker compose up` must boot it).
- **Implementation hints** — three short `hint*.txt` notes from the user
  (e.g. "use a per-conversation watermark, not a per-user queue").
- **Conventions** — `CLAUDE.md` (no Javadoc by default, verify DB names
  before use, match existing module style).

## The step-by-step loop

The same five-step loop was applied to every piece of work. Phase plans and
the test-scenarios doc are its durable artifacts.

1. **Architect once, up front.** Claude wrote
   `howto/tasks/app_requirements_plan.md` — proposed runtime topology,
   module layout, transport split (REST + STOMP), and the list of ten
   implementation phases. The human reviewed and redirected before any code
   was written.

2. **Plan each phase separately.** For every phase, a dedicated
   `howto/tasks/phase_N_plan.md` was produced first: exit criteria, data
   model changes, endpoint table, and a DoD checklist. The human read these
   before green-lighting the phase.

3. **Implement the phase end-to-end.** Claude wrote the Flyway migration,
   backend service + controller, frontend service + component, and tests in
   one pass, keeping to the conventions in `CLAUDE.md`.

4. **Verify before commit.** `./gradlew test` on the backend plus a browser
   check of the new UI. The `scripts/docker-smoke.sh` script was run once per
   completed phase (wipes the db, rebuilds the image, curls every endpoint).

5. **Commit as `implement Phase N: <summary>`.** One commit per phase in
   normal cases; two when the DoD surfaced a sub-task (e.g.
   `3dcebd9 Phase 3.1: split "remove" from "ban"`).

## Phase-by-phase ledger

All eleven phases and the follow-ups, in order, from `git log`:

| Commit | Phase | What Claude built |
|--------|-------|-------------------|
| `1863aa6` | 0 | `docker compose up` baseline — Angular + Spring Boot + Postgres + Flyway wired end-to-end. |
| `a071af0` | 1 | Auth: registration, session login, password reset, account lifecycle. |
| `8af6c21` | 2 | Rooms: public/private, membership, roles, admin actions. |
| `b755eed` | 3 | Messaging: REST send + STOMP fan-out, edit, soft-delete, reply, keyset history. |
| `4cab30c` | 3 fix | DoD gaps caught during browser verification. |
| `3dcebd9` | 3.1 | Split "remove member" from "ban" in room moderation. |
| `a794d42` | 4 | Presence (online / AFK / offline) with per-tab pings. |
| `e787e8d` | 5 | Contacts, friend requests, user-to-user bans, personal dialogs. |
| `7fc8e9e` | 6 | Attachments on messages (files + images, rate-limited uploads). |
| `da799e0` | 7 | Notifications and unread state. |
| `90aa231` | 8 | Admin UI polish (Manage Room modal with five tabs). |
| `8a471a2` | 9 | End-to-end hardening for the demo. |
| `6c07de4` | audit | Gap-closure pass driven by `howto/tasks/gap_report_2026-04-19.md`. |
| `1b600c0` + `b4f55fc` | e2e | Playwright suite: 78 green UI tests + `frontend/e2e/` scaffolding. |

## Tooling patterns that supported the loop

- **Exploration before implementation.** For each non-trivial step, Claude
  ran an `Explore` sub-agent with a concrete checklist (file paths, selector
  tables, endpoint shapes) before writing code. That map was the input to
  step 3 of the loop.
- **Traceable test IDs.** Every Playwright test carries a `TC-…` ID that
  maps to `howto/tasks/test_scenarios.md`, which in turn points back to the
  requirement clause in `app_requirements.txt`. Diffing the three files
  surfaces coverage gaps mechanically.
- **Honest `test.skip` with reasons.** When a case couldn't be cleanly
  automated (wall-clock AFK, 20 MB uploads, STOMP reconnect races),
  `test.skip(...)` carries an inline rationale rather than silently passing.
  Sixteen of the ninety-four documented cases are in this state.
- **Auto-memory across sessions.** Project-wide facts that future sessions
  need (e.g. the blocked `Co-Authored-By Claude` commit-trailer hook) are
  persisted in `~/.claude/.../memory/` and avoid re-litigation.

## Failure modes actually encountered

Three recurring frictions — worth naming so future sessions mitigate them:

1. **Grounding drift.** Claude occasionally answered confidently from
   assumption instead of re-reading the file. Two concrete misses shipped
   before the e2e pass caught them: "no unban UI" (an `Unblock` button
   exists in `contacts.component.html`), and TC-ROOM-016's claim that
   "Remove is treated as a ban" (backend implements remove and ban as
   separate actions). Both are now explicitly called out in
   `test_scenarios.md`.
2. **Selector/timing flakiness on first UI-test pass.** The messaging spec
   went 11 → 3 → 1 → 0 failing tests over four edit/run cycles, driven
   mostly by CSS-selector mismatches and WebSocket-reconnection races.
3. **Doc sprawl inside `howto/`.** Eleven phase plans + gap reports + a
   session log accumulated. Valuable during implementation, obvious sprawl
   after. The durable surface kept committed is `README.md`, `CLAUDE.md`,
   and this file; the rest stays globally git-ignored.

## Final state

- `docker compose up` boots the full stack with a seeded demo.
- Requirements §2–§5 implemented; §6 (Jabber/XMPP federation) intentionally
  out of scope.
- 78 green Playwright tests, 16 documented skips, 0 failing.
- Two known requirement-vs-implementation gaps documented inline (user-ban
  reversibility, remove-as-ban).

The workflow above — plan, phase, implement, verify, commit — is what made
that reachable inside the hackathon window. The plan and test-scenarios
artifacts are what kept Claude's output aligned with the spec across
sessions.
