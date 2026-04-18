#!/usr/bin/env bash
#
# Docker-image smoke test. Runs once at the end of each phase per
# howto/general/phase_implementation_rules.txt rule 34.
#
# What it does:
#   1. docker compose down -v    (wipes the db volume → fresh migrations)
#   2. docker compose up -d --build
#   3. polls /api/health until ok
#   4. walks the primary endpoints of every shipped phase with curl
#   5. exits 0 on success, non-zero on any mismatch, with the failing
#      assertion printed
#
# Why this exists separately from the Testcontainers IT suite: the ITs run
# against Spring Boot in-process; this script proves the *packaged* Docker
# image boots cleanly, serves the Angular bundle, applies Flyway from zero,
# and answers on 8080.
#
# Usage: scripts/docker-smoke.sh
#
# Assumes Docker Desktop (or equivalent) with Compose v2, curl, and python3
# on PATH.

set -Eeuo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TS="$(date +%s)"
ALICE_EMAIL="smoke-alice-${TS}@example.com"
ALICE_USER="smokea${TS}"
BOB_EMAIL="smoke-bob-${TS}@example.com"
BOB_USER="smokeb${TS}"
PASSWORD="SmokePass123!"
ALICE_JAR="$(mktemp)"
BOB_JAR="$(mktemp)"

cleanup() {
  rm -f "$ALICE_JAR" "$BOB_JAR" 2>/dev/null || true
}
trap cleanup EXIT

red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
step()  { printf '\n\033[36m── %s\033[0m\n' "$*"; }

fail() {
  red "FAIL: $*"
  red "----- app logs (last 60 lines) -----"
  docker compose logs --tail=60 app || true
  exit 1
}

csrf_of() {
  local jar="$1"
  # XSRF-TOKEN cookie lives in the 7th tab-separated column of the jar file.
  awk '$6 == "XSRF-TOKEN" { print $7 }' "$jar" | tail -n1
}

json_field() {
  # $1 = body, $2 = dotted path (e.g. ".id" or ".items[0].seq")
  python3 -c "import sys,json; d=json.loads(sys.stdin.read()); \
    exec('print(d'+sys.argv[1]+')')" "$2" <<<"$1"
}

assert_status() {
  local want="$1" got="$2" ctx="$3"
  if [[ "$got" != "$want" ]]; then
    fail "$ctx: expected HTTP $want, got $got"
  fi
}

call() {
  # call METHOD PATH [--jar JAR] [--body JSON] [--expect STATUS]
  local method="$1" path="$2"; shift 2
  local jar="" body="" expect="200"
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --jar)    jar="$2"; shift 2 ;;
      --body)   body="$2"; shift 2 ;;
      --expect) expect="$2"; shift 2 ;;
      *) fail "call: unknown arg $1" ;;
    esac
  done
  local args=(-sS -X "$method" -o /tmp/smoke.body -w '%{http_code}')
  if [[ -n "$jar" ]]; then
    args+=(-c "$jar" -b "$jar")
    local c; c="$(csrf_of "$jar" || true)"
    if [[ -n "$c" ]]; then args+=(-H "X-XSRF-TOKEN: $c"); fi
  fi
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  local code; code="$(curl "${args[@]}" "${BASE_URL}${path}")"
  assert_status "$expect" "$code" "$method $path"
  cat /tmp/smoke.body
}

seed_csrf() {
  local jar="$1"
  curl -sS -c "$jar" -b "$jar" -o /dev/null "${BASE_URL}/api/auth/csrf"
}

# ───────────────────────── phase 0: stack up from scratch ─────────────────

step "Phase 0 — clean rebuild and health"
docker compose down -v >/dev/null 2>&1 || true
docker compose up -d --build >/dev/null
for i in $(seq 1 60); do
  if curl -fsS "${BASE_URL}/api/health" >/dev/null 2>&1; then
    green "  /api/health ready in ${i}s"; break
  fi
  sleep 1
  [[ $i -eq 60 ]] && fail "backend never became healthy"
done
HEALTH="$(curl -sS "${BASE_URL}/api/health")"
[[ "$(json_field "$HEALTH" "['status']")" == "ok" ]] || fail "health.status != ok: $HEALTH"
[[ "$(json_field "$HEALTH" "['db']")" == "ok" ]] || fail "health.db != ok: $HEALTH"
FLYWAY="$(json_field "$HEALTH" "['flywayVersion']")"
green "  flywayVersion=$FLYWAY"

