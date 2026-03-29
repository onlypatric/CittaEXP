#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$' \n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CITTAEXP_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd -- "${CITTAEXP_DIR}/.." && pwd)"
HUSKTOWNS_DIR="${ROOT_DIR}/HuskTowns"
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
ENABLE_ITEMSADDER="${ENABLE_ITEMSADDER:-1}"
ENABLE_VAULT="${ENABLE_VAULT:-1}"
ENABLE_ESSENTIALSX="${ENABLE_ESSENTIALSX:-1}"
ENABLE_HUSKCLAIMS="${ENABLE_HUSKCLAIMS:-1}"
ENABLE_PROTOCOLLIB="${ENABLE_PROTOCOLLIB:-1}"
BUNDLE_OUTPUT_NAME="${BUNDLE_OUTPUT_NAME:-cittaexp-runtime-bundle.zip}"

log() { printf '[start.sh] %s\n' "$1"; }
fail() { printf '[start.sh] ERROR: %s\n' "$1" >&2; exit 1; }

java_major_version() {
  local output major
  output="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
  major="$(printf '%s' "$output" | sed -E 's/.*version "([0-9]+).*/\1/')"
  if [[ -z "$major" ]]; then
    printf '0\n'
    return
  fi
  printf '%s\n' "$major"
}

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

build_husktowns() {
  if [[ "$SKIP_BUILD" == "1" ]]; then
    log "SKIP_BUILD=1, salto build HuskTowns"
    return
  fi
  local gradle_flags=()
  if [[ "$GRADLE_OFFLINE" == "1" ]]; then
    gradle_flags+=(--offline)
  fi
  (cd "$HUSKTOWNS_DIR" && ./gradlew "${gradle_flags[@]}" :paper:shadowJar)
}

