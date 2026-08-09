# Motion specification

Motion confirms state; it never hides capture status or delays a stop action.

| Transition | Intent | Constraint |
|---|---|---|
| Idle → Starting | acknowledge invocation | brief, non-looping anticipation |
| Starting → Recording | confirm actual start | indicator settles only after CameraX start event |
| Recording | maintain awareness | restrained pulse; static label and timer remain sufficient |
| Recording → Stopping | confirm stop command | immediate label change; stop control disabled against duplicates |
| Stopping → Saved/Idle | confirm finalize result | success only after finalize; failure uses distinct treatment |
| Any → Failed | attract attention once | no continuous alarming animation |

Use opacity, scale, and simple vector/Canvas transforms that avoid layout movement. No Material ripple. Press feedback must be immediate and custom; focus state must remain visible without motion. Avoid flashes, aggressive vibration, parallax, and large continuous movement.

When system animation duration is disabled or reduced, render stable end states immediately and preserve all textual/state feedback. Infinite decorative animation must stop when off-screen, backgrounded, or battery-sensitive. Recording indication may remain active but should be low-cost.

Durations, easing curves, frame pacing, 60/90/120 Hz behavior, reduced-motion behavior, and Compose recomposition cost are **Pending verification** through instrumentation, screenshot/recording review, and physical devices. Motion must not be approved from source inspection alone.
