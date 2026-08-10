# Player

`PlayerController` creates Media3 ExoPlayer only after a valid Room capture ID opens Player. The application graph and Activity startup never create a decoder. The local item URI is resolved from the repository; arbitrary external URI/path extras are ignored.

Video output uses `SurfaceView`. Chalna provides Foundation-only controls for play/pause, ±10 seconds, draggable/keyboard-accessible seeking, buffered progress, current/total time, mute, 0.5×–2× speed, fullscreen, share, trash, details, and external open. States distinguish preparing, buffering, ready, ended, playback error, and source missing.

Lifecycle policy:

- background/onStop pauses;
- leaving Player releases the surface and ExoPlayer;
- becoming-noisy pauses;
- actual playback alone keeps the screen on;
- recording start immediately pauses playback;
- playback is rejected while capture is active;
- position is throttled into Room and restored unless near completion.

Fullscreen owns edge-to-edge/system-bar visibility only while active. Back exits fullscreen before leaving Player. Portrait/landscape metadata controls fit without forced cropping or a portrait-only application lock. Chalna adds no media playback service or Internet permission.
