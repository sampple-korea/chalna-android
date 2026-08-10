# Process model

All production components run in the default application process:

- `ChalnaVoiceInteractionService`
- `ChalnaVoiceInteractionSessionService`
- `ChalnaRecognitionService`
- `CaptureService`
- `MainActivity`
- `ChalnaCaptureTileService`

Earlier releases assigned private `:interactor`, `:session`, and `:recognizer` processes while reading a Kotlin singleton owned by the default process. Android processes do not share singleton memory, so Assistant Glow and recording state could diverge. v1.2.0 removes those process attributes instead of pretending that a `StateFlow` is IPC.

The trade-off is that the selected Voice Interaction service can keep the app process warm. Startup is therefore deliberately small: the application installs the lightweight state bridge, initializes the cached settings snapshot, and creates notification channels. Room, Gallery paging/reconciliation, thumbnail decoding, MediaMetadataRetriever, ExoPlayer, Compose graphics, and Visual Lab are lazy.

The process is not recording authority after death. `CaptureService` is `START_NOT_STICKY`; reboot or crash never restarts capture. The durable attempt journal contains exact output identity and lifecycle stage. A later explicit launch/capture reconciles the journal, salvages a valid playable result, deletes only invalid partial output, and clears stale state/notification.

`CaptureRuntime` remains only as a read-only compatibility bridge installed from the application graph. Capture state can be published only by the repository/actor. UI, notification, Assistant session, and Quick Settings Tile observe the same repository.
