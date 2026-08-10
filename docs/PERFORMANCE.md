# Performance

The capture hot path is `Assistant callback → typed dispatcher → actor enqueue → CaptureService`. It performs no Gallery query, Room migration, thumbnail decode, metadata extraction, player initialization, networking, or Compose wait.

Application initialization is limited to the state authority, cached settings, and notification channels. Room/Gallery are lazy; ExoPlayer is Player-screen scoped. Recording has a local monotonic ticker only while elapsed time is visible; Player ticks only while visible/playing. Idle Home, Settings, and Gallery have no app-wide one-second loop.

Invocation Glow caches path geometry, palettes, gradients, matrices, and draw primitives at activation/size change. Its draw phase mutates cached objects and does not allocate arrays, paths, lists, or bitmaps per frame. Animation stops on hide/destroy/background/reduced motion/power saver and never blocks dispatch.

Thumbnail memory is bounded by an LRU and responds to trim-memory. Decode jobs are cancellable and target cell size. MediaRetriever/Extractor and player resources use bounded lifetimes.

The benchmark module records cold/warm startup, initial display, Gallery transition/scroll, Player transition, and deterministic Glow work, then uploads JSON/traces and a Baseline Profile candidate. Physical Assistant-to-CameraX P50/P95 and 60/120 Hz device behavior are not inferred from emulator results.

Release validates 16 KB APK alignment and every native ELF LOAD segment, verifies the AAB `PAGE_ALIGNMENT_16K` configuration, then installs/launches on the API 35 `google_apis_ps16k` image.
