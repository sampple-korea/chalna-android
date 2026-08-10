# Architecture

Chalna is a single production `app` module plus an Android test-only `benchmark` module. Production UI uses Compose Runtime/UI/Foundation/Animation/Graphics and custom Canvas/vector controls; Material 2/3, Google Material Components, Material icons, and Material player UI are absent.

```text
Assistant / Quick Tile / notification / explicit UI test
                │ invocation ID + typed command
                ▼
CaptureCommandDispatcher ── dedupe receipt / preflight
                │
                ▼
CaptureService ── CaptureCommandActor ── authoritative CaptureStateRepository
                │                              │
                │                              ├── Assistant Glow / Home
                │                              ├── notification + haptic
                │                              └── Quick Tile
                ▼
CameraX rear VideoCapture<Recorder>
                │
                ├── MediaStore / Movies/Chalna
                └── app-specific Chalna Vault
                │ exact output + attempt journal
                ▼
validation ── Room transaction / reconciliation ── Paging Gallery ── lazy Media3 Player
```

## Process and invocation

Voice Interaction service/session, the qualification-only recognition service, CaptureService, Tile, and UI run in the default process. This fixes the earlier invalid assumption that Kotlin singleton/StateFlow data crosses Android process boundaries. The voice service remains lightweight: no Room query, Gallery scan, thumbnail work, player, decoder, or graphics initialization occurs from service/application startup.

On API 34+, `onPrepareToShowSession` dispatches early. `AssistantInvocationRegistry` associates the official show-session ID with exactly one typed result; Session `onShow` consumes it and only falls back to dispatch if preparation did not. Older releases use an official invocation-time value when present and otherwise a fresh opaque token. Session view failure/timeout never cancels an already accepted recording command.

The recognition service exists only for Android Assistant role qualification. It publishes the RecognitionService action, `CATEGORY_DEFAULT`, BIND permission, and settings metadata, while rejecting speech sessions without opening the microphone. Chalna never listens to voice queries. Assist/screenshot context is disabled and ignored.

## Capture authority

The command actor is the only state writer and CameraX owner. Toggle becomes Start, CancelStart, Stop, or Busy atomically from authoritative state. Settings are snapshotted once per capture; later changes affect only the next recording. Notification STOP without an active capture is a no-op that clears stale foreground state instead of starting a camera service.

Detailed transitions are in [CAPTURE_STATE_MACHINE.md](CAPTURE_STATE_MACHINE.md). Camera operations begin only after an accepted explicit trigger. No provider acquisition, bind, recorder preparation, audio source, circular buffer, pre-capture, or warm session exists before it.

## Media safety

The destination prepares an exact MediaStore URI or canonical Vault path and records it in a durable attempt journal before recording. CameraX Status supplies monotonic duration/bytes. Finalize is followed by existence, positive size/duration, MIME, and metadata validation. Valid media remains saved if Room persistence fails; a pending operation reconciles it later. Recovery salvages playable output and deletes only invalid partial data.

Room is the metadata/index store with WAL, unique IDs, Paging queries, playback state, pending operations, exported schemas, and explicit migrations. The legacy AtomicFile index is imported idempotently and retained as a temporary backup. Files remain the source of truth; Gallery verifies addressed/bounded batches without scanning the user's library or requesting `READ_MEDIA_VIDEO`.

## Player and UI

Gallery and thumbnails initialize on demand. Player resolves an opaque Room ID, creates a local-only ExoPlayer/SurfaceView when the screen enters, pauses for recording/background/noisy audio, and releases on exit. MainActivity accepts only a known open-capture action and never trusts an arbitrary URI or path.

Window insets are screen-owned; Player fullscreen controls system bars independently. Decorative Aura/Glow stops for background, reduced motion, and power saver. Production Diagnostics is absent; debug-only Visual Lab supplies deterministic screenshots and state/Glow/icon fixtures.

## Platform and build boundaries

`minSdk 29`, `compileSdk 37.1`, `targetSdk 36`, Java 17, AGP 9.3.1, Gradle 9.5.0, Kotlin 2.3.21, CameraX 1.6.1, Media3 1.11.0, Room 2.8.4, Paging 3.5.0. Build/test/package/emulator work runs only on GitHub Actions.

The release pipeline produces signer-continuous APK and AAB artifacts, verifies 16 KB page alignment, forbidden permissions/components/dependencies, Assistant role qualification, clean/update install, hashes, signatures, package/version, SBOM, immutable Release identity, and re-downloaded assets.

Official references: [VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService), [CameraX video](https://developer.android.com/media/camera/camerax/video-capture), [foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types), [Room](https://developer.android.com/jetpack/androidx/releases/room), [Paging](https://developer.android.com/jetpack/androidx/releases/paging), [MediaStore](https://developer.android.com/training/data-storage/shared/media), and [Media3](https://developer.android.com/jetpack/androidx/releases/media3).
