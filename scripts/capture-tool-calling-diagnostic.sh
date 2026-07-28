#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.jesjobom.ararai"
ARTIFACT_DIR="${1:-/home/node/.openclaw/jarvis/artifacts/ararai}"
APK_PATH="${ARTIFACT_DIR}/app-debug.apk"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
LOG_PATH="${ARTIFACT_DIR}/tool-calling-${RUN_ID}.log"
EXIT_INFO_PATH="${ARTIFACT_DIR}/tool-calling-${RUN_ID}-exit-info.txt"

command -v adb >/dev/null 2>&1 || {
    echo "adb não foi encontrado no PATH." >&2
    exit 1
}

adb get-state >/dev/null
mkdir -p "${ARTIFACT_DIR}"

if [[ "${INSTALL_APK:-1}" == "1" ]]; then
    [[ -f "${APK_PATH}" ]] || {
        echo "APK não encontrado: ${APK_PATH}" >&2
        exit 1
    }
    adb install -r "${APK_PATH}"
fi

adb shell am force-stop "${PACKAGE_NAME}"
adb logcat -c

finish() {
    trap - INT TERM EXIT
    adb shell dumpsys activity exit-info "${PACKAGE_NAME}" >"${EXIT_INFO_PATH}" || true
    echo
    echo "Logcat: ${LOG_PATH}"
    echo "Exit info: ${EXIT_INFO_PATH}"
}
trap finish INT TERM EXIT

echo "Abra o ArarAI e execute:"
echo "Models → Diagnostics → Structured tool calling → multi-turn-reuse"
echo "Depois de o relatório aparecer, compartilhe-o e toque em Close and release process."
echo "Pressione Ctrl+C aqui somente depois que o processo isolado fechar."
echo

adb logcat -b all -v threadtime |
    tee "${LOG_PATH}" |
    grep --line-buffered -E "ArarAI\\.ToolCalling|ArarAI\\.LiteRtLm|AndroidRuntime|FATAL EXCEPTION|lmkd|lowmemorykiller"
