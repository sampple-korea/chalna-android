# QA report

Report date: 2026-08-10. Android compilation and tests ran only on GitHub-hosted runners, per repository policy.

## v1.2.0 production-hardening candidate

The v1.2 candidate preserves the Assistant-role eligibility repair delivered in v1.1.1. It does not claim that the recognition-service fix is new: the non-empty recognition service, default recognition-service category, `supportsAssist` metadata, application ID, and signing-continuity anchor are retained. The process model is intentionally changed so voice interaction, session, recognition, capture, and UI share the default application process and one authoritative capture state.

The final source audit also corrected a distinct v1.2 invocation-identity defect: Android 14+ publishes `VoiceInteractionSession.KEY_SHOW_SESSION_ID` as an integer, not a string. Reading the documented integer restores early `onPrepareToShowSession` dispatch and lets `onShow` consume the same receipt instead of producing an anonymous second identity. API 34 instrumentation now asserts both the parsed session key and derived invocation ID.

At commit `09ab820da03d5bcd8a26c2702f1e987db84013df`, Android CI [31364048916](https://github.com/sampple-korea/chalna-android/actions/runs/31364048916) passed policy, formatting, Detekt, Android Lint, JVM tests, dependency inspection, and debug assembly. UI QA [31364048942](https://github.com/sampple-korea/chalna-android/actions/runs/31364048942) passed API 34 instrumentation, accessibility smoke, all 39 deterministic screenshot comparisons, and independent Assistant-role qualification on API 29, 33, 35, and 36. The API 29/33 verifier uses `dumpsys role` because those platform images do not expose the newer `cmd role get-role-holders` command; it still verifies the system role holder and both secure-service mappings rather than bypassing eligibility.

Benchmark [31364048906](https://github.com/sampple-korea/chalna-android/actions/runs/31364048906) produced 464 unobfuscated, application-only startup/baseline rules and ten Perfetto traces. Both committed profile files have SHA-256 `3F2B2E2AEC3EA1986DA9F01DEE6DDB832B15E254503490C43F77F0F71E91668A`. Cold initial-display median was 334.505 ms against a 612.806 ms gate; warm median was 55.482 ms against a 197.428 ms gate. These are emulator software-path results, not physical camera latency.

The UI artifact contains 39 PNGs with zero exact and perceptual differences from committed goldens. The downloaded Home, recording, Gallery, selection, Player, Settings, large-font, Glow activation/peak/stop, tall/colorful/cutout, and circle/squircle/teardrop icon-mask images were opened and inspected. State hierarchy, custom icon optical weight, Korean wrapping, absence of a persistent recording border, Gallery density, Player hierarchy, Mist/Night contrast, Glow bloom/decay, cutout clearance, and adaptive-icon safe margins were accepted after the deliberate refinement pass.

Durable reconciliation tests cover deferred metadata indexing and Vault-export relationship recovery without deleting valid media. Legacy-import fixtures cover empty, 250-row, mixed-destination, malformed/truncated, unknown-quality, duplicate, idempotent rerun, and backup-retention cases. Release publication evidence is intentionally not claimed here until the signed v1.2.0 workflow completes and its downloaded assets are independently verified.

## v1.1.1 Assistant-role hotfix

Android RoleController excludes a voice-interaction package when its metadata omits `recognitionService`. v1.1.0 removed that required attribute, so Android correctly treated Chalna as an unqualified Assistant candidate. v1.1.1 restores non-empty session, recognition, and `supportsAssist` metadata; declares a system-bound recognizer that rejects requests without opening audio; and restores the session service's dedicated process.

The code candidate at `2988731f124b0a2f227fc9f3e850b1539fb0752b` passed Android CI [31324093962](https://github.com/sampple-korea/chalna-android/actions/runs/31324093962) and UI QA [31324097583](https://github.com/sampple-korea/chalna-android/actions/runs/31324097583). The API 34 AOSP emulator accepted `app.chalna.capture.debug` as `android.app.role.ASSISTANT` without qualification bypass and mapped both secure settings to Chalna's declared VoiceInteractionService and RecognitionService. The independent deterministic screenshot pass also completed 38 tests and retained its artifact gate.

The signed release workflow repeats that same role-assignment and secure-wiring proof against the exact release APK before it can create a draft. Publication, signing continuity, and remote-asset values belong to the immutable v1.1.1 release workflow and build-info asset.

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

APK signing, alignment, package/version, merged-permission, immutable-release, re-download, checksum, and signature evidence is emitted by `.github/workflows/release.yml` into the immutable Release and versioned build-info asset.

## Visual findings and fixes

The v1.1 first pass exposed a generic concentric status orb, oversized standalone gallery filters, card-heavy player actions, English-only review captures, flat hotspot discs in the perimeter glow, and setup-state screenshots that did not reveal their relevant rows. Refinement replaced the orb with a hollow angular capture instrument, consolidated the gallery filter into one compact segmented control, removed player-action cards, forced deterministic Korean resources, added same-day grid density, used cached radial hotspot shaders, increased Mist-theme optical definition, and made player metadata vertically scrollable. The regenerated matrix was then inspected for spacing, wrapping, contrast, glow falloff, cutout/corner handling, icon optical margins, selection clarity, and 200% font behavior.

## Source/security audit

- The v1.1.1 manifest contains no `INTERNET`, `READ_MEDIA_VIDEO`, location, contacts, SMS, call-log, broad-storage, overlay, accessibility-service, or boot-start permission/receiver.
- Material 2/3, Google Material, Material icon, and ripple imports/dependencies are blocked by CI policy.
- Camera provider acquisition, binding, Recorder preparation, and audio enablement occur only after an explicit service dispatch. No pre-capture, pre-buffer, camera warm-up, persistent binding, or hidden notification path was found.
- Voice assist structures/screenshots are ignored. Components are non-exported unless the Android voice-interaction contract requires system binding.
- Final audit fixes added live prerequisite reconciliation, microphone-gated audio enablement, stale MediaStore/Vault validation, durable active-attempt recovery, timeout/finalize cleanup, next-capture destination snapshots, and step-scoped release secrets.

## Physical-device-only validation

No physical Android device was connected. The following are not claimed as physically verified: OEM power-button/gesture routing, locked screen-on/off delivery, real rear-camera/audio encoding, privacy indicators, Device Gallery/Vault behavior, in-app playback, share/export/delete grants, camera-busy/low-storage behavior, Assistant-to-CameraX start latency, thermal/battery behavior, 90/120 Hz frame pacing, and TalkBack spoken output. Follow `docs/DEVICE_TEST_PLAN.md` before declaring a specific model supported.
