# Script para correr los E2E tests con las passwords correctas.
# Exporta DB_PASSWORD y REDIS_PASSWORD explicitamente; NO quema credenciales en el repo.
#
# Por que existe:
#   - La test DB (football_manager_test) se rota junto con la dev DB (V24D12-D-6).
#   - Redis DB 15 (test) require password desde la rotacion de V24D12-D.
#   - application-test.yml tiene defaults vacios (DB_PASSWORD / REDIS_PASSWORD)
#     a proposito para forzar este setup.
#   - Sin REDIS_PASSWORD, los 19 test classes E2E fallan con
#     NOAUTH Authentication required en AbstractIntegrationTest.cleanRedis (línea 61).
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File .\run-game-controller-tests.ps1
#   # o con un test especifico:
#   powershell -ExecutionPolicy Bypass -File .\run-game-controller-tests.ps1 -Test "RoundControllerE2ETest"
#
# Por default corre mvn test (suite completa). Si pasan los env vars y Redis esta UP,
# el resultado esperado es 1319 tests / 0 failures / 0 errors.

[CmdletBinding()]
param(
    [string]$Test = ""   # opcional: -Test "FooBarTest" para correr una clase especifica
)

$env:DB_PASSWORD = "Mgr2026Rot!Secure#"
$env:REDIS_PASSWORD = "MgrRedis2026!Rotate#Secure"

Set-Location D:\ProyectosOpenCode\MANAGER

if ([string]::IsNullOrWhiteSpace($Test)) {
    Write-Host "[run-game-controller-tests] Running full mvn test suite..."
    mvn test
} else {
    Write-Host "[run-game-controller-tests] Running mvn test -Dtest=$Test..."
    mvn test -Dtest=$Test
}
