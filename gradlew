#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="9.3.1"
GRADLE_HOME="${HOME}/.gradle/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"

if ! command -v gradle >/dev/null 2>&1; then
  if [ ! -x "${GRADLE_BIN}" ]; then
    mkdir -p "${HOME}/.gradle"
    TMP_ZIP="$(mktemp /tmp/gradle-XXXXXX.zip)"
    curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "${TMP_ZIP}"
    rm -rf "${GRADLE_HOME}"
    unzip -q "${TMP_ZIP}" -d "${HOME}/.gradle"
    rm -f "${TMP_ZIP}"
  fi
  export PATH="${GRADLE_HOME}/bin:${PATH}"
fi

exec gradle "$@"
