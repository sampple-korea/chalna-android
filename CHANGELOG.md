# Changelog

This file records repository changes, not unverified release claims. Dates use ISO 8601.

## [1.1.0] - Unreleased

### Added

- Chalna-created capture library with filtering, selection, share/export/delete actions, and local in-app playback.
- Explicit Device Gallery and Chalna Vault destinations. Device Gallery uses MediaStore; Vault uses app-private storage and does not imply encryption.
- Stable AndroidX Media3 1.11.0 ExoPlayer and non-Material Compose UI support for local playback.
- Gallery, player-glow, and custom-icon screenshot coverage plus archived JVM test XML.

### Changed

- Package remains `app.chalna.capture`; version code advances from 1 to 2.
- Production Diagnostics and VisualLab surfaces are removed.
- Policy rejects `READ_MEDIA_VIDEO`, Media3 Material 3 UI, production Diagnostics/VisualLab, and the existing Material/network violations.
- Release verification pins signer continuity to v1.0.0 and retries bounded immutable-attestation propagation before failing.

## [1.0.0]

### Added

- Explicit Assistant-invocation start/stop recording through Voice Interaction services and a CameraX foreground service.
- Rear-camera Recorder capture to `Movies/Chalna`, optional audio, quality fallback, auto-stop, last-capture opening, notifications, and distinct haptics.
- Five-step permission/Assistant setup, Home state instrument, capture/appearance settings, diagnostics, compatibility help, and privacy information in Korean and English.
- Foundation-only Chalna design system with Night/Mist themes, angular Aura, blur, fluid fields, Edge Pulse, custom controls/icons, reduced motion, and Noto Sans Korean variable typography.
- Unit, instrumentation, deterministic screenshot, policy, lint, static-analysis, signing, APK-inspection, immutable-release, and remote-integrity workflows.

### Verified

- Android CI run 31311630513: 13 JVM tests, zero failures; lint/static/policy/formatting and debug APK passed.
- UI QA run 31311630522: 18 instrumentation tests plus a separately asserted 15-screenshot pass; API 34 install/launch smoke passed.
- Physical OEM Assistant invocation, locked screen-off delivery, camera latency, thermal/battery, and 90/120 Hz behavior remain explicitly device-only validation.
