# Chalna v1.0.0 execution record

## Product goal

Ship a private, signed, immutable GitHub Release of an Android 10+ app that turns the selected system Assistant invocation into a visible local-only CameraX video recording toggle. No pre-capture and no network permission.

## Current state

- GitHub owner authenticated as `sampple-korea`.
- Private repository created: `sampple-korea/chalna-android`.
- Official platform and library documentation reviewed on 2026-08-09.
- Implementation in progress. No build, test, screenshot, APK, or release claim is valid yet.

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

None yet; workflows have not run.

## Design review

Pending first rendered screenshot artifact. Release is prohibited before at least one documented refinement pass.

## Release readiness

Signing source exists outside the repository at the user-provided Desktop location, but alias/certificate metadata and GitHub secrets are not yet verified. Immutable release is not yet enabled. No tag or release exists.

## Verification results

Pending.

## Remaining blockers

None confirmed. Physical-device-only OEM invocation, lock-screen delivery, hardware latency, heat, battery, and high-refresh-rate checks will remain explicitly unverified unless a device becomes available.
