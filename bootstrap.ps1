#requires -Version 5.1
<#
Simulacrum bootstrap (Windows / PowerShell).

Unpack the source zip, then run this script. It will:
  1. Verify JDK 21 is on PATH
  2. Build the shadow jar with the bundled Gradle wrapper
  3. Create the Robot Framework venv at robot\.venv
  4. Run the Robot Framework smoke suite (17 tests)
  5. Launch the JavaFX desktop app

Re-runnable. The Gradle build is incremental, the venv setup is idempotent,
and re-running just refreshes whatever changed.

Usage:
    .\bootstrap.ps1                 # full path: build + venv + tests + launch
    .\bootstrap.ps1 -NoTests        # skip the Robot suite
    .\bootstrap.ps1 -NoLaunch       # skip the GUI launch (CI mode)
    .\bootstrap.ps1 -NoTests -NoLaunch   # build the jar only
#>
[CmdletBinding()]
param(
    [switch]$NoTests,
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $Root
try {
    Write-Host "==> Checking JDK 21"
    try {
        $verLine = (& java -version 2>&1 | Select-Object -First 1).ToString()
    } catch {
        throw "Java not found on PATH. Install JDK 21 (e.g. Microsoft OpenJDK 21) and re-run."
    }
    Write-Host "    $verLine"
    if ($verLine -notmatch '"21\.' -and $verLine -notmatch 'version 21') {
        Write-Warning "Detected Java version does not look like 21. The Gradle toolchain will download JDK 21 if needed."
    }

    Write-Host "==> Building shadow jar (.\gradlew.bat shadowJar)"
    & .\gradlew.bat shadowJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }
    $Jar = Join-Path $Root 'build\libs\simulacrum-all.jar'
    if (-not (Test-Path $Jar)) { throw "Build succeeded but jar missing at $Jar" }
    Write-Host "    Built: $Jar  ($([math]::Round((Get-Item $Jar).Length/1MB,1)) MB)"

    if (-not $NoTests) {
        Write-Host "==> Setting up Robot Framework venv at robot\.venv"
        & (Join-Path $Root 'robot\setup-venv.ps1')
        if ($LASTEXITCODE -ne 0) { throw "venv setup failed (exit $LASTEXITCODE)" }

        Write-Host "==> Running Robot Framework smoke suite"
        & (Join-Path $Root 'robot\run-on-windows.ps1') -HoldSeconds 0
        if ($LASTEXITCODE -ne 0) { throw "Robot suite failed (exit $LASTEXITCODE)" }
    } else {
        Write-Host "==> Skipping Robot Framework suite (-NoTests)"
    }

    if (-not $NoLaunch) {
        Write-Host "==> Launching Simulacrum (close the window to exit)"
        & java -jar $Jar
    } else {
        Write-Host ""
        Write-Host "Bootstrap complete. Launch the app manually with:"
        Write-Host "    java -jar `"$Jar`""
    }
}
finally {
    Pop-Location
}