# ───────────────────────── phase 1: auth lifecycle ───────────────────────

step "Phase 1 — register, login, /me, logout"
seed_csrf "$ALICE_JAR"
call POST /api/auth/register --jar "$ALICE_JAR" \
     --body "{\"email\":\"$ALICE_EMAIL\",\"username\":\"$ALICE_USER\",\"password\":\"$PASSWORD\"}" \
     >/dev/null
ME="$(call GET /api/auth/me --jar "$ALICE_JAR")"
[[ "$(json_field "$ME" "['username']")" == "$ALICE_USER" ]] || fail "/me: username mismatch: $ME"
call POST /api/auth/logout --jar "$ALICE_JAR" --expect 204 >/dev/null
call GET  /api/auth/me --jar "$ALICE_JAR" --expect 401 >/dev/null

# Log back in for the rest of the walk.
seed_csrf "$ALICE_JAR"
call POST /api/auth/login --jar "$ALICE_JAR" \
     --body "{\"email\":\"$ALICE_EMAIL\",\"password\":\"$PASSWORD\",\"rememberMe\":false}" \
     >/dev/null
green "  auth lifecycle ok for $ALICE_USER"

# ───────────────────────── phase 2: rooms and membership ─────────────────

step "Phase 2 — create room, join, roles"
seed_csrf "$BOB_JAR"
call POST /api/auth/register --jar "$BOB_JAR" \
     --body "{\"email\":\"$BOB_EMAIL\",\"username\":\"$BOB_USER\",\"password\":\"$PASSWORD\"}" \
     >/dev/null
BOB_ID="$(json_field "$(call GET /api/auth/me --jar "$BOB_JAR")" "['id']")"

ROOM_JSON="$(call POST /api/rooms --jar "$ALICE_JAR" --expect 201 \
  --body "{\"name\":\"smoke-${TS}\",\"description\":\"x\",\"visibility\":\"public\"}")"
ROOM_ID="$(json_field "$ROOM_JSON" "['id']")"

call POST "/api/rooms/$ROOM_ID/join" --jar "$BOB_JAR" --expect 204 >/dev/null

MEMBERS="$(call GET "/api/rooms/$ROOM_ID/members" --jar "$ALICE_JAR")"
COUNT="$(json_field "$MEMBERS" "")"
# count rows by re-parsing
COUNT="$(python3 -c "import sys,json; print(len(json.loads(sys.stdin.read())))" <<<"$MEMBERS")"
[[ "$COUNT" == "2" ]] || fail "room members count != 2: $MEMBERS"
green "  room $ROOM_ID has 2 members after bob joined"

# ───────────────────────── phase 3: messaging + history ──────────────────

step "Phase 3 — message post / edit / delete / history"
MSG1="$(call POST "/api/rooms/$ROOM_ID/messages" --jar "$ALICE_JAR" --expect 201 \
  --body '{"text":"smoke 1"}')"
MSG1_ID="$(json_field "$MSG1" "['id']")"
SEQ1="$(json_field "$MSG1" "['seq']")"
[[ "$SEQ1" == "1" ]] || fail "first message seq should be 1: $MSG1"

call POST "/api/rooms/$ROOM_ID/messages" --jar "$BOB_JAR" --expect 201 \
  --body '{"text":"smoke 2"}' >/dev/null
call POST "/api/rooms/$ROOM_ID/messages" --jar "$ALICE_JAR" --expect 201 \
  --body '{"text":"smoke 3"}' >/dev/null

EDIT="$(call PATCH "/api/messages/$MSG1_ID" --jar "$ALICE_JAR" \
  --body '{"text":"smoke 1 (edited)"}')"
[[ "$(json_field "$EDIT" "['body']")" == "smoke 1 (edited)" ]] \
  || fail "edit did not stick: $EDIT"
[[ "$(json_field "$EDIT" "['editedAt']")" != "None" ]] \
  || fail "editedAt not set: $EDIT"

call DELETE "/api/messages/$MSG1_ID" --jar "$ALICE_JAR" --expect 204 >/dev/null

