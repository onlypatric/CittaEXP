#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$' \n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CITTAEXP_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd -- "${CITTAEXP_DIR}/.." && pwd)"
SERVER_TEST_DIR="${ROOT_DIR}/SERVER-TEST"
EXTERNAL_LIBS_DIR="${ROOT_DIR}/EXTERNAL-LIBS"
RUNTIME_DIR="${SERVER_TEST_DIR}/runtime/current"
PLUGINS_DIR="${RUNTIME_DIR}/plugins"
LIBRARIES_DIR="${RUNTIME_DIR}/libraries"

JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:--Xms1G -Xmx2G}"
PAPER_VERSION="${PAPER_VERSION:-1.21.11}"
GRADLE_OFFLINE="${GRADLE_OFFLINE:-1}"
SKIP_BUILD="${SKIP_BUILD:-0}"
PREPARE_ONLY="${PREPARE_ONLY:-0}"
INCLUDE_OPTIONAL_DEPS="${INCLUDE_OPTIONAL_DEPS:-1}"
ENABLE_ITEMSADDER="${ENABLE_ITEMSADDER:-0}"
ENABLE_CLASSIFICHEEXP="${ENABLE_CLASSIFICHEEXP:-1}"

log() { printf '[start.sh] %s\n' "$1"; }
fail() { printf '[start.sh] ERROR: %s\n' "$1" >&2; exit 1; }

pick_latest_jar() {
  local base_dir="$1"
  local pattern="$2"
  local best=""
  local jar
  shopt -s nullglob
  for jar in "${base_dir}"/${pattern}; do
    case "$jar" in
      *-sources.jar|*-javadoc.jar|*-plain.jar) continue ;;
    esac
    if [[ -z "$best" || "$jar" -nt "$best" ]]; then
      best="$jar"
    fi
  done
  shopt -u nullglob
  [[ -n "$best" ]] && printf '%s\n' "$best"
}

build_plugin() {
  if [[ "$SKIP_BUILD" == "1" ]]; then
    log "SKIP_BUILD=1, salto build CittaEXP"
    return
  fi
  local gradle_flags=()
  if [[ "$GRADLE_OFFLINE" == "1" ]]; then
    gradle_flags+=(--offline)
  fi
  (cd "$CITTAEXP_DIR" && ./gradlew "${gradle_flags[@]}" clean shadowJar)
}

copy_plugin_if_found() {
  local pattern="$1"
  local target_name="$2"
  local source
  source="$(pick_latest_jar "$EXTERNAL_LIBS_DIR" "$pattern" || true)"
  if [[ -n "$source" ]]; then
    cp -f "$source" "$PLUGINS_DIR/$target_name"
    log "plugin opzionale installato: $target_name ($(basename "$source"))"
  fi
}

verify_husktowns_libs_bundle() {
  local libs_dir="$EXTERNAL_LIBS_DIR/husktowns-libs"
  [[ -d "$libs_dir" ]] || fail "Cartella husktowns-libs non trovata in EXTERNAL-LIBS"
  local required=(
    "jedis-*.jar"
    "mysql-connector-j-*.jar"
    "mariadb-java-client-*.jar"
    "sqlite-jdbc-*.jar"
  )
  local p found
  for p in "${required[@]}"; do
    found="$(pick_latest_jar "$libs_dir" "$p" || true)"
    [[ -n "$found" ]] || fail "Libreria HuskTowns mancante in husktowns-libs: $p"
  done
}

prepare_runtime() {
  mkdir -p "$RUNTIME_DIR" "$PLUGINS_DIR" "$LIBRARIES_DIR"

  local paper_jar
  paper_jar="$(pick_latest_jar "$EXTERNAL_LIBS_DIR" "paper-${PAPER_VERSION}*.jar" || true)"
  [[ -n "$paper_jar" ]] || paper_jar="$(pick_latest_jar "$EXTERNAL_LIBS_DIR" "paper*.jar" || true)"
  if [[ -z "$paper_jar" && -f "$RUNTIME_DIR/paper.jar" ]]; then
    paper_jar="$RUNTIME_DIR/paper.jar"
  fi
  [[ -n "$paper_jar" ]] || fail "Paper jar non trovato in EXTERNAL-LIBS"
  if [[ "$(cd -- "$(dirname -- "$paper_jar")" && pwd)/$(basename -- "$paper_jar")" != "$RUNTIME_DIR/paper.jar" ]]; then
    cp -f "$paper_jar" "$RUNTIME_DIR/paper.jar"
  fi

  local husktowns_jar
  husktowns_jar="$(pick_latest_jar "$EXTERNAL_LIBS_DIR" "HuskTowns*.jar" || true)"
  [[ -n "$husktowns_jar" ]] || fail "HuskTowns jar non trovato in EXTERNAL-LIBS"

  local plugin_jar
  plugin_jar="$(pick_latest_jar "$CITTAEXP_DIR/build/libs" "CittaEXP-*.jar" || true)"
  [[ -n "$plugin_jar" ]] || fail "Jar CittaEXP non trovato in build/libs"

  rm -f "$PLUGINS_DIR"/CittaEXP*.jar \
        "$PLUGINS_DIR"/HuskTowns*.jar \
        "$PLUGINS_DIR"/ClassificheExp*.jar \
        "$PLUGINS_DIR"/Vault*.jar \
        "$PLUGINS_DIR"/EssentialsX*.jar \
        "$PLUGINS_DIR"/HuskClaims*.jar \
        "$PLUGINS_DIR"/ItemsAdder*.jar
  cp -f "$plugin_jar" "$PLUGINS_DIR/CittaEXP.jar"
  cp -f "$husktowns_jar" "$PLUGINS_DIR/HuskTowns.jar"

  verify_husktowns_libs_bundle
  cp -f "$EXTERNAL_LIBS_DIR"/husktowns-libs/*.jar "$LIBRARIES_DIR"/

  if [[ "$INCLUDE_OPTIONAL_DEPS" == "1" ]]; then
    if [[ "$ENABLE_CLASSIFICHEEXP" == "1" ]]; then
      copy_plugin_if_found "ClassificheExp*.jar" "ClassificheExp.jar"
    fi
    copy_plugin_if_found "Vault*.jar" "Vault.jar"
    copy_plugin_if_found "EssentialsX*.jar" "EssentialsX.jar"
    copy_plugin_if_found "HuskClaims*.jar" "HuskClaims.jar"
    if [[ "$ENABLE_ITEMSADDER" == "1" ]]; then
      copy_plugin_if_found "ItemsAdder*.jar" "ItemsAdder.jar"
    fi
  fi

  if [[ ! -f "$RUNTIME_DIR/eula.txt" ]]; then
    printf 'eula=true\n' > "$RUNTIME_DIR/eula.txt"
  fi
}

start_server() {
  cd "$RUNTIME_DIR"
  local -a java_opts
  read -r -a java_opts <<< "$JAVA_OPTS"
  log "runtime prepared offline in $RUNTIME_DIR"
  log "plugins installed:"
  ls -1 "$PLUGINS_DIR" | sed 's/^/[start.sh] - /'
  log "starting Paper server"
  exec "$JAVA_BIN" "${java_opts[@]}" -jar "$RUNTIME_DIR/paper.jar" nogui
}

build_plugin
prepare_runtime
if [[ "$PREPARE_ONLY" == "1" ]]; then
  log "PREPARE_ONLY=1, preparazione completata (server non avviato)"
  exit 0
fi
start_server
