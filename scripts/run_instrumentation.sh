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
# Synthetic UI fixtures only: keep screenshots with the device diagnostics.
for name in covers mixed; do
  preview="build/device-diagnostics/leepe-${name}.png"
  if adb exec-out run-as pe.lee.feature.library.test cat "files/leepe-ui-preview/${name}.png" > "$preview" 2>/dev/null && [ -s "$preview" ]; then
    # API 35 logs also expose an encoded copy for review without a local emulator.
    if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "35" ]; then
      printf '\nLEEPE_PREVIEW_BEGIN %s\n' "$name"
      base64 -w0 "$preview"
      printf '\nLEEPE_PREVIEW_END\n'
    fi
  else
    rm -f "$preview"
  fi
done
exit "$status"
