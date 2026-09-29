#requires -Version 5.1
<#
Create robot\.venv and install robot\requirements.txt. -Python picks the interpreter.
#>
[CmdletBinding()]
param(
    [string]$Python = 'python'
)

$ErrorActionPreference = 'Stop'

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Venv = Join-Path $Here '.venv'
$VenvPython = Join-Path $Venv 'Scripts\python.exe'
$Requirements = Join-Path $Here 'requirements.txt'

if (-not (Test-Path $Requirements)) {
    Write-Error "Requirements file missing: $Requirements"
}

if (-not (Test-Path $VenvPython)) {
    Write-Host "Creating virtual environment at $Venv ..."
    & $Python -m venv $Venv
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to create venv with '$Python'. Try -Python <path-to-python.exe>."
    }
} else {
    Write-Host "Virtual environment already exists at $Venv"
}

Write-Host "Upgrading pip ..."
& $VenvPython -m pip install --upgrade pip -q

Write-Host "Installing requirements from $Requirements ..."
& $VenvPython -m pip install -r $Requirements -q

Write-Host ""
Write-Host "Done. Robot Framework is installed in $Venv"
& (Join-Path $Venv 'Scripts\robot.exe') --version
# robot --version exits 251
if ($LASTEXITCODE -eq 251) { $global:LASTEXITCODE = 0 }
