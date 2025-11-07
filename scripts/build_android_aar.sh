#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${ROOT_DIR}/android"
NATIVE_DIR="${ANDROID_DIR}/native"
EXAMPLE_ANDROID_DIR="${ROOT_DIR}/example/android"
OUTPUT_LIBS_DIR="${ANDROID_DIR}/libs"
AAR_OUTPUT_NAME="stone_deep_tech_native-release.aar"
JAR_OUTPUT_NAME="stone_deep_tech-native.jar"

log() {
  echo "[build_android_aar] $1"
}

ensure_env_var() {
  local var_name="$1"
  local var_value="$2"
  local fallback_cmd="$3"

  if [[ -z "${!var_name:-}" ]]; then
    if [[ -n "$var_value" ]]; then
      export "${var_name}"="$var_value"
    elif [[ -n "$fallback_cmd" ]]; then
      local detected
      if detected=$(eval "$fallback_cmd" 2>/dev/null); then
        export "${var_name}"="$detected"
      fi
    fi
  fi
  if [[ -z "${!var_name:-}" ]]; then
    log "Variável ${var_name} não encontrada. Configure-a antes de continuar."
    exit 1
  fi
}

detect_flutter() {
  if [[ -n "${FLUTTER_SDK:-}" ]]; then
    return
  fi

  if command -v flutter >/dev/null 2>&1; then
    local flutter_bin
    flutter_bin="$(command -v flutter)"
    FLUTTER_SDK="$(cd "$(dirname "$flutter_bin")/.." && pwd)"
    export FLUTTER_SDK
  fi
}

detect_java() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    if [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
      log "JAVA_HOME atual (${JAVA_HOME}) é inválido. Tentando detectar automaticamente."
      unset JAVA_HOME
    else
      return
    fi
  fi

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    local detected
    detected=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null)
    if [[ -n "$detected" ]]; then
      export JAVA_HOME="$detected"
    fi
  fi
}

detect_android_sdk() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    return
  fi

  local props_file
  if [[ -f "${ANDROID_DIR}/local.properties" ]]; then
    props_file="${ANDROID_DIR}/local.properties"
  elif [[ -f "${EXAMPLE_ANDROID_DIR}/local.properties" ]]; then
    props_file="${EXAMPLE_ANDROID_DIR}/local.properties"
  fi

  if [[ -n "$props_file" ]]; then
    local sdk_dir
    sdk_dir=$(grep "^sdk.dir=" "$props_file" | cut -d'=' -f2-)
    if [[ -n "$sdk_dir" ]]; then
      export ANDROID_SDK_ROOT="$sdk_dir"
    fi
  fi

  if [[ -z "${ANDROID_SDK_ROOT:-}" && -n "${ANDROID_HOME:-}" ]]; then
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
  fi
}

prepare_local_properties() {
  local template_file
  if [[ -f "${ANDROID_DIR}/local.properties" ]]; then
    template_file="${ANDROID_DIR}/local.properties"
  elif [[ -f "${EXAMPLE_ANDROID_DIR}/local.properties" ]]; then
    template_file="${EXAMPLE_ANDROID_DIR}/local.properties"
  fi

  if [[ -z "$template_file" ]]; then
    log "Arquivo local.properties não encontrado para usar como template."
    exit 1
  fi

  if [[ ! -f "${NATIVE_DIR}/local.properties" ]]; then
    log "Gerando ${NATIVE_DIR}/local.properties a partir de ${template_file}."
    cp "$template_file" "${NATIVE_DIR}/local.properties"
  fi
}

build_native_aar() {
  log "Executando flutter precache --android"
  "${FLUTTER_SDK}/bin/flutter" precache --android

  log "Compilando módulo native com Gradle wrapper do exemplo"
  pushd "$EXAMPLE_ANDROID_DIR" > /dev/null
  ./gradlew -p "$NATIVE_DIR" clean assembleRelease
  popd > /dev/null
}

extract_outputs() {
  mkdir -p "$OUTPUT_LIBS_DIR"

  local aar_path="${NATIVE_DIR}/build/outputs/aar/${AAR_OUTPUT_NAME}"
  if [[ ! -f "$aar_path" ]]; then
    log "AAR não encontrado em ${aar_path}"
    exit 1
  fi

  log "Copiando AAR para ${OUTPUT_LIBS_DIR}"
  cp "$aar_path" "${OUTPUT_LIBS_DIR}/${AAR_OUTPUT_NAME}"

  local temp_dir
  temp_dir="$(mktemp -d)"
  unzip -q "$aar_path" -d "$temp_dir"

  if [[ ! -f "${temp_dir}/classes.jar" ]]; then
    log "classes.jar não encontrado dentro do AAR"
    exit 1
  fi

  log "Gerando JAR ${JAR_OUTPUT_NAME}"
  mv "${temp_dir}/classes.jar" "${OUTPUT_LIBS_DIR}/${JAR_OUTPUT_NAME}"

  rm -rf "$temp_dir"
}

main() {
  detect_flutter
  ensure_env_var "FLUTTER_SDK" "${FLUTTER_SDK:-}" ""
  detect_java
  ensure_env_var "JAVA_HOME" "${JAVA_HOME:-}" "/usr/libexec/java_home -v 17"
  detect_android_sdk
  ensure_env_var "ANDROID_SDK_ROOT" "${ANDROID_SDK_ROOT:-}" ""

  prepare_local_properties
  build_native_aar
  extract_outputs

  log "Build finalizado com sucesso. Artefatos disponíveis em ${OUTPUT_LIBS_DIR}."
}

main "$@"

