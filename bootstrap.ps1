#requires -Version 5.1
<#
Build the shadow jar, set up robot\.venv, run the Robot suite, launch the app.
Usage: .\bootstrap.ps1 [-NoTests] [-NoLaunch]
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
        Write-Warning "Java is not 21."
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
