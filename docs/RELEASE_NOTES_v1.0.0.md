## What is Chalna

Chalna turns the selected Android Assistant invocation into an immediate local video recording toggle.

## Core features

- Assistant invocation starts and stops a CameraX recording.
- Lock-screen-aware official voice interaction path.
- Rear-camera video with optional audio and stable quality fallback.
- Local MediaStore saving under `Movies/Chalna`.
- Visible foreground notification with a stop action.
- Distinct haptic feedback, settings, diagnostics, and the custom Chalna Aura interface.

## Installation

Download `chalna-v1.0.0-release.apk` from this private repository release and use Android's normal package installation flow. Do not bypass Android security warnings or device policy.

## Setup

Open Chalna, grant the required capture and notification permissions, and select Chalna as the system's default digital assistant. This replaces the invocation that would otherwise open Gemini or another assistant; it can be reverted in system settings.

## Privacy

- Recordings remain local to the device.
- The app has no network permission.
- There is no pre-capture, camera warm-up, circular buffer, or hidden recording.
- Recording remains visible through Android privacy indicators and a foreground notification.

## Known platform limitations

Assistant gestures, hardware buttons, locked-screen delivery, and screen-off delivery vary by Android version and device manufacturer. Emulator validation does not establish physical camera latency or OEM invocation compatibility; follow `docs/DEVICE_TEST_PLAN.md` for device checks.

