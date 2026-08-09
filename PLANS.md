# Chalna v1.0.0 execution record

## Product goal

Ship a private, signed, immutable GitHub Release of an Android 10+ app that turns the selected system Assistant invocation into a visible local-only CameraX video recording toggle. No pre-capture and no network permission.

## Current state

- GitHub owner authenticated as `sampple-korea`.
- Private repository created: `sampple-korea/chalna-android`.
- Official platform and library documentation reviewed on 2026-08-09.
- Production implementation is complete. Android CI `31311630513` and UI QA `31311630522` are green for commit `3c8f06b90056f7eb24ac2417cf695968c2fa6f3c`; the final documentation/release-workflow commit must pass the same gates before publication.

## Decisions

- Application ID: `app.chalna.capture`; version `1.0.0` (`1`).
- `minSdk 29`, `compileSdk 37.1`, `targetSdk 36`; current stable AndroidX requires API 37 compilation while target 36 avoids Android 17 behavior opt-in before physical-device validation.
- AGP 9.3.1, Gradle 9.5.0, built-in Kotlin 2.3.21, JDK 17 bytecode.
- Compose BOM 2026.06.00; Foundation-only custom design system.
- CameraX 1.6.1 Recorder with `MediaStoreOutputOptions`; rear camera; no Preview use case.
- Single `app` module. Explicit coordinator/engine seams without a DI framework.
- Voice Interaction components are system-bound; recording service is not exported.
- Settings use DataStore Preferences and an in-memory `StateFlow` cache.
- Diagnostics retain only bounded metadata; never assist structure, screenshots, media contents, account, location, or foreground-app identity.
- CI uses GitHub-hosted runners exclusively for Android compilation, unit tests, lint, instrumentation, screenshots, and packaging.

## Implementation stages

1. Repository policy, product/UX/architecture documentation, Android scaffold.
2. Voice interaction, foreground capture service, CameraX Recorder, MediaStore, notification, haptic, state machine.
3. Setup, Home, settings, diagnostics, help/privacy, custom visuals, motion, accessibility, localization.
4. Unit, UI, screenshot, policy, lint, formatting, static-analysis tests.
5. Remote CI correction loop, screenshot inspection, deliberate design refinement.
6. Signing secrets, release build, APK validation, immutable release, remote re-download verification.

## Test strategy

- JVM tests cover command serialization, transition legality, deduplication, readiness, timing statistics, quality fallback, filenames, and recovery.
- Instrumentation covers setup/navigation/state rendering/settings/semantics/large-font themes.
- Deterministic screenshot states run on a fixed API/device/locale configuration and publish PNG artifacts for manual inspection.
- Scripts fail on forbidden Material imports/dependencies, forbidden permissions, pre-capture patterns, dynamic versions, placeholders, secrets, signing files, and unpinned actions.

## CI failures and fixes

- API 37.1 was required by current stable AndroidX; CI installation was corrected from API 37 to 37.1.
- Voice-interaction metadata, target-SDK lint, icon resources, and Canvas allocation warnings were corrected from complete CI logs.
- Emulator images initially failed for disk capacity; runner space reclamation and a 2 GiB userdata partition fixed creation.
- Runners without KVM exposed slow/offline installs; KVM access is enabled when available with a software fallback.
- Raw `am instrument` returned process success despite one failed screenshot; UI QA now uses `connectedDebugAndroidTest` and separately asserts `OK`/absence of `FAILURES!!!` for screenshot export.
- Screenshot cold-capture redraw was retried once; the final 15-state capture suite is green.

## Design review

UI QA artifact `ui-qa-api-34-31311630522` was downloaded and inspected at original resolution. The first pass exposed an overly cool recording action, cool error Aura, weak default settings, and incorrect variable-font axis selection. Refinement added state-specific warm/error spectra, a solid coral stop action, Auto/audio-on defaults, explicit font axes, stronger Korean/Latin weight, a five-step setup flow, and expanded screenshot coverage. The second artifact was inspected before committing five final captures in `docs/screenshots/`.

## Release readiness

The user-provided PKCS#12 signing source was parsed as a `PrivateKeyEntry`; alias, century-long certificate validity, RSA-4096 key, and SHA-256 certificate fingerprint were verified without committing the key. Five separate `CHALNA_RELEASE_*` secrets are configured. Repository immutable releases are enabled. The release workflow builds, validates, emulator-smokes, creates a complete draft, publishes it, re-downloads all assets, verifies checksum/signature/package/version, and runs GitHub release/asset integrity verification.

## Verification results

Source/CI/UI evidence is recorded in `docs/QA_REPORT.md`. Final APK-specific evidence is intentionally generated into the immutable `chalna-v1.0.0-build-info.json` release asset, because an APK hash and release run ID cannot be embedded into the source commit that produces them without changing that artifact.

## Remaining blockers

None confirmed. Physical-device-only OEM invocation, lock-screen delivery, hardware latency, heat, battery, and high-refresh-rate checks will remain explicitly unverified unless a device becomes available.
