#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
device="${MVP_ADB_SERIAL:-192.168.10.53:5555}"
package=pt.mvplisboa.sessionoverlay
action="${1:-status}"

case "$action" in
    connect) exec adb connect "$device" ;;
    install)
        apk_path="${MVP_APK_PATH:-$project_dir/app/build/outputs/apk/debug/app-debug.apk}"
        if [[ ! -f "$apk_path" && -z "${MVP_APK_PATH:-}" ]]; then
            shopt -s nullglob
            packaged_apks=("$project_dir"/dist/mvp-session-overlay-*-debug.apk)
            if (( ${#packaged_apks[@]} == 1 )); then
                apk_path="${packaged_apks[0]}"
            fi
        fi
        if [[ ! -f "$apk_path" ]]; then
            echo 'APK not found. Run ./scripts/build.sh or set MVP_APK_PATH to a packaged APK.' >&2
            exit 1
        fi
        exec adb -s "$device" install -r "$apk_path" ;;
    setup) exec adb -s "$device" shell am start -n "$package/.SetupActivity" ;;
    activate)
        adb -s "$device" shell am start -W -n "$package/.SetupActivity"
        "$project_dir/scripts/tv.sh" start
        for attempt in {1..25}; do
            observed="$("$project_dir/scripts/tv.sh" status)"
            if [[ "$observed" == *'"foreground_service":true'* ]]; then
                adb -s "$device" shell input keyevent KEYCODE_BACK
                printf '%s\n' "$observed"
                exit 0
            fi
            sleep 0.2
        done
        echo 'Foreground startup was not confirmed. Administrator screen remains open for diagnosis.' >&2
        exit 1 ;;
    permission) exec adb -s "$device" shell appops set "$package" SYSTEM_ALERT_WINDOW allow ;;
    emergency-hide) exec adb -s "$device" shell am force-stop "$package" ;;
    logs) exec adb -s "$device" logcat -v time -s MVPOverlay:I AndroidRuntime:E ;;
    start) command=START ;;
    show) command=SHOW ;;
    show-delayed) command=SHOW_DELAYED ;;
    hide) command=HIDE ;;
    stop) command=STOP ;;
    status) command=STATUS ;;
    demo) command=DEMO ;;
    cancel-timer) command=TIMER_CANCEL ;;
    timer|extend|notice)
        seconds="${2:-}"
        if [[ ! "$seconds" =~ ^[0-9]+$ ]] || (( 10#$seconds < 1 || 10#$seconds > 86400 )); then
            echo 'Specify seconds from 1 to 86400, for example: tv.sh timer 1200' >&2
            exit 2
        fi
        case "$action" in
            timer) command=TIMER_START; extra_key=duration_seconds ;;
            extend) command=TIMER_EXTEND; extra_key=extend_seconds ;;
            notice) command=NOTICE; extra_key=threshold_seconds ;;
        esac
        ;;
    *)
        echo 'Usage: tv.sh {connect|install|setup|activate|permission|start|show|show-delayed|hide|stop|status|logs|emergency-hide|demo|cancel-timer|timer SECONDS|extend SECONDS|notice SECONDS}' >&2
        exit 2 ;;
esac

# Explicit receiver is only in debug APKs and requires the shell's DUMP permission.
# --include-stopped-packages also permits testing immediately after fresh install.
set -- adb -s "$device" shell am broadcast --include-stopped-packages --receiver-foreground \
    -n "$package/.DebugControlReceiver" -a "$package.DEBUG" --es command "$command"
if [[ -n "${extra_key:-}" ]]; then
    set -- "$@" --el "$extra_key" "$seconds"
fi
exec "$@"
