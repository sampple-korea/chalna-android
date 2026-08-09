# Device test plan

No physical-device result is currently claimed. Every result below begins **Pending verification** and requires device model, region/firmware, Android build, app commit/artifact SHA-256, tester, date, evidence link, and notes.

## Minimum matrix

| Class | Representative coverage | Result |
|---|---|---|
| API 29 | AOSP-like + one major OEM | Pending verification |
| API 30–32 | at least two OEM families | Pending verification |
| API 33 | notification permission granted/denied | Pending verification |
| API 34 | while-in-use camera/mic FGS rules | Pending verification |
| API 35 | keyguard and Assistant gesture | Pending verification |
| API 36 | reference device + one non-Google OEM | Pending verification |
| Foldable/tablet | compact/expanded and rotation | Pending verification |
| Low/mid/high tier | encoder/thermal/resource variance | Pending verification |

## Per-device procedure

1. Record clean install and APK identity/signature/hash. Confirm declared permissions exclude `INTERNET`, `READ_MEDIA_VIDEO`, broad storage, and unrelated permissions.
2. Deny every runtime permission; verify no camera/microphone indicator or service. Grant Camera only; test silent capture with audio off. Grant Microphone and test audio on. Test notification denial where applicable.
3. Before selecting Chalna as Assistant, reboot, background, force-stop, unlock/lock, and watch privacy indicators/logs: no pre-capture, binding, warm-up, boot capture, or microphone activity.
4. Select Chalna through system settings. Test every supported invocation gesture while unlocked, app foreground/background, screen off, keyguard without authentication bypass, immediately after reboot/unlock, and under OEM battery modes.
5. Start/stop 20 repeated short recordings; rapid duplicate invocations; stop notification; concurrent camera app; incoming call/audio focus; rotation/fold; low storage; revoked permission during/after capture; process kill; camera privacy toggle.
6. Record to Device Gallery and validate the MediaStore item under `Movies/Chalna`: playable, expected orientation, audio policy, duration, timestamp/name, no orphan pending item, and correct failure messaging. Confirm Chalna does not enumerate unrelated device videos.
7. Record to Chalna Vault and validate app-private placement, in-app playback, explicit share/export grants, delete behavior, clear-data/uninstall behavior, and the absence of encryption claims. Never upload media as evidence unless it contains no personal content and access is restricted.
8. Run 15/30/60-minute captures where storage permits. Record temperature, battery delta, frame/encoder/finalize errors, notification continuity, and app responsiveness; do not infer pass thresholds until product owners approve measured baselines.
9. Test TalkBack, switch/keyboard navigation, 200% font, display size, RTL, day/night, 60/90/120 Hz, reduced animations, and landscape/multi-window.

## Exit criteria

All supported API rows and at least the named major OEM families have evidence; no pre-trigger sensor use; no hidden/overlapping capture; reliable visible stop path; no false saved state; privacy/security findings resolved; limitations documented. Any missing row remains **Pending verification**, not “not applicable,” unless product scope explicitly excludes it.
