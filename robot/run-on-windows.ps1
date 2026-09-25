#requires -Version 5.1
<#
Boots the Simulacrum jar with --no-globe (so the WorldWind SwingNode stays out
of the way), waits for the test-control HTTP endpoint, then runs the Robot
Framework smoke suite using the venv at robot\.venv. Exits non-zero if any
step fails. Windows equivalent of run-under-xvfb.sh.
#>
[CmdletBinding()]
param(
    [int]$Port = 17355,
    [int]$BootTimeoutSeconds = 60,
    # Seconds to leave the JavaFX window on screen after the suite finishes,
    # so you can actually see it. Set to 0 in CI.
    [int]$HoldSeconds = 8,
    # Boot with the WorldWind 3D globe enabled. Off by default so CI on
    # software-GL machines stays stable; turn on for demos and recordings.
    [switch]$WithGlobe,
    # Seconds of pause inserted between every test (via Robot's Test Teardown)
    # so a human eye can follow each step. 0 keeps the suite at full speed.
    [double]$PaceSeconds = 0
)

$ErrorActionPreference = 'Stop'

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Root = Resolve-Path (Join-Path $Here '..')
$Jar  = Join-Path $Root 'build\libs\simulacrum-all.jar'
$Venv = Join-Path $Here '.venv'
$Robot = Join-Path $Venv 'Scripts\robot.exe'

if (-not (Test-Path $Jar)) {
    Write-Error "Jar not found at $Jar -- run '.\gradlew.bat shadowJar' first."
}
if (-not (Test-Path $Robot)) {
    Write-Error "robot.exe not found at $Robot -- create the venv with: python -m venv robot\.venv ; robot\.venv\Scripts\python.exe -m pip install -r robot\requirements.txt"
}

$LogDir = Join-Path ([IO.Path]::GetTempPath()) ("simulacrum-robot-" + [Guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $LogDir | Out-Null
$AppLog = Join-Path $LogDir 'app.log'

$JavaArgs = @('-jar', $Jar)
if (-not $WithGlobe) { $JavaArgs += '--no-globe' }
$Mode = if ($WithGlobe) { 'full UI + globe' } else { '--no-globe' }
Write-Host "Launching Simulacrum ($Mode), log: $AppLog"
$app = Start-Process -FilePath 'java' `
    -ArgumentList $JavaArgs `
    -RedirectStandardOutput $AppLog `
    -RedirectStandardError  (Join-Path $LogDir 'app.err.log') `
    -PassThru

try {
    Write-Host "Waiting for test control endpoint on :$Port ..."
    $ready = $false
    for ($i = 0; $i -lt $BootTimeoutSeconds; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
            if ($r.StatusCode -eq 200) { $ready = $true; break }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if (-not $ready) {
        Write-Host "App log:" -ForegroundColor Red
        if (Test-Path $AppLog) { Get-Content $AppLog | Write-Host }
        throw "Test control endpoint never came up on :$Port"
    }

    # Pull the JavaFX window to the foreground so the suite is visible.
    try { Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/ui/focus" -TimeoutSec 2 | Out-Null } catch { }

    $RobotArgs = @('--outputdir', $LogDir)
    if ($PaceSeconds -gt 0) { $RobotArgs += @('--variable', "PACE:${PaceSeconds}s") }
    $RobotArgs += (Join-Path $Here 'smoke.robot')
    & $Robot @RobotArgs
    $rc = $LASTEXITCODE
    Write-Host "Robot output in $LogDir"
    if ($HoldSeconds -gt 0) {
        Write-Host "Holding JavaFX window for $HoldSeconds seconds (override with -HoldSeconds N)..."
        try { Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/ui/focus" -TimeoutSec 2 | Out-Null } catch { }
        Start-Sleep -Seconds $HoldSeconds
    }
    exit $rc
}
finally {
    if ($app -and -not $app.HasExited) {
        Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue
    }
}
