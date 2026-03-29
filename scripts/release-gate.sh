#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$' \n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CITTAEXP_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

log() { printf '[release-gate] %s\n' "$1"; }

log "Running strict release gate (compile + fast/full tests + bot matrix)"
(cd "$CITTAEXP_DIR" && ./gradlew compileJava -x test)
(cd "$CITTAEXP_DIR" && ./gradlew testFast)
(cd "$CITTAEXP_DIR" && ./gradlew testFull)

BOT_SUITE=fast BOT_DB_MODE=sqlite "$SCRIPT_DIR/run-bot-suite.sh"
BOT_SUITE=full-positive BOT_DB_MODE=sqlite "$SCRIPT_DIR/run-bot-suite.sh"
BOT_SUITE=full-negative BOT_DB_MODE=sqlite "$SCRIPT_DIR/run-bot-suite.sh"
BOT_SUITE=full BOT_DB_MODE=both "$SCRIPT_DIR/run-bot-suite.sh"

log "Release gate completed successfully."
