# Performance

The capture hot path is `Assistant callback → typed dispatcher → actor enqueue → CaptureService`. It performs no Gallery query, Room migration, thumbnail decode, metadata extraction, player initialization, networking, or Compose wait.

Application initialization is limited to the state authority, cached settings, and notification channels. Room/Gallery are lazy; ExoPlayer is Player-screen scoped. Recording has a local monotonic ticker only while elapsed time is visible; Player ticks only while visible/playing. Idle Home, Settings, and Gallery have no app-wide one-second loop.

Invocation Glow caches path geometry, palettes, gradients, matrices, and draw primitives at activation/size change. Its draw phase mutates cached objects and does not allocate arrays, paths, lists, or bitmaps per frame. Animation stops on hide/destroy/background/reduced motion/power saver and never blocks dispatch.

Thumbnail memory is bounded by an LRU and responds to trim-memory. Decode jobs are cancellable and target cell size. MediaRetriever/Extractor and player resources use bounded lifetimes.

The benchmark module currently records cold/warm startup and time to initial display, then uploads JSON, Perfetto traces, and a package-filtered Baseline Profile candidate. Gallery, Player, and deterministic Glow paths are covered by instrumentation and screenshot regression, but this release does not claim separate numeric Gallery-first-content, Player-first-frame, or Glow frame-time benchmarks. Physical Assistant-to-CameraX P50/P95 and 60/120 Hz device behavior are not inferred from emulator results.

Release validates 16 KB APK alignment and every native ELF LOAD segment, verifies the AAB `PAGE_ALIGNMENT_16K` configuration, then installs/launches on the API 35 `google_apis_ps16k` image.
