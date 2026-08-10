#!/usr/bin/env bash
set -euo pipefail

./gradlew --no-daemon --stacktrace :benchmark:connectedProfileAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.chalna.capture.benchmark.BaselineProfileGenerator

profile_root="benchmark/build/outputs/connected_android_test_additional_output"
startup_profile="$(find "$profile_root" -type f -name '*-startup-prof.txt' -size +0c -print -quit)"
baseline_profile="$(find "$profile_root" -type f -name '*-baseline-prof.txt' -size +0c -print -quit)"
test -n "$startup_profile"
cp "$startup_profile" generated-startup-prof.txt
if test -n "$baseline_profile"; then
  cp "$baseline_profile" generated-baseline-prof.txt
else
  # Benchmark 1.4 can emit only the startup artifact for a startup-only CUJ.
  # Those rules are also valid baseline rules, per the official API contract.
  cp "$startup_profile" generated-baseline-prof.txt
fi

test -s generated-baseline-prof.txt
test -s generated-startup-prof.txt
test "$(wc -l < generated-baseline-prof.txt)" -ge 20
test "$(wc -l < generated-startup-prof.txt)" -ge 20
grep -q 'Lapp/chalna/capture/' generated-baseline-prof.txt
grep -q 'Lapp/chalna/capture/' generated-startup-prof.txt

./gradlew --no-daemon --stacktrace :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.chalna.capture.benchmark.StartupBenchmark
