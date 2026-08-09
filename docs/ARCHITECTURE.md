# Architecture

## Context

Chalna is a single Android `app` module with explicit seams rather than a dependency-injection framework. The UI uses Compose Runtime/UI/Foundation/Animation/Graphics/Canvas only—no Material 2/3, Google Material Components, Material icons, or Material ripple.

```text
System Assistant gesture / notification stop / confirmed diagnostic
                 │ explicit command + unique invocation ID
                 ▼
VoiceInteraction service/session or private receiver path
                 ▼
CaptureService (non-exported foreground service)
                 ▼
CaptureCoordinator ── serialized commands / deduplication
                 ▼
CameraX engine ── rear VideoCapture<Recorder> ── MediaStore
                 │
                 ├── notification + haptic + observable UI state
                 └── bounded timing diagnostics (no media/context)
```

The voice interaction components are exported only for system binding and protected by `BIND_VOICE_INTERACTION`; the capture service is not exported. The always-resident voice service must stay lightweight. CameraX creation/binding and microphone access belong only in the post-trigger capture path.

## State machine

| Current | Command | Next/action |
|---|---|---|
| Idle | unique Toggle | Starting; call engine start |
| Failed | unique Toggle | Starting; retry |
| Recording | unique Toggle or Stop | Stopping; call engine stop |
| Starting / Stopping | any command | No overlapping action |
| any | duplicate/blank invocation ID | Ignore |
| Starting | engine start success | Recording |
| Starting | error | Failed |
| Stopping | finalize success | Idle; update last capture |
| Stopping | error | Failed |
| active transitional/recording after process restart | recovery | Failed/interrupted, never assumed saved |

A coroutine mutex serializes transitions; a bounded invocation-ID set prevents redelivery from toggling twice. Only CameraX finalize success with a usable MediaStore URI constitutes a saved capture.

## Capture lifecycle and privacy boundary

Before trigger: no provider acquisition, use-case binding, pending recording, audio source, service warm-up, or buffered frames. After trigger: validate readiness; start foreground visibility; acquire CameraX provider; select supported quality; bind rear `VideoCapture`; prepare MediaStore output; optionally enable audio; start. On stop/failure: stop/close recording, await finalize, unbind/release camera, update state, and stop foreground service. The exact ordering must be covered by tests and device evidence; runtime verification is **Pending verification**.

The app declares no `INTERNET`; cleartext is disabled; backup is disabled. Media crosses the app boundary only into user-visible MediaStore. Settings remain local. Diagnostics hold at most bounded names/timestamps/durations and must never record assist data, screen content, media, app identity, location, or accounts.

## Keyguard and Android restrictions

`VoiceInteractionService.onLaunchVoiceAssistFromKeyguard()` is the platform entry point when supported. A non-exported show-when-locked activity may provide a minimal bridge, but it cannot unlock/dismiss authentication. Camera/microphone are while-in-use permissions; modern Android restricts starting their foreground-service types from the background. Assistant role exemptions and OEM implementations require real-device verification. Do not add boot receivers or persistence as a workaround.

## Build boundaries

`minSdk 29`, `compileSdk 37.1`, `targetSdk 36`, Java 17, AGP 9.3.1, Gradle 9.5.0, Kotlin 2.3.21, Compose BOM 2026.06.00, CameraX 1.6.1. Current stable Core and Lifecycle require API 37 compilation; target API 36 remains independently pinned until Android 17 behavior changes receive physical-device validation. CI alone builds/tests/packages Android artifacts.

References: [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService), [CameraX architecture](https://developer.android.com/media/camera/camerax/architecture), [video capture](https://developer.android.com/media/camera/camerax/video-capture), [foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types), and [MediaStore](https://developer.android.com/training/data-storage/shared/media).
