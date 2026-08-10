#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_HOME:?ANDROID_HOME is required}"
: "${BUNDLETOOL_JAR:?BUNDLETOOL_JAR is required}"
: "${VERSION:?VERSION is required}"
: "${VERSION_CODE:?VERSION_CODE is required}"
: "${GITHUB_SHA:?GITHUB_SHA is required}"
: "${GITHUB_RUN_ID:?GITHUB_RUN_ID is required}"
: "${EXPECTED_RELEASE_CERT_SHA256:?EXPECTED_RELEASE_CERT_SHA256 is required}"
: "${CHALNA_RELEASE_CERT_SHA256:?CHALNA_RELEASE_CERT_SHA256 is required}"

apk_input="${1:?release APK path is required}"
aab_input="${2:?release AAB path is required}"
previous_apk="${3:?previous release APK path is required}"
sbom_input="${4:?CycloneDX SBOM path is required}"

apk="chalna-v${VERSION}-release.apk"
aab="chalna-v${VERSION}-release.aab"
checksum="chalna-v${VERSION}-SHA256.txt"
build_info="chalna-v${VERSION}-build-info.json"
sbom="chalna-v${VERSION}-sbom.json"

test -s "$apk_input"
test -s "$aab_input"
test -s "$previous_apk"
test -s "$sbom_input"
cp "$apk_input" "$apk"
cp "$aab_input" "$aab"
cp "$sbom_input" "$sbom"

build_tools_version="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V | tail -n 1)"
test -n "$build_tools_version"
build_tools="$ANDROID_HOME/build-tools/$build_tools_version"
apkanalyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
test -x "$apkanalyzer"
test -x "$build_tools/apksigner"
test -x "$build_tools/zipalign"
test -x "$build_tools/aapt2"
test -s "$BUNDLETOOL_JAR"
test -s app/src/main/baseline-prof.txt
test -s app/src/main/startup-prof.txt
test "$(wc -l < app/src/main/baseline-prof.txt)" -ge 20
test "$(wc -l < app/src/main/startup-prof.txt)" -ge 20
if grep -v 'Lapp/chalna/capture/' app/src/main/baseline-prof.txt | grep -q '[^[:space:]]'; then
  echo 'Baseline profile contains a rule outside the Chalna package.' >&2
  exit 1
fi

"$build_tools/zipalign" -c -P 16 -v 4 "$apk"
certificate_output="$("$build_tools/apksigner" verify --verbose --print-certs "$apk")"
printf '%s\n' "$certificate_output"
grep -q 'Verified using v2 scheme.*true' <<< "$certificate_output"
certificate_sha="$(sed -n 's/^.*certificate SHA-256 digest: //p' <<< "$certificate_output" | head -n 1 | tr '[:lower:]' '[:upper:]')"
test -n "$certificate_sha"
test "$certificate_sha" = "$EXPECTED_RELEASE_CERT_SHA256"
test "$certificate_sha" = "$CHALNA_RELEASE_CERT_SHA256"
if grep -qi 'Android Debug' <<< "$certificate_output"; then
  echo 'Release APK uses a debug signing identity.' >&2
  exit 1
fi

previous_certificate_output="$("$build_tools/apksigner" verify --verbose --print-certs "$previous_apk")"
previous_certificate_sha="$(sed -n 's/^.*certificate SHA-256 digest: //p' <<< "$previous_certificate_output" | head -n 1 | tr '[:lower:]' '[:upper:]')"
test "$previous_certificate_sha" = "$EXPECTED_RELEASE_CERT_SHA256"
test "$previous_certificate_sha" = "$certificate_sha"

previous_app_id="$("$apkanalyzer" manifest application-id "$previous_apk")"
previous_version_code="$("$apkanalyzer" manifest version-code "$previous_apk")"
test "$previous_app_id" = 'app.chalna.capture'
[[ "$previous_version_code" =~ ^[0-9]+$ ]]
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]]
if (( VERSION_CODE <= previous_version_code )); then
  echo "Version code $VERSION_CODE must be greater than previous release code $previous_version_code." >&2
  exit 1
fi

