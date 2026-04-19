# How Claude Was Used on This Project — A Retrospective

An honest-as-possible summary of what Claude Code (Opus 4.7, 1M context) did
on this repo, what it cost in friction, and what patterns kept it useful.

## TL;DR

Claude implemented essentially the entire stack — Spring Boot backend, Angular
17 frontend, Playwright e2e suite — in ten planned phases plus iteration.
Speed and breadth were the headline wins. The real costs were not output
quality but **verification overhead** and **grounding drift**: Claude
repeatedly wrote code against assumptions that the actual codebase then
contradicted, and the burden of catching that fell on the human. Structured
conventions (`CLAUDE.md`, phase plans, a separate test-scenarios doc) kept the
drift inside tolerable bounds.

## Scope Claude actually covered

From `git log` on this branch:

- **Phase 0–9** — scaffolding, auth, rooms, messaging/WS, presence, contacts +
  bans, attachments, notifications/unread, admin UI, end-to-end hardening.
  Twenty-four commits from `1863aa6 scaffold Phase 0` through
  `8a471a2 implement Phase 9`.
- **Gap-closure pass** — `6c07de4 close requirement gaps` addressed concrete
  diffs against `howto/tasks/app_requirements.txt` (add-friend from rooms,
  paste attachments, rate limits, disconnect-aware presence).
- **Playwright e2e suite** — `1b600c0` + `b4f55fc`: 78 green UI tests covering
  every requirement section except the stretch Jabber one, with 16 inline-
  reasoned skips and a full traceability doc at
  `howto/tasks/test_scenarios.md`.

No human-authored code was deliberately left in this branch. Every file under
`backend/`, `frontend/`, `scripts/`, and the e2e folder originated with
Claude, reviewed and redirected by the human operator.

## What worked well

1. **End-to-end breadth in a single tool.** The same session pivoted from
   Java/JPA/Flyway migrations, through Angular standalone components and
   RxJS signals, into Playwright specs and GitHub-flavored Markdown docs
   without a context switch penalty for the human. That "one collaborator
   who knows the whole stack" property is the single biggest productivity
   win the tool delivered.

2. **Plans as a forcing function.** Each phase began with a plan file under
   `howto/tasks/phase_*.md` that the human skimmed before Claude started
   writing code. Phases where that plan was read and redirected *before*
   coding took fewer iterations than phases where Claude jumped straight to
   code.

3. **Convention capture via `CLAUDE.md`.** The repo's `CLAUDE.md` (no Javadoc
   by default, no guessing DB names, match existing module style) was
   honored across sessions. The separate auto-memory system also captured
   friction-points across conversations — e.g. the blocked
   `Co-Authored-By Claude` commit-trailer hook was learned once and not
   repeated.

4. **Honest skips with inline reasons.** When something couldn't be cleanly
   automated (wall-clock AFK, STOMP reconnect races, 20 MB upload rejection),
   Claude used `test.skip(...)` with a human-readable rationale rather than
   faking a pass. That made the 16 skips auditable in one pass — see the
   coverage table in `README.md`.

5. **Self-verification before claiming done.** Every phase commit was
   preceded by `./gradlew test` and a compile; the e2e commits ran Playwright
   end-to-end. Failures were fixed in the same session rather than shipped
   forward.

## What didn't work / cost

