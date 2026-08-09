# QA report

Report date: 2026-08-09. Android compilation and tests ran only on GitHub-hosted runners, per repository policy.

## v1.1.0 status

Current source targets v1.1.0/version code 2. Its gallery, Chalna Vault, local Media3 player, Diagnostics/VisualLab removal, gallery/glow/icon screenshots, signing continuity, APK policy, and immutable-release verification are **Pending verification**. The evidence below is the immutable v1.0.0 baseline and must not be presented as proof of v1.1.0.

## Remote evidence

| Area | Result | Evidence |
|---|---|---|
| Policy and dependency audit | Pass | Android CI [31311630513](https://github.com/sampple-korea/chalna-android/actions/runs/31311630513) |
| Formatting and static analysis | Pass | same run; repository scripts returned success |
| Android Lint | Pass, warnings-as-errors | same run; downloaded `lint-results-release` reports inspected |
| JVM tests | 13 passed, 0 failed, 0 skipped | downloaded `testDebugUnitTest` HTML from run 31311630513 |
| Debug APK | Built and archived | `android-ci-31311630513` artifact |
| Instrumentation | 18 passed | UI QA [31311630522](https://github.com/sampple-korea/chalna-android/actions/runs/31311630522), API 34 AOSP ATD |
| Screenshot suite | 15 passed independently | same run; instrumentation log ended `OK (15 tests)` and contained no failure marker |
| Install/launch smoke | Pass | same API 34 emulator run; application process verified |
| Visual review | Pass after deliberate refinement | downloaded artifact `ui-qa-api-34-31311630522`; five selected PNGs committed under `docs/screenshots/` |

The v1.1.0 release commit must repeat Android CI and UI QA. APK signing, alignment, package/version, merged-permission, immutable-release, re-download, checksum, and signature evidence is emitted by `.github/workflows/release.yml` into the immutable Release and `chalna-v1.1.0-build-info.json` asset.

## Visual findings and fixes

The first rendered pass showed an all-cool recording action, a cool error ring, default screenshot settings inconsistent with product defaults, and a variable font rendered at its thin default axis. The refinement pass introduced recording rose/coral energy, amber/coral error Aura, a solid stop control, Auto quality/audio-on defaults, explicit Noto Sans Korean weight axes, a five-step setup flow, and a 15-state screenshot matrix. Night and Mist were then re-rendered and inspected for hierarchy, wrapping, contrast, glow intensity, system insets, icon weight, and clipping.

## Source/security audit

- v1.0.0 manifest contains no `INTERNET`, location, contacts, SMS, call-log, broad-storage, overlay, accessibility-service, or boot-start permission/receiver. v1.1.0 additionally requires proof that `READ_MEDIA_VIDEO` is absent.
- Material 2/3, Google Material, Material icon, and ripple imports/dependencies are blocked by CI policy.
- Camera provider acquisition, binding, Recorder preparation, and audio enablement occur only after an explicit service dispatch. No pre-capture, pre-buffer, camera warm-up, persistent binding, or hidden notification path was found.
- Voice assist structures/screenshots are ignored. Components are non-exported unless the Android voice-interaction contract requires system binding.
- Final audit fixes added live prerequisite reconciliation, microphone-gated audio enablement, stale MediaStore validation, timeout/finalize cleanup, and step-scoped release secrets.

## Physical-device-only validation

No physical Android device was connected. The following are not claimed as physically verified: OEM power-button/gesture routing, locked screen-on/off delivery, real rear-camera/audio encoding, privacy indicators, Device Gallery/Vault behavior, in-app playback, share/export/delete grants, camera-busy/low-storage behavior, Assistant-to-CameraX start latency, thermal/battery behavior, 90/120 Hz frame pacing, and TalkBack spoken output. Follow `docs/DEVICE_TEST_PLAN.md` before declaring a specific model supported.
