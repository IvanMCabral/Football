# Script para correr los E2E tests del GameController con la password correcta.
# Usa el env var DB_PASSWORD explicito; NO quemar la password en el repo.
#
# Por que existe: la test DB se rota junto con la dev DB (V24D12-D-6).
# application-test.yml tiene default vacio a proposito para forzar este setup.

$env:DB_PASSWORD = "Mgr2026Rot!Secure#"
Set-Location D:\ProyectosOpenCode\MANAGER
mvn test -Dtest=GameControllerE2ETest
