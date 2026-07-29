# Smoke rapido de la version jugable.
#
# Objetivo:
#   Validar formaciones, live minuto-a-minuto, replay/harness y flujo de torneo
#   sin correr toda la suite historica ni imprimir diagnosticos largos.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\smoke-fast.ps1

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$tests = @(
    "FormationServiceTest",
    "DetailedFormationParserTest",
    "DetailedSprintetailedMatchEngineFormationTest",
    "DetailedLiveSessionTickIncrementalTest",
    "DetailedLiveSessionTest",
    "DetailedSubstitutionEngineTest",
    "DetailedLiveSessionEventFilterTest",
    "CachingRandomWrapperTest",
    "DetailedSprintetailedMatchEngineRandomOverloadTest",
    "C55_7_5_FullEndOfTournamentFlowTest",
    "DetailedFormationGoalDiversityE2ETest"
) -join ","

Write-Host "[smoke-fast] Validando version jugable..."
$logFile = Join-Path $PSScriptRoot "target\smoke-fast.log"
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
    Get-Content -Path $logFile -Tail 80
    throw "[smoke-fast] Fallo el smoke rapido. Revisar target/smoke-fast.log y target/surefire-reports."
}

Write-Host "[smoke-fast] OK ($logFile)"