HIST="$(call GET "/api/rooms/$ROOM_ID/messages" --jar "$ALICE_JAR")"
TOTAL="$(python3 -c "import sys,json; d=json.loads(sys.stdin.read()); print(len(d['items']))" <<<"$HIST")"
[[ "$TOTAL" == "3" ]] || fail "history should still show 3 items (soft delete): $HIST"
DELETED_SEQ1="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
for m in d['items']:
  if m['seq']==1:
    print('body=%s deletedAt=%s' % (m.get('body'), m.get('deletedAt')))" <<<"$HIST")"
[[ "$DELETED_SEQ1" == "body=None deletedAt="*[0-9T:.Z-]* ]] \
  || fail "deleted seq=1 not surfaced as soft delete: $DELETED_SEQ1"
green "  messaging: post/edit/delete/history ok (3 rows, seq=1 soft-deleted)"

# ───────────────────────── phase 3.1: remove vs ban ──────────────────────

step "Phase 3.1 — remove lets rejoin, ban does not"
call DELETE "/api/rooms/$ROOM_ID/members/$BOB_ID" --jar "$ALICE_JAR" --expect 204 >/dev/null
BANS_AFTER_REMOVE="$(call GET "/api/rooms/$ROOM_ID/bans" --jar "$ALICE_JAR")"
[[ "$BANS_AFTER_REMOVE" == "[]" ]] || fail "remove should NOT populate ban list: $BANS_AFTER_REMOVE"
call POST "/api/rooms/$ROOM_ID/join" --jar "$BOB_JAR" --expect 204 >/dev/null
green "  remove → rejoin works, no ban trail"

call POST "/api/rooms/$ROOM_ID/bans" --jar "$ALICE_JAR" --expect 201 \
  --body "{\"userId\":\"$BOB_ID\",\"reason\":\"smoke\"}" >/dev/null
call POST "/api/rooms/$ROOM_ID/join" --jar "$BOB_JAR" --expect 403 >/dev/null
call DELETE "/api/rooms/$ROOM_ID/bans/$BOB_ID" --jar "$ALICE_JAR" --expect 204 >/dev/null
call POST "/api/rooms/$ROOM_ID/join" --jar "$BOB_JAR" --expect 204 >/dev/null
green "  ban → rejoin 403, unban → rejoin 204"

# ───────────────────────── phase 4: presence ────────────────────────────

step "Phase 4 — presence ping, bulk, config"
CONFIG="$(call GET /api/presence/config --jar "$ALICE_JAR")"
PING_MS="$(json_field "$CONFIG" "['pingIntervalMs']")"
[[ "$PING_MS" -gt 0 ]] || fail "presence config must expose a positive pingIntervalMs: $CONFIG"

call POST /api/presence/ping --jar "$ALICE_JAR" --expect 204 \
  --body '{"tabId":"smoke-tab"}' >/dev/null
ALICE_ID="$(json_field "$(call GET /api/auth/me --jar "$ALICE_JAR")" "['id']")"

BULK="$(call GET "/api/presence?userIds=$ALICE_ID,$BOB_ID" --jar "$ALICE_JAR")"
ALICE_STATUS="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
for v in d:
  if v['userId']=='$ALICE_ID': print(v['status'])" <<<"$BULK")"
[[ "$ALICE_STATUS" == "online" ]] || fail "alice should be online after ping: $BULK"
green "  ping → bulk status returns online; config pingIntervalMs=${PING_MS} ms"

call POST /api/presence/ping --jar "$ALICE_JAR" --expect 400 \
  --body '{"tabId":""}' >/dev/null
green "  blank tabId rejected with 400"

# ───────────────────────── static assets ─────────────────────────────────

step "Static — SPA fallback and Angular bundle"
INDEX="$(curl -sS -o /tmp/smoke.body -w '%{http_code}' "${BASE_URL}/")"
assert_status 200 "$INDEX" "GET /"
grep -q '<app-root' /tmp/smoke.body || fail "index.html missing <app-root>"

DEEP="$(curl -sS -o /tmp/smoke.body -w '%{http_code}' "${BASE_URL}/rooms/$ROOM_ID")"
assert_status 200 "$DEEP" "deep-link GET /rooms/<id>"
grep -q '<app-root' /tmp/smoke.body || fail "deep-link did not forward to index.html"
green "  SPA fallback serves index.html for /rooms/<id>"

# ───────────────────────── done ──────────────────────────────────────────

green ""
green "All docker smoke checks passed (phases 0, 1, 2, 3, 3.1, 4)."