1. **Grounding drift. This is the dominant failure mode.** Claude routinely
   wrote or asserted things that the actual code contradicted:
   - It told the human "there is no UI to unblock a banned user"
     (`test_scenarios.md` §11, answer #1), then later discovered the
     `Unblock` button literally sitting in `contacts.component.html`.
   - It initially wrote `test_scenarios.md` TC-ROOM-016 asserting
     "Remove member is treated as a ban" (faithful to §2.4.8 of the
     requirements). The backend does not implement that rule; the e2e test
     had to be rewritten to assert actual behavior and flag the gap.
   - The first Playwright run discovered that half the API-driven tests
     returned 403 because Spring's CSRF filter was enforcing
     `X-XSRF-TOKEN`. That cookie warming is a one-line detail the human
     had to catch from the failing traces.

   **Mitigation that actually helped:** forcing Claude to use an Explore
   subagent with a concrete checklist ("confirm file paths, grep for
   selectors, return file:line pointers") before writing code. Ad-hoc
   "check the code" prompts produced worse grounding than a structured
   exploration pass.

2. **Selector and timing flakiness in UI tests.** Typical iteration on the
   first pass through the messaging spec: 11 tests failing on the first run,
   down to 3, to 1, to 0 over four edit/run cycles. Root causes were split
   between legitimate STOMP reconnection races (two tests ended up skipped
   on purpose) and Claude picking CSS selectors that matched the wrong
   elements (`.badge` vs `.unread-badge`, clicking the emoji-picker's
   disabled search clear button). Each miss cost ~30–60 s of wall-clock on
   a Playwright re-run, and several required screenshot/page-snapshot
   diffing to resolve.

3. **Scope creep under "auto mode".** When invited to run autonomously
   ("implement and run tests in §2.1 Authentication"), Claude did the job,
   but `npm install` side effects, extra dependency bumps, and bolt-on
   helpers sometimes arrived unannounced. The human had to re-read each
   diff carefully. The win from autonomy is real but it is not free.

4. **Documentation files proliferate if unchecked.** The `howto/` tree
   accumulated eleven phase plans, gap reports, and session logs, all
   gitignored locally. Useful while the work was live; obvious sprawl
   once the feature was done. Keeping `CLAUDE.md` and `README.md` as the
   durable surface, and letting ephemeral plans die in the scratch folder,
   kept the committed doc set small.

5. **Cost/latency that doesn't show in the diff.** A single 78-test
   Playwright run ≈ 3 minutes; a full `git log` of the work is 24 commits,
   each typically preceded by 3–10 intermediate runs during iteration.
   The token bill, while not tracked precisely here, is substantial; using
   fast mode (Opus 4.6) for narrow lookups and full Opus 4.7 only for
   stitching phases together was the practical compromise.

## Patterns that consistently paid off

- **Traceable specs.** Every `TC-…` ID in `test_scenarios.md` points to a
  clause in `app_requirements.txt` *and* to a passing (or skipped) spec in
  `frontend/e2e/`. That three-way link is the artifact that made the
  requirement-vs-implementation gaps visible.
- **Small, named helpers in test code.** `apiPost`, `registerViaApi`,
  `makeFriends`, `createPublicRoom` — writing these up-front in
  `helpers/auth.ts` meant later specs read like requirements prose.
- **Sub-agent research before implementation.** The three "map the
  code" Explore runs in this repo account for a disproportionate share of
  the time-saved: they produced concrete selector and endpoint tables that
  the spec-writing passes then consumed without re-guessing.
- **Reverting to REST when WebSocket flakes.** Several messaging specs
  `page.reload()` after a UI send instead of trusting the live STOMP push.
  Slower per-test but deterministic.

## Patterns that consistently didn't

- Trusting a remembered fact without re-reading the file — see the
  "ban is terminal" miss.
- Letting Claude pick selectors from a generic description. Always better
  to grep the actual template first.
- Skipping the plan step for "small" changes. The small changes were the
  ones most likely to need a follow-up commit.

## Final ledger

- **Shipped:** a runnable `docker compose up` chat server implementing
  nearly all of `app_requirements.txt` §2–§5, with demo seed data, an
  admin UI, a docker-smoke script, and a Playwright suite that fails
  loudly when any of it regresses.
- **Not shipped:** the stretch Jabber/XMPP federation section (§6), plus
  perf and wall-clock-bound scenarios deliberately skipped in the e2e
  suite.
- **Known gaps (documented, not hidden):** two specific requirement-vs-
  implementation mismatches on user-to-user ban reversibility and
  room-remove-as-ban behavior. Both are called out in `test_scenarios.md`
  §1 and §4 with file pointers.

The overall conclusion is the boring one: Claude dramatically reduced the
wall-clock time to a usable chat server, at the cost of a human operator
who had to grep, verify, and redirect on a roughly per-commit cadence. The
tooling around it — plans, memory, `CLAUDE.md`, the traceable test doc —
was what made that redirect cheap enough to be worth doing.
