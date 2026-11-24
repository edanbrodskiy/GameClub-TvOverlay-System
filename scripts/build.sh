#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# Use a configured JDK or the standard per-user/system installation on macOS.
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! java_version="$("$java_bin" -version 2>&1)" || [[ "$java_version" != *'version "17.'* ]]; then
    echo 'JDK 17 is required. Install JDK 17 and set JAVA_HOME to its installation directory.' >&2
    exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && ! -f "$project_dir/local.properties" && -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

cd -- "$project_dir"
if (( $# == 0 )); then
    set -- :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
fi
exec ./gradlew "$@"

