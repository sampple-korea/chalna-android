# Chalna v1.2.0

## Critical fixes

- Preserved the v1.1.1 Assistant-role qualification fix—recognition service, default category, and voice-interaction metadata—and added multi-API role verification so this release cannot regress it.
- Corrected Android 14+ session-ID parsing so the early Assistant callback and the visible session share one platform invocation identity instead of falling back to an anonymous token.
- Unified Voice Interaction, session, recognition, capture, and UI state in the default application process; Assistant feedback no longer reads a process-local stale singleton.
- Serialized capture commands through a single actor with invocation IDs, typed outcomes, Starting cancellation, idempotent stopping, and monotonic timing.
- Hardened CameraX cancellation, finalize validation, output identity, recovery journal, storage checks, and resource release without adding pre-capture or camera warm-up.

## Library and playback

- Migrated the Chalna-created capture index to Room with exported schema, deterministic paging, idempotent legacy import, exact URI/path ownership, and a consumed, bounded recoverable-media reconciliation queue.
- Added destination, favorite, trash, sorting, multi-selection, batch operation, and idempotent Vault-export state.
- Made the local Media3 player lazy and lifecycle-owned, with SurfaceView output, recording-conflict pause, seeking, playback speed, resume position, and source-error handling.
- Added a Quick Settings capture tile that uses the same command authority and respects the lock screen.

## Experience

- Refined Home, Setup, Settings, Gallery, Player, custom controls, adaptive icon masks, and transient Chalna Invocation Glow.
- Kept notification permission optional for recording readiness.
- Production still contains no Diagnostics or Visual Lab surface; the lab remains debug-only.

## Privacy and platform boundaries

- Videos remain local in Device Gallery or Chalna Vault. Vault content is removed with app data unless exported.
- No `INTERNET`, `READ_MEDIA_VIDEO`, broad storage, overlay, Accessibility Service, analytics, upload, or advertising permission/dependency.
- No pre-capture, pre-buffer, camera warm-up, persistent camera binding, or camera/microphone activation before an explicit user trigger.
- Assistant invocation and screen-off behavior still vary by Android version and device manufacturer; Samsung side-button routing may require separate device settings. The Quick Settings tile is the official fallback.

## Installation

Download the signed APK from this private Release and use Android's normal package installer. Existing v1.1.1 installations update in place because package name and signing identity are unchanged. After installation, open Chalna and choose it from the system Assistant role picker.
