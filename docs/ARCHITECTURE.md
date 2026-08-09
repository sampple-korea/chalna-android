# Architecture

## Context

Chalna is a single Android `app` module with explicit seams rather than a dependency-injection framework. The UI uses Compose Runtime/UI/Foundation/Animation/Graphics/Canvas only—no Material 2/3, Google Material Components, Material icons, or Material ripple.

```text
System Assistant gesture / recording-notification action
                 │ explicit command + unique invocation ID
                 ▼
VoiceInteraction service/session or private receiver path
                 ▼
CaptureService (non-exported foreground service)
                 ▼
CaptureCoordinator ── serialized commands / deduplication
                 ▼
CameraX engine ── rear VideoCapture<Recorder> ── selected local destination
                                                ├── Device Gallery (MediaStore/Movies/Chalna)
                                                └── Chalna Vault (app-private files)
                 │
                 ├── notification + haptic + observable UI state
                 └── capture index (Chalna-created metadata only)

Capture index ── gallery/filter/selection ── local Media3 player
                                          └── explicit share/export/delete
```

The voice interaction components are exported only for system binding and protected by `BIND_VOICE_INTERACTION`; the capture service is not exported. Android's Assistant RoleController also requires voice-interaction metadata to name a recognition service. `ChalnaRecognitionService` is protected by `BIND_SPEECH_RECOGNITION_SERVICE`, is not selectable as a standalone recognizer, rejects every recognition request, and never opens the microphone. It exists only to satisfy the platform role contract; Chalna does not listen for speech. The always-resident voice service must stay lightweight. CameraX creation/binding and microphone access belong only in the post-trigger capture path. Gallery browsing and playback of already-saved media cannot call the capture path.

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
| Stopping | finalize success | Idle; update capture index |
| Stopping | error | Failed |
| active transitional/recording after process restart | recovery | Failed/interrupted, never assumed saved |

A coroutine mutex serializes transitions; a bounded invocation-ID set prevents redelivery from toggling twice. Only CameraX finalize success with a usable local content URI/reference constitutes a saved capture and index entry.

## Capture lifecycle and privacy boundary

Before trigger: no provider acquisition, use-case binding, pending recording, audio source, service warm-up, or buffered frames. After trigger: validate readiness; start foreground visibility; acquire CameraX provider; select supported quality; bind rear `VideoCapture`; prepare the selected local output; optionally enable audio; start. On stop/failure: stop/close recording, await finalize, unbind/release camera, update the local index only after success, and stop the foreground service. Gallery browsing and Media3 playback use already-saved content and never bind capture hardware. The exact ordering must be covered by tests and device evidence; v1.1.0 runtime verification is **Pending verification**.

The app declares no `INTERNET` or `READ_MEDIA_VIDEO`; cleartext is disabled; backup is disabled. Device Gallery output crosses into user-visible MediaStore only when selected. Vault content remains app-private unless the user explicitly shares or exports it. Settings and the Chalna-created capture index remain local. Production contains no Diagnostics or VisualLab surface and never records assist data, screen content, unrelated media, app identity, location, or accounts.

Media3 is pinned to stable 1.11.0 and limited to `media3-exoplayer`. No Media3 UI module, streaming extension, datasource network stack, download manager, media session, or ads module is included. Chalna owns its Foundation-only controls; player lifecycle and video surface ownership remain separate from CameraX lifecycle ownership.

## Keyguard and Android restrictions

`VoiceInteractionService.onLaunchVoiceAssistFromKeyguard()` is the platform entry point when supported. A non-exported show-when-locked activity may provide a minimal bridge, but it cannot unlock/dismiss authentication. Camera/microphone are while-in-use permissions; modern Android restricts starting their foreground-service types from the background. Assistant role exemptions and OEM implementations require real-device verification. Do not add boot receivers or persistence as a workaround.

## Build boundaries

`minSdk 29`, `compileSdk 37.1`, `targetSdk 36`, Java 17, AGP 9.3.1, Gradle 9.5.0, Kotlin 2.3.21, Compose BOM 2026.06.00, CameraX 1.6.1, Media3 1.11.0. Current stable Core and Lifecycle require API 37 compilation; target API 36 remains independently pinned until Android 17 behavior changes receive physical-device validation. CI alone builds/tests/packages Android artifacts.

References: [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService), [CameraX architecture](https://developer.android.com/media/camera/camerax/architecture), [video capture](https://developer.android.com/media/camera/camerax/video-capture), [foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [MediaStore](https://developer.android.com/training/data-storage/shared/media), and [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3).