copy_plugin_if_found() {
  local pattern="$1"
  local target_name="$2"
  local required="${3:-0}"
  local source
  source="$(pick_latest_jar "$EXTERNAL_LIBS_DIR" "$pattern" || true)"
  if [[ -n "$source" ]]; then
    cp -f "$source" "$PLUGINS_DIR/$target_name"
    log "plugin opzionale installato: $target_name ($(basename "$source"))"
    return 0
  fi
  if [[ "$required" == "1" ]]; then
    fail "Plugin opzionale abilitato ma non trovato: $target_name (pattern: $pattern)"
  fi
  return 1
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

run_prepare_itemsadder_assets() {
  if [[ "$ENABLE_ITEMSADDER" != "1" ]]; then
    return
  fi
  local itemsadder_data_dir="$PLUGINS_DIR/ItemsAdder"
  mkdir -p "$itemsadder_data_dir"
  log "preparo asset/config ItemsAdder per namespace cittaexp_gui"
  python3 "$SCRIPT_DIR/prepare_itemsadder_hub_gui.py" --runtime-itemsadder-dir "$itemsadder_data_dir"
}

create_runtime_bundle() {
  local dist_dir="$CITTAEXP_DIR/build/distributions"
  local bundle_path="$dist_dir/$BUNDLE_OUTPUT_NAME"
  mkdir -p "$dist_dir"
  python3 - "$PLUGINS_DIR" "$bundle_path" <<'PYBUNDLE'
from pathlib import Path
import sys
import zipfile

plugins_dir = Path(sys.argv[1])
bundle_path = Path(sys.argv[2])
entries = []
for jar_name in ('CittaEXP.jar', 'HuskTowns.jar'):
    jar_path = plugins_dir / jar_name
    if not jar_path.exists():
        raise SystemExit(f'missing runtime jar: {jar_path}')
    entries.append((jar_path, Path('plugins') / jar_name))

itemsadder_ns = plugins_dir / 'ItemsAdder' / 'contents' / 'cittaexp_gui'
if itemsadder_ns.exists():
    for path in sorted(itemsadder_ns.rglob('*')):
        if not path.is_file():
            continue
        if path.name == '.DS_Store' or '.bak' in path.name:
            continue
        entries.append((path, Path('plugins') / 'ItemsAdder' / 'contents' / 'cittaexp_gui' / path.relative_to(itemsadder_ns)))

with zipfile.ZipFile(bundle_path, 'w', compression=zipfile.ZIP_DEFLATED) as zf:
    for src, arc in entries:
        zf.write(src, arc.as_posix())
print(bundle_path)
PYBUNDLE
  log "bundle runtime creato: $bundle_path"
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
  husktowns_jar="$(pick_latest_jar "$HUSKTOWNS_DIR/target" "HuskTowns-Paper-*.jar" || true)"
  [[ -n "$husktowns_jar" ]] || husktowns_jar="$(pick_latest_jar "$EXTERNAL_LIBS_DIR" "HuskTowns*.jar" || true)"
  [[ -n "$husktowns_jar" ]] || fail "HuskTowns jar non trovato ne in HuskTowns/target ne in EXTERNAL-LIBS"

  local plugin_jar
  plugin_jar="$(pick_latest_jar "$CITTAEXP_DIR/build/libs" "CittaEXP-*.jar" || true)"
  [[ -n "$plugin_jar" ]] || fail "Jar CittaEXP non trovato in build/libs"

  rm -f "$PLUGINS_DIR"/CittaEXP*.jar \
        "$PLUGINS_DIR"/HuskTowns*.jar \
        "$PLUGINS_DIR"/Vault*.jar \
        "$PLUGINS_DIR"/EssentialsX*.jar \
        "$PLUGINS_DIR"/HuskClaims*.jar \
        "$PLUGINS_DIR"/ProtocolLib*.jar \
        "$PLUGINS_DIR"/ItemsAdder*.jar
  cp -f "$plugin_jar" "$PLUGINS_DIR/CittaEXP.jar"
  cp -f "$husktowns_jar" "$PLUGINS_DIR/HuskTowns.jar"

  verify_husktowns_libs_bundle
  cp -f "$EXTERNAL_LIBS_DIR"/husktowns-libs/*.jar "$LIBRARIES_DIR"/

  if [[ "$INCLUDE_OPTIONAL_DEPS" == "1" ]]; then
    if [[ "$ENABLE_VAULT" == "1" ]]; then
      copy_plugin_if_found "Vault*.jar" "Vault.jar" 1
    fi
    if [[ "$ENABLE_ESSENTIALSX" == "1" ]]; then
      copy_plugin_if_found "EssentialsX*.jar" "EssentialsX.jar" 1
    fi
    if [[ "$ENABLE_HUSKCLAIMS" == "1" ]]; then
      copy_plugin_if_found "HuskClaims*.jar" "HuskClaims.jar" 1
    fi
    if [[ "$ENABLE_PROTOCOLLIB" == "1" ]]; then
      # Priorita al jar richiesto; fallback al pattern generico.
      if ! copy_plugin_if_found "ProtocolLib.jar" "ProtocolLib.jar" 0; then
        copy_plugin_if_found "ProtocolLib*.jar" "ProtocolLib.jar" 1
      fi
    fi
    if [[ "$ENABLE_ITEMSADDER" == "1" ]]; then
      # Priorita al jar pin richiesto; fallback al pattern generico.
      if ! copy_plugin_if_found "ItemsAdder_4.0.16-beta-11.jar" "ItemsAdder.jar" 0; then
        copy_plugin_if_found "ItemsAdder*.jar" "ItemsAdder.jar" 1
      fi
    fi
  fi

  # Preserva sempre la cartella dati di ItemsAdder (resource pack, cache, assets).
  # Non viene mai rimossa o sovrascritta da questo script.
  if [[ -d "$PLUGINS_DIR/ItemsAdder" ]]; then
    log "ItemsAdder data folder preservata: $PLUGINS_DIR/ItemsAdder"
  fi

  if [[ ! -f "$RUNTIME_DIR/eula.txt" ]]; then
    printf 'eula=true\n' > "$RUNTIME_DIR/eula.txt"
  fi

  log "runtime summary:"
  log " - paper source: $(basename "$paper_jar")"
  log " - cittaexp source: $(basename "$plugin_jar")"
  log " - husktowns source: $(basename "$husktowns_jar")"
  log " - optional: vault=${ENABLE_VAULT}, essentialsx=${ENABLE_ESSENTIALSX}, huskclaims=${ENABLE_HUSKCLAIMS}, protocollib=${ENABLE_PROTOCOLLIB}, itemsadder=${ENABLE_ITEMSADDER}"
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

java_major="$(java_major_version)"
if [[ "$java_major" -lt 21 ]]; then
  fail "Java 21+ richiesto. Rilevato major=${java_major} (JAVA_BIN=$JAVA_BIN)"
fi
build_plugin
build_husktowns
prepare_runtime
run_prepare_itemsadder_assets
create_runtime_bundle
if [[ "$PREPARE_ONLY" == "1" ]]; then
  if [[ -f "$RUNTIME_DIR/paper.jar" && -f "$PLUGINS_DIR/CittaEXP.jar" && -f "$PLUGINS_DIR/HuskTowns.jar" ]]; then
    log "PREPARE_ONLY=1, readiness=ready (server non avviato)"
  else
    log "PREPARE_ONLY=1, readiness=not-ready (artifact mancanti)"
    exit 2
  fi
  exit 0
fi
start_server
