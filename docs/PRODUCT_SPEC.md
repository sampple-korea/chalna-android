# Product specification

## Definition

Chalna 1.0.0 is a private Android 10+ capture utility. When Chalna is the user-selected system Assistant, an Assistant invocation is an explicit command to toggle a visible, local video recording. It is not a voice assistant, cloud camera, hidden recorder, or pre-capture system.

## Goals and success criteria

1. A valid invocation while idle starts rear-camera recording after permission and platform checks.
2. A valid invocation while recording, or the notification stop action, finalizes exactly one MediaStore item.
3. No camera/microphone activity occurs before the trigger; no persistent binding, warm-up, pre-buffer, boot capture, or preview use case exists.
4. Recording is always signaled by foreground notification and clear UI/haptic feedback where permitted.
5. Operation is local: no `INTERNET`, analytics, account, upload, or advertising.
6. Duplicate invocation IDs and transitional-state commands do not create overlapping recordings.

Quantitative latency, reliability, thermal, power, and OEM coverage targets are **Pending verification** and must be established from device evidence rather than invented thresholds.

## Functional requirements

- Onboarding explains permissions, Assistant selection, local storage, and OEM limitations.
- Readiness reports Camera, optional Microphone, and Assistant-selection status independently.
- Audio defaults on but can be disabled; microphone denial must still allow silent video when audio is off.
- Preferred quality defaults to FHD, with supported-quality fallback. UHD, FHD, HD, and SD are preference choices, not device guarantees.
- Recording file names use `Chalna_yyyyMMdd_HHmmss_SSS.mp4` in UTC.
- Finalization reports success only after CameraX finalize success and a usable `content://` URI.
- Process interruption must not imply a successful capture; recovery surfaces failure/interruption state.
- Settings, help/privacy, and bounded diagnostics remain usable without network.

## Safety invariants

- Entry into capture hot path only from Assistant invocation, notification action, or an explicit diagnostics confirmation.
- Runtime permissions precede restricted resource access.
- Every start has a visible foreground-service lifecycle and a reachable stop path.
- Keyguard handling must not unlock the device, dismiss authentication, expose captured media, or bypass platform restrictions.
- Application UI never claims a recording was saved before finalize success.

## Non-goals

Live preview, streaming, upload, remote control, scheduled/background capture, hotword recognition, screen capture, continuous camera service, editing, sharing automation, multi-camera capture, and iOS support.

## Acceptance evidence

CI logs, policy scans, unit/instrumentation results, deterministic screenshots, device matrix results, APK metadata/signature/permissions, checksums, and the re-downloaded private release asset. Until captured, all acceptance is **Pending verification**.
