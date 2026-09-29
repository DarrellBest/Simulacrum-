#requires -Version 5.1
<#
Run the Robot suite against build\libs\simulacrum-all.jar using robot\.venv.
#>
[CmdletBinding()]
param(
    [int]$Port = 17355,
    [int]$BootTimeoutSeconds = 60,
    # seconds to keep the window open after the suite
    [int]$HoldSeconds = 8,
    [switch]$WithGlobe,
    # pause between tests
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
