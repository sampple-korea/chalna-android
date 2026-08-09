# UX specification

## Experience principles

Immediate comprehension, explicit consent, visible capture, recoverable failure, and local control. The interface must never imply that selection of Chalna as Assistant grants permission to record automatically.

## Core journey

1. **Setup:** explain what the Assistant gesture will do; request Camera; offer Audio with a separate Microphone explanation; direct the user to default-assistant settings; verify readiness on return.
2. **Ready home:** show “Ready” only when Camera and selected Assistant are confirmed, plus Microphone if audio is enabled. Primary content explains the device gesture; it is not a software capture button.
3. **Invocation/start:** show Starting, then Recording only after CameraX reports start. Provide elapsed time and persistent notification. Announce state to accessibility services.
4. **Stop/finalize:** transition to Stopping, suppress repeated toggles, then show saved-item metadata only after successful finalize.
5. **Failure:** plain-language cause and recovery action; never present a false success. Permission, assistant-selection, camera-in-use, storage/finalize, OEM restriction, and interruption states are distinct.
6. **Library:** list only Chalna-created indexed captures, distinguish Device Gallery from Chalna Vault, and make filtering/selection explicit.
7. **Playback/manage:** play local items in-app; expose share, export, external-open, and confirmed delete actions without implying cloud transfer or Vault encryption.

## Keyguard

An invocation from keyguard may use a minimal show-when-locked surface solely to establish the explicit user-triggered path. It must not request credential bypass, reveal thumbnails/media details, dismiss keyguard, or persist over unrelated screens. OEMs may block or reroute invocation; failure must return to a safe idle/failed state. Locked-device behavior is **Pending verification** per device.

## Controls and accessibility

- Minimum interactive target: 48 × 48 dp; adequate spacing prevents accidental activation.
- Every icon-like control has a localized text label/semantic description; state is not conveyed by color alone.
- Support TalkBack, switch access, keyboard/D-pad where applicable, RTL, font scaling through 200%, display scaling, and portrait/landscape without clipped critical actions.
- Respect system reduced-motion/animator settings. Announce Starting, Recording, Stopping, Saved, and Failed without repetitive live-region chatter.
- Contrast target: WCAG 2.2 AA (4.5:1 normal text, 3:1 large text and essential graphics); measured values are **Pending verification**.
- Destructive deletion requires clear confirmation, identifies the selected local item count/destination, and handles already-missing media without a false success.

## Copy rules

All Korean and English user-visible text lives in Android string resources. Use concrete verbs: “Start recording,” “Stop recording,” “Saved on this device.” “Device Gallery” means MediaStore-visible storage; “Chalna Vault” means app-private local storage and must never be described as encrypted. Never say “secure,” “private,” or “successful” without the qualified behavior/evidence. Permission denial copy explains both consequence and settings recovery.

## UI QA views

Setup missing each requirement; ready day/night; starting; recording at 00:00 and long duration; stopping; saved; every failure; Device Gallery and Vault library states; empty/filtered/selected gallery; player paused/playing/buffering/error; playback glow; custom icon states; delete confirmation; audio disabled; permission denied permanently; 200% font; RTL; TalkBack focus order; compact/expanded widths; keyguard surface. The v1.1 deterministic screenshot matrix was generated and inspected after deliberate refinement; physical TalkBack and OEM keyguard behavior remain in the device plan.
