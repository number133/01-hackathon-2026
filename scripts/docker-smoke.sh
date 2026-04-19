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

# ───────────────────────── phase 5: contacts + dialogs ──────────────────

step "Phase 5 — friend request, dialog, ban, refriend"

# Alice sends Bob a friend request.
FR="$(call POST /api/friend-requests --jar "$ALICE_JAR" --expect 201 \
  --body "{\"username\":\"${BOB_USER}\"}")"
FR_ID="$(json_field "$FR" "['id']")"

# Bob accepts.
call POST "/api/friend-requests/$FR_ID/accept" --jar "$BOB_JAR" --expect 204 >/dev/null

# Alice's friends list contains Bob.
FRIENDS="$(call GET /api/friends --jar "$ALICE_JAR")"
echo "$FRIENDS" | grep -q "$BOB_USER" \
  || fail "bob not in alice's friends: $FRIENDS"
green "  friend request → accept → friends list ok"

# Alice opens a dialog with Bob.
DLG="$(call POST /api/dialogs --jar "$ALICE_JAR" --expect 201 \
  --body "{\"userId\":\"$BOB_ID\"}")"
DLG_ID="$(json_field "$DLG" "['id']")"

# Alice sends a DM.
call POST "/api/dialogs/$DLG_ID/messages" --jar "$ALICE_JAR" --expect 201 \
  --body '{"text":"hi bob"}' >/dev/null

# Bob can read history.
HIST="$(call GET "/api/dialogs/$DLG_ID/messages" --jar "$BOB_JAR")"
python3 -c "import sys,json
d=json.loads(sys.stdin.read())
assert d['items'][0]['body']=='hi bob', d" <<<"$HIST"
green "  dialog created + DM delivered"

# Alice bans Bob. Dialog flips frozen.
call POST "/api/user-bans/$BOB_ID" --jar "$ALICE_JAR" --expect 204 >/dev/null
DLG_AFTER="$(call GET "/api/dialogs/$DLG_ID" --jar "$ALICE_JAR")"
[[ "$(json_field "$DLG_AFTER" "['frozen']")" == "True" ]] \
  || fail "dialog should be frozen after ban: $DLG_AFTER"

# Bob cannot send.
call POST "/api/dialogs/$DLG_ID/messages" --jar "$BOB_JAR" --expect 409 \
  --body '{"text":"blocked?"}' >/dev/null
green "  ban → dialog frozen, target cannot send"

# Unban: dialog still frozen (no friendship).
call DELETE "/api/user-bans/$BOB_ID" --jar "$ALICE_JAR" --expect 204 >/dev/null
DLG_AFTER2="$(call GET "/api/dialogs/$DLG_ID" --jar "$ALICE_JAR")"
[[ "$(json_field "$DLG_AFTER2" "['frozen']")" == "True" ]] \
  || fail "dialog should stay frozen without friendship: $DLG_AFTER2"

# Re-friend: dialog unfreezes.
FR2="$(call POST /api/friend-requests --jar "$ALICE_JAR" --expect 201 \
  --body "{\"username\":\"${BOB_USER}\"}")"
FR2_ID="$(json_field "$FR2" "['id']")"
call POST "/api/friend-requests/$FR2_ID/accept" --jar "$BOB_JAR" --expect 204 >/dev/null
DLG_FINAL="$(call GET "/api/dialogs/$DLG_ID" --jar "$ALICE_JAR")"
[[ "$(json_field "$DLG_FINAL" "['frozen']")" == "False" ]] \
  || fail "dialog should unfreeze after re-friend: $DLG_FINAL"
green "  unban alone keeps frozen; re-friend unfreezes"

# ───────────────────────── phase 6: attachments ─────────────────────────

step "Phase 6 — upload + link + download"
ROOM_VIEW="$(call GET "/api/rooms/$ROOM_ID" --jar "$ALICE_JAR")"
CONV_ID="$(json_field "$ROOM_VIEW" "['conversationId']")"
[[ -n "$CONV_ID" ]] || fail "room view should carry conversationId: $ROOM_VIEW"

