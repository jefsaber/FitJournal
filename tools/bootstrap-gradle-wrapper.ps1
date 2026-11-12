# Fetch the OFFICIAL Gradle 8.13 wrapper files from the pinned Gradle v8.13.0 release.
# Run from PowerShell: powershell -ExecutionPolicy Bypass -File tools\bootstrap-gradle-wrapper.ps1
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$base = "https://raw.githubusercontent.com/gradle/gradle/v8.13.0"
New-Item -ItemType Directory -Force -Path (Join-Path $root "gradle\wrapper") | Out-Null
$files = @(
  @{Url="$base/gradlew"; Target="gradlew"},
  @{Url="$base/gradlew.bat"; Target="gradlew.bat"},
  @{Url="$base/gradle/wrapper/gradle-wrapper.jar"; Target="gradle\wrapper\gradle-wrapper.jar"}
)
foreach ($file in $files) {
  $target = Join-Path $root $file.Target
  Invoke-WebRequest -Uri $file.Url -OutFile $target
  Write-Host "Fetched $($file.Target)"
}
Write-Host "Official Gradle 8.13 wrapper is ready. Open FitJournal in Android Studio and sync Gradle."
