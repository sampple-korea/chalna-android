# Capture state machine

The single-consumer `CaptureCommandActor` is the only component that advances recording state. External triggers submit a `CaptureRequest`; they do not inspect state and call CameraX directly.

| State | Accepted command | Result |
|---|---|---|
| Idle / Failed / Saved | Start or Toggle | snapshot settings, run preflight, enter StartRequested |
| StartRequested / StartingForeground / OpeningCamera / StartingRecorder | Toggle, Stop, CancelStart | enter CancelRequested and cancel the structured start job |
| Recording | Toggle, Stop, NotificationStop, AutoStop | StopRequested → StoppingRecorder → Finalizing |
| CancelRequested / StopRequested / StoppingRecorder / Finalizing | Stop | idempotent no-op |
| Persisting / Recovering | Toggle | Busy; never queue a future start |
| Finalize validated | internal Persist | Persisting → Saved; metadata failure marks MetadataPending without deleting media |
| Operation failure | internal failure | Failed with a typed code, followed by service/resource cleanup |

`CaptureStateRepository` publishes the authoritative state and retains bounded invocation receipts. The dispatcher atomically resolves Toggle from that state, reserves each invocation ID once, and returns a typed result. A duplicate callback returns `Duplicate`; a distinct invocation is never discarded solely because it arrived inside a debounce window.

Epoch time is limited to display names and capture dates. `SystemClock.elapsedRealtimeNanos()` or an injected `MonotonicClock` drives elapsed time, ordering, auto-stop, latency, and the short post-finalize guard. `CancellationException` is rethrown at coroutine boundaries.

Starting cancellation has two outcomes: an unstarted recorder releases camera/output and returns Idle, while a recorder that crossed the start boundary performs a normal stop/finalize and preserves the short usable capture. Saving never schedules a delayed recording.

Verification lives in `CaptureCoordinatorTest`, `CaptureStateRepositoryTest`, installed Assistant-role tests, and the process/recovery instrumentation plan. Camera hardware ordering remains a physical-device check.