TMPFILE="./smoke-upload-$$.png"
printf 'smoke-bytes' > "$TMPFILE"
ACSRF="$(csrf_of "$ALICE_JAR")"
UPLOAD_CODE="$(curl -sS -b "$ALICE_JAR" -H "X-XSRF-TOKEN: $ACSRF" \
  -F "file=@$TMPFILE;type=image/png" \
  -F "conversationId=$CONV_ID" \
  -F 'comment=smoke' \
  -o /tmp/smoke.body -w '%{http_code}' \
  "${BASE_URL}/api/attachments")"
assert_status 201 "$UPLOAD_CODE" "POST /api/attachments"
ATT_ID="$(json_field "$(cat /tmp/smoke.body)" "['id']")"
rm -f "$TMPFILE"
green "  upload ok: attachment=$ATT_ID"

SEND="$(call POST "/api/rooms/$ROOM_ID/messages" --jar "$ALICE_JAR" --expect 201 \
  --body "{\"text\":\"with attachment\",\"attachmentIds\":[\"$ATT_ID\"]}")"
ATT_ECHO="$(json_field "$SEND" "['attachments'][0]['id']")"
[[ "$ATT_ECHO" == "$ATT_ID" ]] || fail "MessageView should carry the attachment: $SEND"

BCSRF="$(csrf_of "$BOB_JAR")"
DL_CODE="$(curl -sS -b "$BOB_JAR" -H "X-XSRF-TOKEN: $BCSRF" \
  -o /tmp/smoke.body -w '%{http_code}' "${BASE_URL}/api/attachments/$ATT_ID")"
assert_status 200 "$DL_CODE" "GET /api/attachments/$ATT_ID"
[[ "$(cat /tmp/smoke.body)" == "smoke-bytes" ]] || fail "downloaded bytes mismatch"
green "  linked + bob downloaded bytes"

call POST "/api/rooms/$ROOM_ID/messages" --jar "$ALICE_JAR" --expect 400 \
  --body '{}' >/dev/null
green "  empty message rejected 400"

# ───────────────────────── phase 7: unread / watermarks ────────────────

step "Phase 7 — unread watermark bumps + mark-read"

# Fresh room so we have a known seq baseline.
UNREAD_ROOM="$(call POST /api/rooms --jar "$ALICE_JAR" --expect 201 \
  --body "{\"name\":\"unread-${TS}\",\"description\":\"\",\"visibility\":\"public\"}")"
UNREAD_ROOM_ID="$(json_field "$UNREAD_ROOM" "['id']")"
UNREAD_CONV_ID="$(json_field "$UNREAD_ROOM" "['conversationId']")"

call POST "/api/rooms/$UNREAD_ROOM_ID/join" --jar "$BOB_JAR" --expect 204 >/dev/null

# Baseline: Bob's unread snapshot shows 0 for this conversation (just joined).
BOB_UNREAD="$(call GET /api/unread --jar "$BOB_JAR")"
BASELINE="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
for v in d:
  if v['conversationId']=='$UNREAD_CONV_ID':
    print(v['count']); break
else:
  print(0)" <<<"$BOB_UNREAD")"
[[ "$BASELINE" == "0" ]] || fail "bob should start at 0 unread in fresh room: $BOB_UNREAD"

# Alice posts two messages; Bob's unread should become 2.
POST1="$(call POST "/api/rooms/$UNREAD_ROOM_ID/messages" --jar "$ALICE_JAR" --expect 201 \
  --body '{"text":"u1"}')"
SEQ_POST1="$(json_field "$POST1" "['seq']")"
call POST "/api/rooms/$UNREAD_ROOM_ID/messages" --jar "$ALICE_JAR" --expect 201 \
  --body '{"text":"u2"}' >/dev/null

BOB_UNREAD="$(call GET /api/unread --jar "$BOB_JAR")"
COUNT_BOB="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
for v in d:
  if v['conversationId']=='$UNREAD_CONV_ID':
    print(v['count']); break
else:
  print(0)" <<<"$BOB_UNREAD")"
[[ "$COUNT_BOB" == "2" ]] || fail "bob should have 2 unread after two posts: $BOB_UNREAD"

# Alice (the author) should NOT accumulate unread for her own posts.
ALICE_UNREAD="$(call GET /api/unread --jar "$ALICE_JAR")"
COUNT_ALICE="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
for v in d:
  if v['conversationId']=='$UNREAD_CONV_ID':
    print(v['count']); break
else:
  print(0)" <<<"$ALICE_UNREAD")"
[[ "$COUNT_ALICE" == "0" ]] || fail "author should not have self-unread: $ALICE_UNREAD"

