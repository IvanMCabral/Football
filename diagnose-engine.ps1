# Diagnostico profundo del motor de partido.
#
# Objetivo:
#   Leer distribuciones, goles, xG, diversidad por formacion y calibracion.
#   Este comando imprime tablas largas a proposito; no es el smoke rapido.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\diagnose-engine.ps1

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$tests = @(
    "V24ModelTuningDiagnosticTest",
    "V27GoalBalanceBaselineDiagnosticTest",
    "V33CalibrationDiagnosticTest",
    "V24FormationGoalDiversityE2ETest",
    "V24DetailedMatchEngineSpeedsterTest",
    "V24ShotXgCalculatorShooterTest"
) -join ","

Write-Host "[diagnose-engine] Corriendo diagnosticos de motor..."
$logFile = Join-Path $PSScriptRoot "target\diagnose-engine.log"
New-Item -ItemType Directory -Force -Path (Split-Path $logFile) | Out-Null
$stdoutFile = "$logFile.out"
$stderrFile = "$logFile.err"
Remove-Item -Path $stdoutFile, $stderrFile -Force -ErrorAction SilentlyContinue
$process = Start-Process -FilePath "mvn.cmd" `
    -ArgumentList @("-q", "-Dtest=$tests", "test") `
    -RedirectStandardOutput $stdoutFile `
    -RedirectStandardError $stderrFile `
    -NoNewWindow `
    -Wait `
    -PassThru
Get-Content -Path $stdoutFile, $stderrFile -ErrorAction SilentlyContinue | Set-Content -Path $logFile
$mvnExitCode = $process.ExitCode
if ($mvnExitCode -ne 0) {
    Get-Content -Path $logFile -Tail 120
    throw "[diagnose-engine] Fallo el diagnostico de motor. Revisar target/diagnose-engine.log y target/surefire-reports."
}

Write-Host "[diagnose-engine] OK ($logFile)"
