$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$extensions = @(".kt", ".kts", ".xml", ".md", ".yml", ".yaml", ".toml", ".properties", ".pro", ".ps1")
$failures = [System.Collections.Generic.List[string]]::new()
$files = git -C $root ls-files | ForEach-Object { Join-Path $root $_ } | Where-Object { $extensions -contains [IO.Path]::GetExtension($_) }
foreach ($file in $files) {
  if (-not (Test-Path $file)) { continue }
  $bytes = [IO.File]::ReadAllBytes($file)
  if ($bytes.Length -eq 0) { continue }
  $text = [Text.Encoding]::UTF8.GetString($bytes)
  if ($text -match '(?m)[ \t]+$') { $failures.Add("Trailing whitespace: $file") }
  if (-not $text.EndsWith("`n")) { $failures.Add("Missing final newline: $file") }
  if ($text.Contains("`r`r`n")) { $failures.Add("Malformed line endings: $file") }
}
if ($failures.Count -gt 0) {
  $failures | ForEach-Object { Write-Error $_ }
  exit 1
}
Write-Output "Formatting checks passed."

