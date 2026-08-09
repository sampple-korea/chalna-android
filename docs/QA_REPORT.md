# QA report

Report date: 2026-08-10. Android compilation and tests ran only on GitHub-hosted runners, per repository policy.

## v1.1.0 status

The v1.1.0 source candidate at `6aa2cc9aa5e17f6c1d328ed24247325f271e9b07` passed Android CI and UI QA. It includes the capture library, Chalna Vault, local Media3 player, release-only Diagnostics/VisualLab exclusion, direct setup actions, optional notification readiness, refined invocation glow, and adaptive-icon mask matrix. Selected output images from that exact UI run are committed under `docs/screenshots/`.

## Remote evidence

| Area | Result | Evidence |
|---|---|---|
| Policy and dependency audit | Pass | Android CI [31322147848](https://github.com/sampple-korea/chalna-android/actions/runs/31322147848) |
| Formatting and static analysis | Pass | same run; all repository policy scripts returned success |
| Android Lint | Pass, warnings-as-errors | downloaded report states `No issues found.` |
| JVM tests | 32 passed, 0 failed, 0 errors, 0 skipped | five downloaded JUnit XML suites from run 31322147848 |
| Debug APK | Built and archived | `android-ci-31322147848` artifact |
| Instrumentation | 44 passed | UI QA [31322147867](https://github.com/sampple-korea/chalna-android/actions/runs/31322147867), API 34 AOSP ATD |
| Deterministic screenshot suite | 38 passed independently | same run; required-name/non-empty artifact gate also passed |
| Install/launch smoke | Pass | same API 34 emulator run; package launch and process check passed |
| Visual review | Pass after two rendered refinement passes | 38 PNGs downloaded and inspected across Home, Setup, Settings, Gallery, Player, glow frames, icon masks, Night/Mist, and 200% font |

APK signing, alignment, package/version, merged-permission, immutable-release, re-download, checksum, and signature evidence is emitted by `.github/workflows/release.yml` into the immutable Release and `chalna-v1.1.0-build-info.json` asset.

## Visual findings and fixes

The v1.1 first pass exposed a generic concentric status orb, oversized standalone gallery filters, card-heavy player actions, English-only review captures, flat hotspot discs in the perimeter glow, and setup-state screenshots that did not reveal their relevant rows. Refinement replaced the orb with a hollow angular capture instrument, consolidated the gallery filter into one compact segmented control, removed player-action cards, forced deterministic Korean resources, added same-day grid density, used cached radial hotspot shaders, increased Mist-theme optical definition, and made player metadata vertically scrollable. The regenerated matrix was then inspected for spacing, wrapping, contrast, glow falloff, cutout/corner handling, icon optical margins, selection clarity, and 200% font behavior.

## Source/security audit

- The v1.1.0 manifest contains no `INTERNET`, `READ_MEDIA_VIDEO`, location, contacts, SMS, call-log, broad-storage, overlay, accessibility-service, or boot-start permission/receiver.
- Material 2/3, Google Material, Material icon, and ripple imports/dependencies are blocked by CI policy.
- Camera provider acquisition, binding, Recorder preparation, and audio enablement occur only after an explicit service dispatch. No pre-capture, pre-buffer, camera warm-up, persistent binding, or hidden notification path was found.
- Voice assist structures/screenshots are ignored. Components are non-exported unless the Android voice-interaction contract requires system binding.
- Final audit fixes added live prerequisite reconciliation, microphone-gated audio enablement, stale MediaStore/Vault validation, durable active-attempt recovery, timeout/finalize cleanup, next-capture destination snapshots, and step-scoped release secrets.

## Physical-device-only validation

No physical Android device was connected. The following are not claimed as physically verified: OEM power-button/gesture routing, locked screen-on/off delivery, real rear-camera/audio encoding, privacy indicators, Device Gallery/Vault behavior, in-app playback, share/export/delete grants, camera-busy/low-storage behavior, Assistant-to-CameraX start latency, thermal/battery behavior, 90/120 Hz frame pacing, and TalkBack spoken output. Follow `docs/DEVICE_TEST_PLAN.md` before declaring a specific model supported.
