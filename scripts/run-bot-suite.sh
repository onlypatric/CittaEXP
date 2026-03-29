#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$' \n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CITTAEXP_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd -- "${CITTAEXP_DIR}/.." && pwd)"
SERVER_TEST_DIR="${ROOT_DIR}/SERVER-TEST"
REPORT_BASE="${CITTAEXP_DIR}/.reports/bot-m6"

BOT_SUITE="${BOT_SUITE:-fast}"          # fast|full-positive|full-negative|full
BOT_DB_MODE="${BOT_DB_MODE:-sqlite}"    # sqlite|mysql|both

log() { printf '[bot-suite] %s\n' "$1"; }
fail() { printf '[bot-suite] ERROR: %s\n' "$1" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Comando richiesto non trovato: $1"
}

require_cmd bash
require_cmd python3

mkdir -p "$REPORT_BASE"
REPORT_FILE="${REPORT_BASE}/${BOT_SUITE}.json"

run_gradle_gate() {
  local -a tasks=("compileJava" "testFast")
  if [[ "$BOT_SUITE" == "full" ]]; then
    tasks+=("testFull")
  fi
  log "Gradle gate: ${tasks[*]}"
  (cd "$CITTAEXP_DIR" && ./gradlew "${tasks[@]}" -x test)
}

latest_report_dir() {
  python3 - <<'PY' "$SERVER_TEST_DIR"
import glob, os, sys
base = os.path.join(sys.argv[1], "reports")
dirs = [d for d in glob.glob(os.path.join(base, "*")) if os.path.isdir(d)]
print(max(dirs, key=os.path.getmtime) if dirs else "")
PY
}

run_scenario() {
  local scenario="$1"
  local before after report_dir report_json server_log status
  before="$(latest_report_dir)"
  log "Eseguo scenario SERVER-TEST: $scenario"
  (cd "$SERVER_TEST_DIR" && ./scripts/run-smoke.sh --scenario "$scenario")
  after="$(latest_report_dir)"
  if [[ -z "$after" || "$after" == "$before" ]]; then
    fail "Impossibile determinare report dir per scenario $scenario"
  fi
  report_dir="$after"
  report_json="${report_dir}/report.json"
  server_log="${report_dir}/server.log"
  [[ -f "$report_json" ]] || fail "report.json mancante per scenario $scenario"
  [[ -f "$server_log" ]] || fail "server.log mancante per scenario $scenario"

  status="$(python3 - <<'PY' "$report_json"
import json,sys
with open(sys.argv[1], encoding='utf-8') as fh:
    data = json.load(fh)
print(data.get("status", "UNKNOWN"))
PY
)"

  local hard_hits
  hard_hits="$(rg -n "Command exception:|CommandException|Unhandled exception|NoSuchMethodError|NoClassDefFoundError|Missing message key|missing message key" "$server_log" -S || true)"

  local result="PASS"
  local reason="ok"
  if [[ "$status" == "FAIL" || "$status" == "ERROR" ]]; then
    result="FAIL"
    reason="scenario-status-${status}"
  fi
  if [[ -n "$hard_hits" ]]; then
    result="FAIL"
    reason="hard-exception-in-log"
  fi

  python3 - <<'PY' "$REPORT_FILE" "$scenario" "$result" "$reason" "$report_dir"
import datetime
import json
import os
import sys
path, scenario, result, reason, report_dir = sys.argv[1:6]
payload = {
    "suite": os.environ.get("BOT_SUITE", "fast"),
    "generated_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "scenarios": []
}
if os.path.exists(path):
    with open(path, encoding="utf-8") as fh:
        payload = json.load(fh)
payload.setdefault("scenarios", []).append({
    "scenario": scenario,
    "result": result,
    "reason": reason,
    "report_dir": report_dir
})
with open(path, "w", encoding="utf-8") as fh:
    json.dump(payload, fh, indent=2, ensure_ascii=False)
PY

  [[ "$result" == "PASS" ]] || fail "Scenario $scenario fallito: $reason (vedi $report_dir)"
}

check_sqlite() {
  local db_file="${ROOT_DIR}/SERVER-TEST/runtime/current/plugins/CittaEXP/challenges.db"
  if [[ ! -f "$db_file" ]]; then
    log "SQLite DB non trovato ($db_file), skip check"
    return 0
  fi
  if ! command -v sqlite3 >/dev/null 2>&1; then
    log "sqlite3 non presente, skip check SQLite"
    return 0
  fi
  local rows
  rows="$(sqlite3 "$db_file" "select count(*) from challenge_instances;" || echo "0")"
  log "DB sqlite challenge_instances=$rows"
}

check_mysql() {
  local host="${MYSQL_HOST:-127.0.0.1}"
  local port="${MYSQL_PORT:-3306}"
  local user="${MYSQL_USER:-root}"
  local pass="${MYSQL_PASSWORD:-}"
  local db="${MYSQL_DATABASE:-cittaexp}"
  local rows=""
  if command -v mysql >/dev/null 2>&1; then
    rows="$(mysql -N -h "$host" -P "$port" -u "$user" "-p$pass" "$db" -e "select count(*) from town_progression;" 2>/dev/null || echo "")"
  elif [[ -n "${MYSQL_DOCKER_CONTAINER:-}" ]] && command -v docker >/dev/null 2>&1; then
    rows="$(docker exec "$MYSQL_DOCKER_CONTAINER" mysql -N -h 127.0.0.1 -P 3306 -u"$user" "-p$pass" "$db" -e "select count(*) from town_progression;" 2>/dev/null || echo "")"
  else
    log "mysql client non presente e nessun MYSQL_DOCKER_CONTAINER configurato, skip check MySQL"
    return 0
  fi
  if [[ -z "$rows" ]]; then
    log "Query MySQL non eseguita (connessione/schema non disponibile), skip"
    return 0
  fi
  log "DB mysql town_progression=$rows"
}

run_profile() {
  case "$BOT_SUITE" in
    fast)
      run_scenario "fallback-no-external"
      ;;
    full-positive)
      run_scenario "wave-full-best-effort"
      ;;
    full-negative)
      run_scenario "fallback-no-external"
      ;;
    full)
      run_scenario "wave-full-best-effort"
      run_scenario "fallback-no-external"
      ;;
    *)
      fail "BOT_SUITE non valido: $BOT_SUITE"
      ;;
  esac
}

run_db_checks() {
  case "$BOT_DB_MODE" in
    sqlite) check_sqlite ;;
    mysql) check_mysql ;;
    both) check_sqlite; check_mysql ;;
    *) fail "BOT_DB_MODE non valido: $BOT_DB_MODE" ;;
  esac
}

rm -f "$REPORT_FILE"
run_gradle_gate
run_profile
run_db_checks

python3 - <<'PY' "$REPORT_FILE"
import json, sys
with open(sys.argv[1], encoding="utf-8") as fh:
    payload = json.load(fh)
failed = [s for s in payload.get("scenarios", []) if s.get("result") != "PASS"]
payload["summary"] = {
    "total": len(payload.get("scenarios", [])),
    "failed": len(failed),
    "status": "PASS" if not failed else "FAIL"
}
with open(sys.argv[1], "w", encoding="utf-8") as fh:
    json.dump(payload, fh, indent=2, ensure_ascii=False)
print(payload["summary"]["status"])
PY

log "Report: $REPORT_FILE"
