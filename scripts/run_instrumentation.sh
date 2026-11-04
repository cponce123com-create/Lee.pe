#!/usr/bin/env bash
# Capture evidence while the emulator is still alive; preserve Gradle's exit code.
set -uo pipefail
mkdir -p build/device-diagnostics
adb logcat -c || true
./gradlew --no-daemon --stacktrace --max-workers=2 -Dorg.gradle.parallel=false -Dorg.gradle.jvmargs=-Xmx4g connectedDebugAndroidTest
status=$?
if [ "$status" -ne 0 ]; then
  adb logcat -d > build/device-diagnostics/logcat.txt 2>&1 || true
  adb exec-out screencap -p > build/device-diagnostics/screenshot.png || true
  adb shell dumpsys activity > build/device-diagnostics/activity.txt 2>&1 || true
fi
exit "$status"
