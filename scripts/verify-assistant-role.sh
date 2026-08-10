#!/usr/bin/env bash
set -euo pipefail

package_name="${1:?usage: verify-assistant-role.sh PACKAGE_NAME}"
role_name="android.app.role.ASSISTANT"
interactor_short="$package_name/.assistant.ChalnaVoiceInteractionService"
interactor_full="$package_name/app.chalna.capture.assistant.ChalnaVoiceInteractionService"
recognizer_short="$package_name/.assistant.ChalnaRecognitionService"
recognizer_full="$package_name/app.chalna.capture.assistant.ChalnaRecognitionService"

adb shell cmd role add-role-holder --user 0 "$role_name" "$package_name"
adb shell cmd role get-role-holders --user 0 "$role_name" | tr -d '\r' | grep -Fx "$package_name"

for _ in $(seq 1 20); do
  interactor="$(adb shell settings get secure voice_interaction_service | tr -d '\r')"
  recognizer="$(adb shell settings get secure voice_recognition_service | tr -d '\r')"
  if { test "$interactor" = "$interactor_short" || test "$interactor" = "$interactor_full"; } &&
     { test "$recognizer" = "$recognizer_short" || test "$recognizer" = "$recognizer_full"; }; then
    printf 'Assistant role wired to %s\n' "$interactor"
    printf 'Recognition service wired to %s\n' "$recognizer"
    exit 0
  fi
  sleep 1
done

printf 'Assistant role holder was accepted but platform wiring did not converge.\n' >&2
printf 'voice_interaction_service=%s\n' "$interactor" >&2
printf 'voice_recognition_service=%s\n' "$recognizer" >&2
exit 1
