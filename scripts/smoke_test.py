#!/usr/bin/env python3
"""Destructive to this app's test state: use an idle test device, never a live session."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

PACKAGE = "pt.mvplisboa.sessionoverlay"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit ADB serial of the test device")
    parser.add_argument("--artifacts", type=Path, default=Path("artifacts"))
    args = parser.parse_args()
    args.artifacts.mkdir(parents=True, exist_ok=True)

    def adb(*arguments, binary=False):
        return subprocess.check_output(
            ["adb", "-s", args.serial, *arguments], timeout=15,
            text=not binary, stderr=subprocess.STDOUT,
        )

    def command(name):
        output = adb("shell", "am", "broadcast", "--include-stopped-packages", "--receiver-foreground",
                     "-n", f"{PACKAGE}/.DebugControlReceiver", "-a", f"{PACKAGE}.DEBUG",
                     "--es", "command", name)
        if "result=0" not in output:
            raise AssertionError(output)
        match = re.search(r'data="(.*)"\s*$', output)
        if not match:
            raise AssertionError(output)
        return json.loads(match.group(1))

    def wait_for(**expected):
        deadline = time.monotonic() + 4
        while True:
            state = command("STATUS")
            if all(state.get(key) == value for key, value in expected.items()):
                return state
            if time.monotonic() >= deadline:
                raise AssertionError(f"Expected {expected}; got {state}")
            time.sleep(0.1)

    checks = []

    def passed(message):
        checks.append(message)
        print("PASS", message, flush=True)

    try:
        adb("shell", "am", "force-stop", PACKAGE)
        adb("shell", "appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "deny")
        # Foreground launch is needed to start an FGS without the overlay exemption.
        adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.SetupActivity")
        command("SHOW")
        denied = wait_for(service_running=True, visible=False)
        assert denied["last_error"], denied
        passed("Missing overlay permission reports an error without crashing")

        adb("shell", "appops", "set", PACKAGE, "SYSTEM_ALERT_WINDOW", "allow")
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
        command("SHOW")
        visible = wait_for(visible=True, last_error=None)
        deadline = time.monotonic() + 4
        while visible["width"] <= 0 and time.monotonic() < deadline:
            visible = command("STATUS")
        assert visible["width"] > 0 and visible["height"] > 0, visible
        screenshot = adb("exec-out", "screencap", "-p", binary=True)
        (args.artifacts / "overlay-visible.png").write_bytes(screenshot)
        windows = adb("shell", "dumpsys", "window", "windows")
        (args.artifacts / "windows-visible.txt").write_text(windows)
        focus = adb("shell", "dumpsys", "window")
        focus_line = next(line for line in focus.splitlines() if "mCurrentFocus=" in line)
        assert PACKAGE not in focus_line, focus_line
        passed(f"Opaque overlay attached at {visible['width']}×{visible['height']} without stealing focus")

        hide_latencies = []
        for _ in range(20):
            command("SHOW")
            command("SHOW")
            wait_for(visible=True, last_error=None)
            windows = adb("shell", "dumpsys", "window", "windows")
            count = len(re.findall(r"Window #\d+ Window\{[^\n]* MVP Session Overlay\}", windows))
            assert count == 1, f"Expected one overlay window; found {count}"
            start = time.monotonic()
            command("HIDE")
            wait_for(visible=False, pending_show=False, last_error=None)
            hide_latencies.append(time.monotonic() - start)
            command("HIDE")
            wait_for(visible=False, last_error=None)
        passed(f"20 repeated show/show/hide/hide cycles; one window; max ADB hide {max(hide_latencies):.3f}s")

        command("SHOW_DELAYED")
        wait_for(pending_show=True)
        command("HIDE")
        wait_for(visible=False, pending_show=False)
        time.sleep(10.5)
        wait_for(visible=False, pending_show=False)
        passed("Hide cancels a pending show; no stale window after the 10-second deadline")

        command("SHOW")
        wait_for(visible=True)
        adb("shell", "am", "force-stop", PACKAGE)
        wait_for(visible=False, service_running=False)
        command("START")
        wait_for(service_running=True, visible=False, pending_show=False)
        passed("Fresh process/service starts hidden after force-stop")

        command("SHOW")
        wait_for(visible=True)
        command("STOP")
        wait_for(service_running=False, visible=False)
        passed("Stopping service removes the attached window")

        command("SHOW")
        wait_for(visible=True)
        adb("shell", "am", "start", "-n", f"{PACKAGE}/.SetupActivity")
        wait_for(visible=False)
        passed("Opening administration hides the test window for local recovery")
        time.sleep(0.5)
        (args.artifacts / "setup.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        (args.artifacts / "smoke-results.json").write_text(json.dumps({
            "serial": args.serial,
            "sdk": adb("shell", "getprop", "ro.build.version.sdk").strip(),
            "checks": checks,
            "max_adb_hide_seconds": max(hide_latencies),
            "hdmi_verified": False,
        }, indent=2, ensure_ascii=False) + "\n")
    finally:
        # Leave the test device with no block, including when an assertion fails.
        adb("shell", "am", "force-stop", PACKAGE)


if __name__ == "__main__":
    main()
