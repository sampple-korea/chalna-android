#!/usr/bin/env bash
set -euo pipefail

root="${1:-ui-screenshots}"
required=(
  home-ready-mist.png home-recording-night.png setup-mist.png settings-night.png
  gallery-night.png gallery-selection-night.png gallery-empty-night.png
  gallery-vault-filter-night.png gallery-vault-selection-night.png
  gallery-delete-confirmation-night.png gallery-mist.png
  player-night.png player-controls-hidden-night.png player-info-night.png
  player-delete-confirmation-night.png player-vault-night.png
  settings-camera-denied-night.png settings-audio-off-night.png
  settings-assistant-missing-night.png settings-notification-optional-night.png settings-vault-mist.png
  glow-start-000-night.png glow-start-016-night.png glow-start-034-night.png
  glow-start-050-night.png glow-start-078-night.png glow-stop-050-mist.png
  glow-error-050-night.png glow-start-tall-colorful-night.png glow-start-compact-rounded-night.png
  glow-stop-wide-cutout-mist.png icon-mask-circle.png icon-mask-squircle.png icon-mask-square.png
  icon-mask-teardrop.png icon-mask-monochrome.png home-large-font-mist.png gallery-large-font-night.png
  settings-large-font-mist.png
)

for screenshot in "${required[@]}"; do
  if ! find "$root" -type f -name "$screenshot" -size +0c -print -quit | grep -q .; then
    echo "Required screenshot artifact is missing or empty: $screenshot" >&2
    exit 1
  fi
done

echo "Verified ${#required[@]} required UI artifacts."
