#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Error: build-dmg.sh requires macOS to generate a DMG (detected $(uname -s))." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

is_homebrew_jdk() {
  local path="${1:-}"
  [[ "$path" == *"/homebrew/"* || "$path" == *"/Cellar/"* || "$path" == *"/usr/local/opt/openjdk"* ]]
}

EXTRA_GRADLE_ARGS=()

if [[ -z "${JAVA_HOME:-}" ]] || is_homebrew_jdk "$JAVA_HOME"; then
  CANDIDATE_JDK=""

  if CANDIDATE_17="$(/usr/libexec/java_home -v 17 2>/dev/null)" && ! is_homebrew_jdk "$CANDIDATE_17"; then
    CANDIDATE_JDK="$CANDIDATE_17"
  else
    while IFS= read -r jdk_path; do
      if [[ -n "$jdk_path" ]] && ! is_homebrew_jdk "$jdk_path"; then
        CANDIDATE_JDK="$jdk_path"
        break
      fi
    done < <(/usr/libexec/java_home -V 2>&1 | awk -F'" ' '/^[[:space:]]*(1[7-9]|[2-9][0-9])\./ {print $NF}')
  fi

  if [[ -n "$CANDIDATE_JDK" ]]; then
    export JAVA_HOME="$CANDIDATE_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using JDK for packaging: $JAVA_HOME"
  else
    echo "Warning: No non-Homebrew JDK 17+ found; disabling Compose JDK vendor check." >&2
    EXTRA_GRADLE_ARGS+=("-Pcompose.desktop.packaging.checkJdkVendor=false")
  fi
fi

./gradlew :composeApp:packageReleaseDmg ${EXTRA_GRADLE_ARGS[@]+"${EXTRA_GRADLE_ARGS[@]}"} "$@"

DMG_DIR="$SCRIPT_DIR/composeApp/build/compose/binaries/main-release/dmg"
if compgen -G "$DMG_DIR/*.dmg" > /dev/null; then
  echo "Generated release DMG:"
  ls -lh "$DMG_DIR"/*.dmg
else
  echo "Error: build completed, but no .dmg file was found in $DMG_DIR" >&2
  exit 1
fi
