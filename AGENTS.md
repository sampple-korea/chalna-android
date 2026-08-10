# Chalna repository rules

- Never run Android builds, Gradle dependency resolution, Android tests, emulator tasks, or APK packaging on the local PC. GitHub Actions is the source of truth.
- Build the UI from Compose Runtime, UI, Foundation, Animation, Graphics, and Canvas. Material 2, Material 3, Google Material Components, Material icons, and Material ripple are forbidden.
- Never implement pre-capture, pre-buffering, persistent camera binding, camera warm-up, boot capture, or any camera/microphone activity before an explicit user trigger.
- Never request `INTERNET`, broad storage, location, contacts, SMS, call-log, overlay, accessibility-service, or other unrelated permissions.
- Prefer current official Android, AndroidX, CameraX, Gradle, Kotlin, and GitHub documentation.
- The capture hot path begins only after an Assistant invocation, notification action, or explicit diagnostics test confirmation.
- Do not claim completion, test success, signing success, or release status without inspecting the corresponding CI logs, APK metadata, signatures, checksums, GitHub API state, and downloaded release asset.
- Preserve unrelated user work. Never force-push or destructively reset.
- Keep all user-visible Korean and English strings in resources.
- Keep VoiceInteractionService, VoiceInteractionSessionService, RecognitionService, CaptureService, and the authoritative capture state in the default process unless a documented, tested IPC architecture replaces it.
- Use epoch clocks only for user dates and filenames. Use an injected monotonic clock for duration, ordering, timers, latency, and guards.
- A valid finalized video must never be deleted merely because metadata/index persistence failed; journal and reconcile it.
- Gallery ownership comes only from exact Chalna capture identities. Never import by scanning `Movies/Chalna`, a filename prefix, or the user's media library.
- Player, Gallery reconciliation, thumbnail decoding, Room bulk queries, and graphics tooling must be lazy and excluded from the Assistant capture hot path.
