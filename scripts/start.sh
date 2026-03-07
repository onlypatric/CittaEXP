#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$' \n\t'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CITTAEXP_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd -- "${CITTAEXP_DIR}/.." && pwd)"
COMMON_LIB_DIR="${ROOT_DIR}/minecraft-common-lib"
CLASSIFICHE_DIR="${ROOT_DIR}/ClassificheExp"
SERVER_TEST_DIR="${ROOT_DIR}/SERVER-TEST"
EXTERNAL_LIBS_DIR="${ROOT_DIR}/EXTERNAL-LIBS"
RUNTIME_DIR="${SERVER_TEST_DIR}/runtime/current"
PLUGINS_DIR="${RUNTIME_DIR}/plugins"
CONFIG_DIR="${RUNTIME_DIR}/config"
TEMPLATES_DIR="${SERVER_TEST_DIR}/templates"

PAPER_VERSION="${PAPER_VERSION:-1.21.11}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:--Xms1G -Xmx2G}"
SERVER_PORT="${SERVER_PORT:-25565}"
AUTO_PORT_FALLBACK="${AUTO_PORT_FALLBACK:-1}"
GRADLE_OFFLINE="${GRADLE_OFFLINE:-1}"
BUILD_COMMON_LIB="${BUILD_COMMON_LIB:-1}"
BUILD_CITTAEXP="${BUILD_CITTAEXP:-1}"
COPY_CACHED_EXTERNAL="${COPY_CACHED_EXTERNAL:-0}"
DRY_RUN="${DRY_RUN:-0}"

log() {
  printf '[start.sh] %s\n' "$1"
}

