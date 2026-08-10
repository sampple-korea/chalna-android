# Changelog

This file records repository changes, not unverified release claims. Dates use ISO 8601.

## [1.2.0] - 2026-08-10

### Fixed

- Restored actual Assistant-role picker eligibility by adding the required default recognition-service category and selectable metadata contract.
- Removed the process-local state split between Voice Interaction/session/recognition and CaptureService/UI.
- Serialized capture commands, preserved coroutine cancellation, handled Starting cancellation and Saving busy state, and moved duration/latency/guards to a monotonic clock.
- Validated CameraX finalize output, retained usable media when metadata persistence fails, and salvaged valid journaled output after process death.
- Removed eager ExoPlayer construction, app-wide one-second ticking, whole-index Gallery refreshes, and per-frame Glow palette/path allocations.

### Added

- Room-backed capture metadata with Paging queries, exported schema, idempotent legacy AtomicFile import, pending reconciliation, favorites, trash, playback positions, and export relationships.
- Recently Deleted, restore/permanent purge, deterministic sorting/filtering, typed batch outcomes, and idempotent Vault-to-MediaStore export.
- Screen-scoped SurfaceView Media3 player lifecycle, recording-conflict pause, buffered seek, speed, resume position, missing/error state, and fullscreen ownership.
- Official Quick Settings capture Tile using the same authoritative command pipeline.
- StrictMode debug checks, baseline-profile/macrobenchmark module, CodeQL/security/SBOM workflow, 16 KB release validation, AAB distribution, update-install verification, and remote APK/AAB integrity checks.
- Process, state-machine, data, migration, storage, Gallery, Player, performance, accessibility, threat-model, and ADR documentation.

### Changed

- Refined Home, Setup, Settings, Gallery, Player, adaptive icon masks, and transient tangent-aligned Chalna Invocation Glow.
- Kept notification permission optional while preserving required foreground-service notification calls.
- Version metadata now comes from `version.properties`; package and release signing identity remain unchanged.

## [1.1.1] - 2026-08-10

### Fixed

- Restored Assistant-role qualification by publishing the required non-empty `recognitionService` metadata and a system-bound recognition-service component that rejects speech requests without activating audio.
- Restored the VoiceInteractionSession service's dedicated process.
- Added policy, instrumentation, API 34 role-assignment, and release-smoke checks that fail when Android no longer accepts Chalna as an Assistant role holder.

## [1.1.0] - 2026-08-10

### Added

- Chalna-created capture library with filtering, selection, share/export/delete actions, and local in-app playback.
- Explicit Device Gallery and Chalna Vault destinations. Device Gallery uses MediaStore; Vault uses app-private storage and does not imply encryption.
- Stable AndroidX Media3 1.11.0 ExoPlayer with Chalna-owned Foundation controls for local playback.
- Gallery, player-glow, and custom-icon screenshot coverage plus archived JVM test XML.
- Direct runtime-permission, Assistant-role, app-settings, and notification-settings actions with automatic resume refresh.
- Galaxy-squircle-safe adaptive icon previews and a debug-only Visual Lab excluded from release artifacts.

### Changed

- Package remains `app.chalna.capture`; version code advances from 1 to 2.
- Production Diagnostics and VisualLab surfaces are removed.
- Home is reduced to capture state, Gallery, Settings, and an optional last-capture preview; Settings are consolidated into Capture, Experience, System, and App sections.
- Notification permission is optional for readiness and capture; Android's required foreground-service notification API remains in use.
- Invocation feedback now uses transient cached three-layer perimeter optics, while recording state uses only the central capture instrument instead of a persistent edge border.
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
