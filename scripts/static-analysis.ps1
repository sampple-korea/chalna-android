$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$sourceRoot = Join-Path $root "app/src/main/java"
$failures = [System.Collections.Generic.List[string]]::new()
if (Test-Path $sourceRoot) {
  foreach ($file in Get-ChildItem $sourceRoot -Recurse -File -Include *.kt,*.java) {
    $text = Get-Content -Raw $file.FullName
    $checks = [ordered]@{
      '\bGlobalScope\b' = 'GlobalScope bypasses lifecycle ownership'
      '\brunBlocking\s*\(' = 'runBlocking is forbidden in production Android code'
      '\bThread\.sleep\s*\(' = 'Thread.sleep blocks a thread'
      '\bprintStackTrace\s*\(' = 'Raw stack traces may expose implementation details'
      '\bSystem\.(out|err)\.' = 'Direct console logging is forbidden'
      '!!' = 'Non-null assertion requires explicit error handling'
    }
    foreach ($entry in $checks.GetEnumerator()) {
      if ($text -match $entry.Key) { $failures.Add("$($entry.Value): $($file.FullName)") }
    }
  }
}
if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}
Write-Output "Static source checks passed."

