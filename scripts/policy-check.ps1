$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure([string]$message) { $failures.Add($message) }

$manifest = Get-Content -Raw (Join-Path $root "app/src/main/AndroidManifest.xml")
$voiceMetadata = Get-Content -Raw (Join-Path $root "app/src/main/res/xml/voice_interaction_service.xml")
$forbiddenPermissions = @(
  "android.permission.INTERNET",
  "android.permission.READ_MEDIA_VIDEO",
  "android.permission.ACCESS_FINE_LOCATION",
  "android.permission.ACCESS_COARSE_LOCATION",
  "android.permission.READ_CONTACTS",
  "android.permission.WRITE_CONTACTS",
  "android.permission.READ_SMS",
  "android.permission.SEND_SMS",
  "android.permission.READ_CALL_LOG",
  "android.permission.WRITE_CALL_LOG",
  "android.permission.READ_EXTERNAL_STORAGE",
  "android.permission.WRITE_EXTERNAL_STORAGE",
  "android.permission.MANAGE_EXTERNAL_STORAGE",
  "android.permission.SYSTEM_ALERT_WINDOW",
  "android.permission.BIND_ACCESSIBILITY_SERVICE"
)
foreach ($permission in $forbiddenPermissions) {
  if ($manifest.Contains($permission)) { Add-Failure "Forbidden permission: $permission" }
}
if ($manifest -match "BOOT_COMPLETED") { Add-Failure "Boot-triggered capture is forbidden" }
if ($voiceMetadata -notmatch 'android:sessionService="[^\"]+"') {
  Add-Failure "Assistant role metadata is missing sessionService"
}
if ($voiceMetadata -notmatch 'android:recognitionService="[^\"]+"') {
  Add-Failure "Assistant role metadata is missing recognitionService"
}
if ($voiceMetadata -notmatch 'android:supportsAssist="true"') {
  Add-Failure "Assistant role metadata must declare supportsAssist=true"
}
if ($manifest -notmatch 'android:name="\.assistant\.ChalnaRecognitionService"') {
  Add-Failure "Assistant role recognition service component is missing"
}

$gradleText = (Get-ChildItem $root -Recurse -File -Include *.gradle,*.gradle.kts,*.toml | ForEach-Object { Get-Content -Raw $_.FullName }) -join "`n"
$forbiddenDependencies = @(
  "androidx.compose.material:",
  "androidx.compose.material3:",
  "com.google.android.material:",
  "material-icons-core",
  "material-icons-extended",
  "androidx.media3:media3-ui-compose-material3"
)
foreach ($dependency in $forbiddenDependencies) {
  if ($gradleText.Contains($dependency)) { Add-Failure "Forbidden dependency: $dependency" }
}
if ($gradleText -match '(?m)version\s*=\s*"\+"|:[0-9][^"'']*\+[''\"]') { Add-Failure "Dynamic dependency version found" }

$mainSources = Get-ChildItem (Join-Path $root "app/src/main") -Recurse -File -Include *.kt,*.kts,*.java,*.xml
foreach ($file in $mainSources) {
  $text = Get-Content -Raw $file.FullName
  if ($text -match 'androidx\.compose\.material(3)?\.' -or $text -match 'com\.google\.android\.material') {
    Add-Failure "Forbidden Material import/reference: $($file.FullName)"
  }
  if ($file.Extension -in @('.kt', '.java') -and $text -match '(?i)\bTODO\b|\bFIXME\b|lorem ipsum|coming soon') {
    Add-Failure "Unfinished production marker: $($file.FullName)"
  }
  if ($text -match '(?i)\bVisualLab(?:Activity|Screen|Route|Panel)?\b') {
    Add-Failure "Production VisualLab reference is forbidden: $($file.FullName)"
  }
  $diagnosticsUi = $text -match '(?i)\bDiagnostics(?:Activity|Screen|Route|Panel)\b|["'']diagnostics["'']'
  if ($file.Extension -eq '.xml') { $diagnosticsUi = $diagnosticsUi -or $text -match '(?i)>\s*Diagnostics\s*<' }
  if ($diagnosticsUi) {
    Add-Failure "Production Diagnostics UI/component reference is forbidden: $($file.FullName)"
  }
}

$appFiles = Get-ChildItem (Join-Path $root "app/src/main/java") -Recurse -File -ErrorAction SilentlyContinue
foreach ($file in $appFiles) {
  $text = Get-Content -Raw $file.FullName
  if ($file.Name -match 'Application' -and $text -match 'ProcessCameraProvider|bindToLifecycle|prepareRecording') {
    Add-Failure "Camera initialization from Application is forbidden: $($file.FullName)"
  }
  if ($text -match '(?i)circular\s*(video\s*)?buffer|ring\s*buffer|pre[-_ ]?capture|pre[-_ ]?buffer|camera\s*warm[-_ ]?up') {
    Add-Failure "Pre-capture/warm-camera implementation marker: $($file.FullName)"
  }
}

$trackedKeys = git -C $root ls-files '*.jks' '*.keystore' '*.p12' '*.pfx' 'keystore.properties' 'signing.properties'
if ($trackedKeys) { Add-Failure "Signing material is tracked: $trackedKeys" }

$workflowFiles = Get-ChildItem (Join-Path $root ".github/workflows") -File -Include *.yml,*.yaml -ErrorAction SilentlyContinue
foreach ($file in $workflowFiles) {
  foreach ($line in Get-Content $file.FullName) {
    if ($line -match '^\s*uses:\s*([^#\s]+)') {
      $reference = $Matches[1]
      if ($reference -notmatch '@[0-9a-f]{40}$') { Add-Failure "Action not pinned to a full SHA in $($file.Name): $reference" }
    }
  }
}

if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}
Write-Output "Policy checks passed."