app_id="$("$apkanalyzer" manifest application-id "$apk")"
version_name="$("$apkanalyzer" manifest version-name "$apk")"
actual_version_code="$("$apkanalyzer" manifest version-code "$apk")"
min_sdk="$("$apkanalyzer" manifest min-sdk "$apk")"
target_sdk="$("$apkanalyzer" manifest target-sdk "$apk")"
test "$app_id" = 'app.chalna.capture'
test "$version_name" = "$VERSION"
test "$actual_version_code" = "$VERSION_CODE"
test "$min_sdk" = '29'
test "$target_sdk" = '36'

"$build_tools/aapt2" dump permissions "$apk" | tee release-permissions.txt
for permission in \
  android.permission.INTERNET \
  android.permission.READ_MEDIA_VIDEO \
  android.permission.READ_EXTERNAL_STORAGE \
  android.permission.WRITE_EXTERNAL_STORAGE \
  android.permission.MANAGE_EXTERNAL_STORAGE \
  android.permission.SYSTEM_ALERT_WINDOW \
  android.permission.BIND_ACCESSIBILITY_SERVICE; do
  if grep -q "$permission" release-permissions.txt; then
    echo "Forbidden permission in release APK: $permission" >&2
    exit 1
  fi
done

"$apkanalyzer" manifest print "$apk" > release-manifest.xml
"$apkanalyzer" dex packages "$apk" > release-dex-packages.txt
if grep -Eqi 'android:debuggable="true"|VisualLab|Diagnostics' release-manifest.xml release-dex-packages.txt; then
  echo 'Release contains a debug-only surface or is debuggable.' >&2
  exit 1
fi
grep -q 'ChalnaVoiceInteractionService' release-manifest.xml
grep -q 'ChalnaVoiceInteractionSessionService' release-manifest.xml
grep -q 'ChalnaRecognitionService' release-manifest.xml
grep -q 'AssistFallbackActivity' release-manifest.xml
grep -q 'android.intent.category.DEFAULT' release-manifest.xml
grep -q 'ChalnaCaptureTileService' release-manifest.xml

python3 - release-manifest.xml <<'PY'
import sys
import xml.etree.ElementTree as ET

android = "{http://schemas.android.com/apk/res/android}"
allowed = {
    "app.chalna.capture.MainActivity",
    "app.chalna.capture.assistant.ChalnaVoiceInteractionService",
    "app.chalna.capture.assistant.ChalnaVoiceInteractionSessionService",
    "app.chalna.capture.assistant.ChalnaRecognitionService",
    "app.chalna.capture.assistant.AssistFallbackActivity",
    "app.chalna.capture.capture.ChalnaCaptureTileService",
}
root = ET.parse(sys.argv[1]).getroot()
for component in root.find("application"):
    if component.get(android + "exported") == "true":
        name = component.get(android + "name", "")
        if name not in allowed:
            raise SystemExit(f"Unexpected exported component: {name}")
PY

java -jar "$BUNDLETOOL_JAR" validate --bundle "$aab"
java -jar "$BUNDLETOOL_JAR" dump manifest --bundle "$aab" --module base > release-aab-manifest.xml
java -jar "$BUNDLETOOL_JAR" dump config --bundle "$aab" > release-aab-config.txt
grep -q 'package="app.chalna.capture"' release-aab-manifest.xml
grep -q "android:versionCode=\"${VERSION_CODE}\"" release-aab-manifest.xml
grep -q "android:versionName=\"${VERSION}\"" release-aab-manifest.xml
grep -q 'PAGE_ALIGNMENT_16K' release-aab-config.txt
zipinfo -1 "$apk" | grep -qx 'assets/dexopt/baseline.prof'
zipinfo -1 "$apk" | grep -qx 'assets/dexopt/baseline.profm'
zipinfo -1 "$aab" | grep -qx 'BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof'

jarsigner -verify "$aab" | tee release-aab-signature.txt
grep -qi 'jar verified' release-aab-signature.txt
aab_certificate_sha="$(keytool -printcert -jarfile "$aab" | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1 | tr -d ':' | tr '[:lower:]' '[:upper:]')"
test -n "$aab_certificate_sha"
test "$aab_certificate_sha" = "$certificate_sha"

