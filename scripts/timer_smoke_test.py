#!/usr/bin/env python3
"""Exercise timers on an idle test TV without changing its current HDMI input."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

PACKAGE = "pt.mvplisboa.sessionoverlay"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--artifacts", type=Path, default=Path("artifacts/timers"))
    args = parser.parse_args()
    args.artifacts.mkdir(parents=True, exist_ok=True)

    def adb(*arguments, binary=False):
        return subprocess.check_output(["adb", "-s", args.serial, *arguments],
                                       timeout=15, text=not binary, stderr=subprocess.STDOUT)

    def command(name, **extras):
        arguments = ["shell", "am", "broadcast", "--include-stopped-packages", "--receiver-foreground",
                     "-n", f"{PACKAGE}/.DebugControlReceiver", "-a", f"{PACKAGE}.DEBUG", "--es", "command", name]
        for key, value in extras.items():
            arguments.extend(["--el", key, str(value)])
        output = adb(*arguments)
        assert "result=0" in output, output
        return json.loads(re.search(r'data="(.*)"\s*$', output).group(1))

    def wait_for(timeout=5, **expected):
        deadline = time.monotonic() + timeout
        while True:
            state = command("STATUS")
            if all(state.get(key) == value for key, value in expected.items()):
                return state
            if time.monotonic() > deadline:
                raise AssertionError(f"Expected {expected}; got {state}")
            time.sleep(0.1)

    def focus():
        line = next(line.strip() for line in adb("shell", "dumpsys", "window").splitlines()
                    if "mCurrentFocus=" in line)
        # TCL can recreate TVActivity's Window while keeping the same app and HDMI.
        # Compare the focused component rather than WindowManager's object hash.
        match = re.search(r" ([\w.$]+/[\w.$]+)\}", line)
        return match.group(1) if match else line

    report = {"serial": args.serial, "checks": [], "screenshots": {}, "hdmi_visually_verified": False}

    def passed(message):
        report["checks"].append(message)
        print("PASS", message, flush=True)

    def capture(name):
        data = adb("exec-out", "screencap", "-p", binary=True)
        if data.startswith(b"\x89PNG\r\n\x1a\n"):
            (args.artifacts / f"{name}.png").write_bytes(data)
            report["screenshots"][name] = "captured"
        else:
            report["screenshots"][name] = "Firmware returned no PNG while HDMI active"

    try:
        command("HIDE")
        command("START")
        wait_for(service_running=True, foreground_service=True, last_error=None)
        service = adb("shell", "dumpsys", "activity", "services", PACKAGE)
        assert "isForeground=true" in service, service
        report["focus_before"] = focus()
        (args.artifacts / "foreground-service.txt").write_text(service)
        input_before = adb("shell", "dumpsys", "tv_input")
        (args.artifacts / "tv-input-before.txt").write_text(input_before)
        report["active_inputs_before"] = re.findall(r"^\s+inputId: (.+)$", input_before, re.MULTILINE)
        passed("Foreground service confirmed independently by Android dumpsys")

        for seconds in (900, 300, 60, 30):
            command("NOTICE", threshold_seconds=seconds)
            wait_for(notice_visible=True, notice_threshold_seconds=seconds, visible=False, last_error=None)
            time.sleep(0.35)
            command("NOTICE", threshold_seconds=seconds)
            windows = adb("shell", "dumpsys", "window", "windows")
            count = len(re.findall(r"Window #\d+ Window\{[^\n]* MVP Session Notice\}", windows))
            assert count == 1, f"Expected one corner window; found {count}"
            (args.artifacts / f"notice-{seconds}-windows.txt").write_text(windows)
            assert focus() == report["focus_before"], "Notification changed the focused application"
            capture(f"notice-{seconds}")
        passed("15m / 5m / 1m / 30s cards; repeated previews keep exactly one window without changing focus")
        wait_for(timeout=12, notice_visible=False, visible=False)
        passed("Transient corner warning disappears automatically after 10 seconds")

        command("TIMER_START", duration_seconds=61)
        wait_for(notice_visible=True, notice_threshold_seconds=60, timer_state="ACTIVE", last_error=None)
        command("TIMER_START", duration_seconds=31)
        wait_for(notice_visible=True, notice_threshold_seconds=30, timer_state="ACTIVE", last_error=None)
        passed("Real countdown crosses the 60s and 30s boundaries and selects the correct notices")

        command("TIMER_START", duration_seconds=3)
        wait_for(timer_state="ACTIVE", notice_visible=False)
        wait_for(timeout=5, timer_state="EXPIRED", visible=True, notice_visible=False, last_error=None)
        command("TIMER_EXTEND", extend_seconds=6)
        wait_for(timer_state="ACTIVE", visible=False, last_error=None)
        command("HIDE")
        time.sleep(6.5)
        wait_for(timer_state="IDLE", visible=False, notice_visible=False, pending_show=False, last_error=None)
        passed("Expiration shows TIME IS UP; extension hides it; Hide cancels the new deadline")

        command("TIMER_START", duration_seconds=4)
        wait_for(timer_state="ACTIVE")
        time.sleep(1)
        command("TIMER_EXTEND", extend_seconds=10)
        time.sleep(4)
        wait_for(timer_state="ACTIVE", visible=False, last_error=None)
        passed("Extending an active session invalidates its previous expiration")

        command("DEMO")
        wait_for(demo_running=True, notice_visible=True)
        command("HIDE")
        time.sleep(12.5)
        wait_for(demo_running=False, notice_visible=False, visible=False, timer_state="IDLE")
        passed("Hide cancels the demo before its next scheduled warning")
        report["focus_after"] = focus()
        assert report["focus_after"] == report["focus_before"], report
        input_after = adb("shell", "dumpsys", "tv_input")
        (args.artifacts / "tv-input-after.txt").write_text(input_after)
        report["active_inputs_after"] = re.findall(r"^\s+inputId: (.+)$", input_after, re.MULTILINE)
        assert report["active_inputs_before"] == report["active_inputs_after"], "TV input changed during test"
        passed("Original application/input focus preserved through all timer tests")
        report["success"] = True
    except Exception as error:
        report["success"] = False
        report["error"] = str(error)
        raise
    finally:
        command("HIDE")
        report["final_state"] = command("STATUS")
        (args.artifacts / "timer-results.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