# Bob marks read at the latest seq; count must drop to 0.
LATEST_SEQ="$((SEQ_POST1 + 1))"
call POST "/api/conversations/$UNREAD_CONV_ID/read" --jar "$BOB_JAR" --expect 204 \
  --body "{\"seq\":$LATEST_SEQ}" >/dev/null

BOB_UNREAD="$(call GET /api/unread --jar "$BOB_JAR")"
COUNT_AFTER="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
for v in d:
  if v['conversationId']=='$UNREAD_CONV_ID':
    print(v['count']); break
else:
  print(0)" <<<"$BOB_UNREAD")"
[[ "$COUNT_AFTER" == "0" ]] || fail "bob should be caught up after mark-read: $BOB_UNREAD"

# Non-participant cannot mark read (carol has no membership in this room).
CAROL_EMAIL="smoke-carol-${TS}@example.com"
CAROL_USER="smokec${TS}"
CAROL_JAR="$(mktemp)"
seed_csrf "$CAROL_JAR"
call POST /api/auth/register --jar "$CAROL_JAR" \
  --body "{\"email\":\"$CAROL_EMAIL\",\"username\":\"$CAROL_USER\",\"password\":\"$PASSWORD\"}" \
  >/dev/null
call POST "/api/conversations/$UNREAD_CONV_ID/read" --jar "$CAROL_JAR" --expect 403 \
  --body '{"seq":1}' >/dev/null
rm -f "$CAROL_JAR"

green "  unread: post→2, author→0, mark-read→0, non-participant→403"

# ───────────────────────── phase 9: demo seed + hardening ──────────────

step "Phase 9 — dev demo seed is populated and reachable"

SEED_LIST="$(curl -sS "${BASE_URL}/api/dev/demo-accounts")"
SEED_COUNT="$(python3 -c "import sys,json; print(len(json.loads(sys.stdin.read())))" <<<"$SEED_LIST")"
[[ "$SEED_COUNT" -ge 3 ]] || fail "demo-accounts endpoint should return ≥ 3 rows: $SEED_LIST"
echo "$SEED_LIST" | grep -q "alice@demo.test" || fail "seed list missing alice: $SEED_LIST"
echo "$SEED_LIST" | grep -q "bob@demo.test" || fail "seed list missing bob: $SEED_LIST"
echo "$SEED_LIST" | grep -q "carol@demo.test" || fail "seed list missing carol: $SEED_LIST"

SEED_JAR="$(mktemp)"
trap 'cleanup; rm -f "$SEED_JAR" 2>/dev/null || true' EXIT
seed_csrf "$SEED_JAR"
call POST /api/auth/login --jar "$SEED_JAR" \
     --body '{"email":"alice@demo.test","password":"DemoPass123!","rememberMe":false}' \
     >/dev/null

CATALOG="$(call GET /api/rooms --jar "$SEED_JAR")"
echo "$CATALOG" | grep -q "general-demo" \
  || fail "public catalog missing seeded general-demo: $CATALOG"

SEED_FRIENDS="$(call GET /api/friends --jar "$SEED_JAR")"
echo "$SEED_FRIENDS" | grep -q '"username":"bob"' \
  || fail "alice's friends should include bob: $SEED_FRIENDS"

SEED_DIALOGS="$(call GET /api/dialogs --jar "$SEED_JAR")"
SEED_DLG_ID="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
for v in d:
  if v['counterpartUsername']=='bob':
    print(v['id']); break
" <<<"$SEED_DIALOGS")"
[[ -n "$SEED_DLG_ID" ]] || fail "alice↔bob dialog not found: $SEED_DIALOGS"

SEED_DM_HIST="$(call GET "/api/dialogs/$SEED_DLG_ID/messages" --jar "$SEED_JAR")"
DM_COUNT="$(python3 -c "import sys,json
d=json.loads(sys.stdin.read())
print(len(d['items']))" <<<"$SEED_DM_HIST")"
[[ "$DM_COUNT" -ge 3 ]] || fail "seeded dialog should have ≥ 3 messages: $SEED_DM_HIST"

green "  demo seed ok — 3 accounts, general-demo public, alice↔bob dialog with $DM_COUNT msgs"

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
green "All docker smoke checks passed (phases 0, 1, 2, 3, 3.1, 4, 5, 6, 7, 9)."
