#!/usr/bin/env bash
set -euo pipefail

package_name="${1:?usage: verify-assistant-role.sh PACKAGE_NAME}"
role_name="android.app.role.ASSISTANT"
interactor_short="$package_name/.assistant.ChalnaVoiceInteractionService"
interactor_full="$package_name/app.chalna.capture.assistant.ChalnaVoiceInteractionService"
recognizer_short="$package_name/.assistant.ChalnaRecognitionService"
recognizer_full="$package_name/app.chalna.capture.assistant.ChalnaRecognitionService"
fallback_short="$package_name/.assistant.AssistFallbackActivity"
fallback_full="$package_name/app.chalna.capture.assistant.AssistFallbackActivity"

adb shell logcat -c >/dev/null 2>&1 || true
set +e
role_output="$(adb shell cmd role add-role-holder --user 0 "$role_name" "$package_name" 2>&1)"
role_status=$?
set -e
if (( role_status != 0 )); then
  printf 'add-role-holder exited %s\n' "$role_status" >&2
  printf '%s\n' "$role_output" >&2
  printf '%s\n' 'Assistant-role diagnostics:' >&2
  printf 'sdk=%s low_ram=%s\n' \
    "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" \
    "$(adb shell getprop ro.config.low_ram | tr -d '\r')" >&2
  adb shell cmd package query-activities --brief -a android.intent.action.ASSIST \
    -c android.intent.category.DEFAULT "$package_name" 2>&1 | tr -d '\r' >&2 || true
  adb shell cmd role get-role-holders --user 0 "$role_name" 2>&1 | tr -d '\r' >&2 || true
  adb shell dumpsys role 2>&1 | tr -d '\r' | tail -200 >&2 || true
  adb shell dumpsys package "$package_name" 2>&1 | tr -d '\r' | \
    grep -E -A8 -B3 'android.intent.action.ASSIST|VoiceInteractionService|AssistFallbackActivity|ChalnaRecognitionService' >&2 || true
  adb shell logcat -d -v brief 2>&1 | tr -d '\r' | \
    grep -E 'AssistantRoleBehavior|RoleController|RoleManager|VoiceInteraction|PermissionController' | tail -160 >&2 || true
  exit 1
fi

holder_seen=false
for _ in $(seq 1 20); do
  holders="$(adb shell cmd role get-role-holders --user 0 "$role_name" | tr -d '\r')"
  if grep -Fxq "$package_name" <<< "$holders"; then
    holder_seen=true
    break
  fi
  sleep 1
done
if test "$holder_seen" != true; then
  printf 'Assistant role transaction completed without retaining %s. holders=%s\n' "$package_name" "$holders" >&2
  exit 1
fi

for _ in $(seq 1 20); do
  interactor="$(adb shell settings get secure voice_interaction_service | tr -d '\r')"
  recognizer="$(adb shell settings get secure voice_recognition_service | tr -d '\r')"
  assistant="$(adb shell settings get secure assistant | tr -d '\r')"
  if { test "$interactor" = "$interactor_short" || test "$interactor" = "$interactor_full"; } &&
     { test "$recognizer" = "$recognizer_short" || test "$recognizer" = "$recognizer_full"; }; then
    printf 'Assistant role wired to %s\n' "$interactor"
    printf 'Recognition service wired to %s\n' "$recognizer"
    exit 0
  fi
  if test "$assistant" = "$fallback_short" || test "$assistant" = "$fallback_full"; then
    printf 'Assistant activity fallback wired to %s\n' "$assistant"
    exit 0
  fi
  sleep 1
done

printf 'Assistant role holder was accepted but platform wiring did not converge.\n' >&2
printf 'voice_interaction_service=%s\n' "$interactor" >&2
printf 'voice_recognition_service=%s\n' "$recognizer" >&2
printf 'assistant=%s\n' "$assistant" >&2
exit 1