fail() {
  printf '[start.sh] ERROR: %s\n' "$1" >&2
  exit 1
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

copy_jars_if_any() {
  local src_dir="$1"
  local label="$2"
  local count=0
  local jar
  [[ -d "$src_dir" ]] || return 0
  shopt -s nullglob
  for jar in "${src_dir}"/*.jar; do
    cp -f "$jar" "$PLUGINS_DIR/$(basename "$jar")"
    count=$((count + 1))
  done
  shopt -u nullglob
  if [[ "$count" -gt 0 ]]; then
    log "copied ${count} jar(s) from ${label}"
  fi
}

port_is_busy() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi
  return 1
}

choose_server_port() {
  local base_port="$1"
  local selected="$base_port"
  local probe=0
  local candidate=0

  if ! port_is_busy "$base_port"; then
    printf '%s\n' "$base_port"
    return 0
  fi

  if [[ "$AUTO_PORT_FALLBACK" != "1" ]]; then
    fail "porta ${base_port} gia in uso. Usa SERVER_PORT=<porta> oppure AUTO_PORT_FALLBACK=1"
  fi

  for probe in {1..30}; do
    candidate=$((base_port + probe))
    if ! port_is_busy "$candidate"; then
      selected="$candidate"
      printf '[start.sh] %s\n' "port ${base_port} busy, fallback to ${selected}" >&2
      printf '%s\n' "$selected"
      return 0
    fi
  done

  fail "nessuna porta libera trovata nel range ${base_port}..$((base_port + 30))"
}

set_server_property() {
  local file_path="$1"
  local key="$2"
  local value="$3"
  local tmp_file="${file_path}.tmp"

  if [[ -f "$file_path" ]]; then
    if grep -q "^${key}=" "$file_path"; then
      awk -v k="$key" -v v="$value" '
        BEGIN { done=0 }
        {
          if ($0 ~ ("^" k "=") && done == 0) {
            print k "=" v
            done=1
          } else {
            print $0
          }
        }
        END {
          if (done == 0) {
            print k "=" v
          }
        }
      ' "$file_path" > "$tmp_file"
      mv "$tmp_file" "$file_path"
      return 0
    fi
  fi

  printf '%s=%s\n' "$key" "$value" >> "$file_path"
}

ensure_file_from_template_if_missing() {
  local template_path="$1"
  local target_path="$2"
  if [[ -f "$target_path" ]]; then
    return 0
  fi
  if [[ -f "$template_path" ]]; then
    cp "$template_path" "$target_path"
  fi
}

ensure_protocol_lib_for_itemsadder() {
  local has_itemsadder=0
  local has_protocol=0
  local candidate=""
  local ia_jar

  shopt -s nullglob nocaseglob
  for ia_jar in "${PLUGINS_DIR}"/*ItemsAdder*.jar; do
    [[ -f "$ia_jar" ]] || continue
    has_itemsadder=1
    break
  done
  for ia_jar in "${PLUGINS_DIR}"/*ProtocolLib*.jar "${PLUGINS_DIR}"/protocollib.jar; do
    [[ -f "$ia_jar" ]] || continue
    has_protocol=1
    break
  done
  shopt -u nullglob nocaseglob

  if [[ "$has_itemsadder" -eq 0 || "$has_protocol" -eq 1 ]]; then
    return 0
  fi

  if [[ -f "${EXTERNAL_LIBS_DIR}/ProtocolLib.jar" ]]; then
    candidate="${EXTERNAL_LIBS_DIR}/ProtocolLib.jar"
  elif [[ -f "${EXTERNAL_LIBS_DIR}/protocollib.jar" ]]; then
    candidate="${EXTERNAL_LIBS_DIR}/protocollib.jar"
  elif [[ -f "${SERVER_TEST_DIR}/cache/external/protocollib.jar" ]]; then
    candidate="${SERVER_TEST_DIR}/cache/external/protocollib.jar"
  fi

  if [[ -n "$candidate" ]]; then
    cp -f "$candidate" "${PLUGINS_DIR}/ProtocolLib.jar"
    log "ItemsAdder dependency resolved: copied ProtocolLib from ${candidate#$ROOT_DIR/}"
  else
    fail "ItemsAdder rilevato ma ProtocolLib non trovato in locale (EXTERNAL-LIBS o SERVER-TEST/cache/external). Installa ProtocolLib e rilancia."
  fi
}

resolve_paper_jar() {
  local candidates=(
    "${SERVER_TEST_DIR}/cache/paper/paper-${PAPER_VERSION}.jar"
    "${SERVER_TEST_DIR}/runtime/current/paper.jar"
    "${ROOT_DIR}/minecraft-common-lib/bin/smoke-1.0.0-ga/paper-${PAPER_VERSION}-69.jar"
    "${ROOT_DIR}/minecraft-common-lib/bin/smoke-1.0.0-rc.1/paper-${PAPER_VERSION}-69.jar"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

build_common_lib() {
  local gradle_args=(--no-daemon)
  if [[ "$GRADLE_OFFLINE" == "1" ]]; then
    gradle_args+=(--offline)
  fi
  log "building minecraft-common-lib adapters"
  (
    cd "$COMMON_LIB_DIR"
    ./gradlew "${gradle_args[@]}" :jar :adapter-itemsadder:jar :adapter-huskclaims:jar
  )
}

build_cittaexp() {
  local common_lib_jar itemsadder_jar huskclaims_adapter_jar huskclaims_api_jar
  common_lib_jar="$(pick_latest_jar "${COMMON_LIB_DIR}/build/libs" "minecraft-common-lib-*.jar")"
  itemsadder_jar="$(pick_latest_jar "${COMMON_LIB_DIR}/adapter-itemsadder/build/libs" "adapter-itemsadder-*.jar")"
  huskclaims_adapter_jar="$(pick_latest_jar "${COMMON_LIB_DIR}/adapter-huskclaims/build/libs" "adapter-huskclaims-*.jar")"
  huskclaims_api_jar="$(pick_latest_jar "${EXTERNAL_LIBS_DIR}" "HuskClaims*.jar")"

  [[ -n "${common_lib_jar:-}" ]] || fail "minecraft-common-lib jar not found in ${COMMON_LIB_DIR}/build/libs"
  [[ -n "${itemsadder_jar:-}" ]] || fail "adapter-itemsadder jar not found in ${COMMON_LIB_DIR}/adapter-itemsadder/build/libs"
  [[ -n "${huskclaims_adapter_jar:-}" ]] || fail "adapter-huskclaims jar not found in ${COMMON_LIB_DIR}/adapter-huskclaims/build/libs"
  [[ -n "${huskclaims_api_jar:-}" ]] || fail "HuskClaims jar non trovato in ${EXTERNAL_LIBS_DIR}"

  local gradle_args=(--no-daemon clean shadowJar)
  if [[ "$GRADLE_OFFLINE" == "1" ]]; then
    gradle_args=(--no-daemon --offline clean shadowJar)
  fi

  log "building CittaEXP shadow jar"
  (
    cd "$CITTAEXP_DIR"
    ./gradlew "${gradle_args[@]}" \
      -PcommonLibJar="$common_lib_jar" \
      -PitemsAdderAdapterJar="$itemsadder_jar" \
      -PhuskClaimsAdapterJar="$huskclaims_adapter_jar" \
      -PhuskClaimsApiJar="$huskclaims_api_jar"
  )
}

prepare_runtime_offline() {
  local paper_jar
  local selected_port
  local properties_file

  paper_jar="$(resolve_paper_jar)" || fail "paper jar non trovato in cache locale; nessun download automatico previsto"
  selected_port="$(choose_server_port "$SERVER_PORT")"
  properties_file="${RUNTIME_DIR}/server.properties"

  mkdir -p "$PLUGINS_DIR" "$CONFIG_DIR"

  cp "$paper_jar" "$RUNTIME_DIR/paper.jar"

  ensure_file_from_template_if_missing "${TEMPLATES_DIR}/eula.txt" "${RUNTIME_DIR}/eula.txt"
  if [[ ! -f "${RUNTIME_DIR}/eula.txt" ]]; then
    printf 'eula=true\n' > "${RUNTIME_DIR}/eula.txt"
  fi

  ensure_file_from_template_if_missing "${TEMPLATES_DIR}/server.properties" "${RUNTIME_DIR}/server.properties"
  ensure_file_from_template_if_missing "${TEMPLATES_DIR}/bukkit.yml" "${RUNTIME_DIR}/bukkit.yml"
  ensure_file_from_template_if_missing "${TEMPLATES_DIR}/spigot.yml" "${RUNTIME_DIR}/spigot.yml"

  if [[ ! -f "$properties_file" ]]; then
    : > "$properties_file"
  fi
  set_server_property "$properties_file" "server-port" "$selected_port"

  ensure_file_from_template_if_missing "${TEMPLATES_DIR}/paper-global.yml" "${CONFIG_DIR}/paper-global.yml"
  ensure_file_from_template_if_missing "${TEMPLATES_DIR}/paper-world-defaults.yml" "${CONFIG_DIR}/paper-world-defaults.yml"

  log "runtime prepared offline in ${RUNTIME_DIR} (port=${selected_port})"
}

install_plugins() {
  local cittaexp_jar
  local classifiche_jar
  cittaexp_jar="$(pick_latest_jar "${CITTAEXP_DIR}/build/libs" "CittaEXP-*.jar")"
  [[ -n "${cittaexp_jar:-}" ]] || fail "CittaEXP jar non trovato in ${CITTAEXP_DIR}/build/libs"

  cp -f "$cittaexp_jar" "${PLUGINS_DIR}/CittaEXP.jar"
  log "installed plugin: ${PLUGINS_DIR}/CittaEXP.jar"

  classifiche_jar="$(pick_latest_jar "${CLASSIFICHE_DIR}/build/libs" "ClassificheExp-*.jar" || true)"
  if [[ -n "${classifiche_jar:-}" ]]; then
    cp -f "$classifiche_jar" "${PLUGINS_DIR}/ClassificheEXP.jar"
    log "installed dependency plugin: ${PLUGINS_DIR}/ClassificheEXP.jar"
  fi

  copy_jars_if_any "$EXTERNAL_LIBS_DIR" "EXTERNAL-LIBS"
  if [[ "$COPY_CACHED_EXTERNAL" == "1" ]]; then
    copy_jars_if_any "${SERVER_TEST_DIR}/cache/external" "SERVER-TEST/cache/external"
  fi

  if [[ -f "${PLUGINS_DIR}/ClassificheEXP.jar" && -f "${PLUGINS_DIR}/ClassificheExp.jar" ]]; then
    rm -f "${PLUGINS_DIR}/ClassificheExp.jar"
    log "removed duplicate plugin jar: ${PLUGINS_DIR}/ClassificheExp.jar"
  fi

  ensure_protocol_lib_for_itemsadder
}

main() {
  [[ -x "${CITTAEXP_DIR}/gradlew" ]] || fail "gradlew non trovato in ${CITTAEXP_DIR}"
  [[ -x "${COMMON_LIB_DIR}/gradlew" ]] || fail "gradlew non trovato in ${COMMON_LIB_DIR}"

  if [[ "$BUILD_COMMON_LIB" == "1" ]]; then
    build_common_lib
  else
    log "skip common-lib build (BUILD_COMMON_LIB=0)"
  fi

  if [[ "$BUILD_CITTAEXP" == "1" ]]; then
    build_cittaexp
  else
    log "skip CittaEXP build (BUILD_CITTAEXP=0)"
  fi

  prepare_runtime_offline
  install_plugins

  log "plugins installed:"
  ls -1 "$PLUGINS_DIR" | sed 's/^/[start.sh] - /'

  if [[ "$DRY_RUN" == "1" ]]; then
    log "dry-run enabled, server start skipped"
    exit 0
  fi

  log "starting Paper server"
  (
    cd "$RUNTIME_DIR"
    local -a java_args=()
    if [[ -n "${JAVA_OPTS}" ]]; then
      read -r -a java_args <<< "$JAVA_OPTS"
    fi
    exec "$JAVA_BIN" "${java_args[@]}" -jar paper.jar --nogui
  )
}

main "$@"