native_list="$(zipinfo -1 "$apk" | grep -E '^lib/.+\.so$' || true)"
if test -n "$native_list"; then
  rm -rf release-native-libs
  mkdir release-native-libs
  unzip -q "$apk" 'lib/*.so' -d release-native-libs
  while IFS= read -r library; do
    while IFS= read -r alignment; do
      if (( alignment < 0x4000 )); then
        echo "Native LOAD segment is not 16 KB aligned: $library ($alignment)" >&2
        exit 1
      fi
    done < <(readelf -lW "$library" | awk '$1 == "LOAD" { print $NF }')
  done < <(find release-native-libs -type f -name '*.so' -print)
fi

if test -f app/build/outputs/mapping/release/mapping.txt; then
  if grep -Eqi 'VisualLab|Diagnostics' app/build/outputs/mapping/release/mapping.txt; then
    echo 'R8 mapping contains a debug-only surface.' >&2
    exit 1
  fi
fi

jq -e '.bomFormat == "CycloneDX" and (.components | length > 0)' "$sbom"
apk_sha="$(sha256sum "$apk" | cut -d' ' -f1)"
aab_sha="$(sha256sum "$aab" | cut -d' ' -f1)"
apk_size="$(stat -c%s "$apk")"
aab_size="$(stat -c%s "$aab")"
printf '%s  %s\n%s  %s\n' "$apk_sha" "$apk" "$aab_sha" "$aab" > "$checksum"

build_timestamp="$(git show -s --format=%cI "$GITHUB_SHA")"
jq -n \
  --arg applicationId "$app_id" \
  --arg versionName "$version_name" \
  --argjson versionCode "$actual_version_code" \
  --arg gitCommit "$GITHUB_SHA" \
  --arg tag "v$VERSION" \
  --argjson minSdk "$min_sdk" \
  --argjson targetSdk "$target_sdk" \
  --arg compileSdk '37.1' \
  --arg apkFilename "$apk" \
  --argjson apkByteSize "$apk_size" \
  --arg apkSha256 "$apk_sha" \
  --arg aabFilename "$aab" \
  --argjson aabByteSize "$aab_size" \
  --arg aabSha256 "$aab_sha" \
  --arg signingCertificateSha256 "$certificate_sha" \
  --arg previousCertificateSha256 "$previous_certificate_sha" \
  --argjson previousVersionCode "$previous_version_code" \
  --arg androidGradlePlugin '9.3.1' \
  --arg kotlin '2.3.21' \
  --arg cameraX '1.6.1' \
  --arg media3 '1.11.0' \
  --arg room '2.8.4' \
  --arg workflowRunId "$GITHUB_RUN_ID" \
  --arg buildTimestamp "$build_timestamp" \
  '{applicationId:$applicationId,versionName:$versionName,versionCode:$versionCode,previousVersionCode:$previousVersionCode,gitCommit:$gitCommit,tag:$tag,minSdk:$minSdk,targetSdk:$targetSdk,compileSdk:$compileSdk,apk:{filename:$apkFilename,byteSize:$apkByteSize,sha256:$apkSha256},aab:{filename:$aabFilename,byteSize:$aabByteSize,sha256:$aabSha256},signingCertificateSha256:$signingCertificateSha256,previousCertificateSha256:$previousCertificateSha256,toolchain:{androidGradlePlugin:$androidGradlePlugin,kotlin:$kotlin,cameraX:$cameraX,media3:$media3,room:$room},buildWorkflowRunId:$workflowRunId,buildTimestamp:$buildTimestamp}' \
  > "$build_info"

if test -n "${GITHUB_OUTPUT:-}"; then
  {
    echo "apk=$apk"
    echo "aab=$aab"
    echo "checksum=$checksum"
    echo "info=$build_info"
    echo "sbom=$sbom"
    echo "apk_sha=$apk_sha"
    echo "aab_sha=$aab_sha"
    echo "certificate_sha=$certificate_sha"
  } >> "$GITHUB_OUTPUT"
fi
