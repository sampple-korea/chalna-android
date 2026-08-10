# Product specification

## Definition

Chalna 1.2.0 is a local-only Android 10+ capture utility. When Chalna is the user-selected system Assistant, an Assistant invocation is an explicit command to toggle a visible, local video recording. The official Quick Settings Tile is an opt-in OEM fallback. Chalna is not a voice assistant, cloud camera, hidden recorder, device-wide gallery scanner, or pre-capture system.

## Goals and success criteria

1. A valid invocation while idle starts rear-camera recording after permission and platform checks.
2. A valid invocation while recording, or the notification stop action, finalizes exactly one item in the selected Device Gallery or Chalna Vault destination.
3. No camera/microphone activity occurs before the trigger; no persistent binding, warm-up, pre-buffer, boot capture, or preview use case exists.
4. Recording is always signaled by foreground notification and clear UI/haptic feedback where permitted.
5. Operation is local: no `INTERNET`, `READ_MEDIA_VIDEO`, analytics, account, background upload, or advertising.
6. The in-app library lists only Chalna-created items from its local index; it does not enumerate unrelated device videos.
7. In-app Media3 playback accepts only indexed local content. Browsing/playback must not enter the camera or microphone capture path.
8. Duplicate invocation IDs and transitional-state commands do not create overlapping recordings.

Quantitative latency, reliability, thermal, power, and OEM coverage targets are **Pending verification** and must be established from device evidence rather than invented thresholds.

## Functional requirements

- Onboarding explains permissions, Assistant selection, local storage, and OEM limitations.
- Readiness reports Camera, optional Microphone, and Assistant-selection status independently.
- Audio defaults on but can be disabled; microphone denial must still allow silent video when audio is off.
- Preferred quality defaults to FHD, with supported-quality fallback. UHD, FHD, HD, and SD are preference choices, not device guarantees.
- Recording file names use `Chalna_yyyyMMdd_HHmmss_SSS.mp4` in UTC.
- Finalization reports success only after CameraX finalize success and a usable `content://` URI.
- Process interruption must not imply a successful capture; recovery surfaces failure/interruption state.
- Settings, help/privacy, capture library, and local playback remain usable without network.
- Device Gallery writes to MediaStore under `Movies/Chalna`; Chalna Vault writes to app-private storage. “Vault” is not an encryption claim.
- Explicit share/export/delete actions operate only on user-selected indexed items.

## Safety invariants

- Entry into capture hot path only from Assistant invocation or recording-notification action. Test-only capture harnesses are excluded from production.
- No production Diagnostics or VisualLab code, navigation, component, or resource.
- Runtime permissions precede restricted resource access.
- Every start has a visible foreground-service lifecycle and a reachable stop path.
- Keyguard handling must not unlock the device, dismiss authentication, expose captured media, or bypass platform restrictions.
- Application UI never claims a recording was saved before finalize success.

## Non-goals

Live preview, streaming, upload, remote control, scheduled/background capture, hotword recognition, screen capture, continuous camera service, editing, sharing automation, multi-camera capture, and iOS support.

## Acceptance evidence

CI logs, policy scans, unit/instrumentation results, deterministic gallery/glow/icon screenshots, Assistant role-assignment smoke, device matrix results, APK metadata/signature/permissions, checksums, and the re-downloaded published release asset are the acceptance evidence.
