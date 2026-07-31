# MANAGER - PB1.1 Production Hardening Report

Fecha: 2026-07-31

## Alcance

Se cerraron los P0 de seguridad productiva detectados en `PB1_PRODUCTION_READINESS_AUDIT.md` sin modificar gameplay, simulación, motor, probabilidades, datasets ni reglas deportivas.

## Cambios principales

### Production profile

- Se creó `src/main/resources/application-prod.yml`.
- `prod` separa logging, Redis SSL, Actuator, errores y flags productivos.
- `server.port` soporta `PORT` para proveedores cloud y conserva fallback local.

### Secrets

- Se removieron defaults inseguros de `application.yaml`.
- DB, Redis y JWT dependen de variables de entorno.
- Se agregó `ProductionStartupValidation`, que bloquea arranque `prod` si faltan secretos o aparecen valores inseguros conocidos.
- Se eliminó `application-local.properties`, que contenía secretos reales versionados.

### CORS

- Se reemplazó CORS disperso por `CorsConfig` central con allowlist.
- Se eliminaron `@CrossOrigin` y wildcards en controllers.
- `APP_CORS_ALLOWED_ORIGINS` define origins permitidos por ambiente.

### Runtime endpoints

- Admin endpoints no cargan en `prod`.
- Seed endpoints no cargan en `prod`.
- Test harness/debug backend no carga en `prod`.
- Frontend production no registra la ruta `debug/test-harness`.
- Rutas sensibles dejaron de ser públicas por política central.

### Redis

- Redis queda clasificado como runtime crítico para PB1.1.
- Redis SSL es configurable y se activa en `prod`.
- La estrategia queda documentada en `REDIS_RUNTIME_STRATEGY.md`.

### Health

- `/api/v1/health` valida PostgreSQL y Redis.
- Si cualquier dependencia crítica cae, responde `DOWN` con HTTP 503.

### Logging

- Producción usa logging menos verboso.
- Actuator production expone `health,info`.
- Error detail productivo queda reducido desde configuración.

## Tests agregados

- `ProductionStartupValidationTest`
- `CorsConfigTest`
- `ProductionEndpointProfileTest`
- Frontend route guard tests para `debug/test-harness`.

## Validación ejecutada

- Backend `mvn -q -DskipTests test-compile`: verde.
- Backend `mvn -q test`: 2536 tests, 0 failures, 0 errors, 4 skipped.
- Frontend build development: verde.
- Frontend build production: verde.
- Frontend `npm test -- --watch=false --browsers=ChromeHeadless`: 1028 SUCCESS, 0 failures, 2 skipped.
- `git diff --check`: sin errores de whitespace; solo warnings normales de CRLF en Windows.

## P0 resueltos

- P0-01 CORS refleja cualquier Origin: resuelto.
- P0-02 Defaults productivos inseguros: resuelto.
- P0-03 Secretos reales versionados en runtime: resuelto.
- P0-04 Endpoints públicos demasiado amplios: resuelto.
- P0-05 Admin endpoints en runtime productivo: resuelto por profile guard.
- P0-06 Debug/test harness visible en producción: resuelto en backend y frontend production.
- P0-07 Perfil `prod` inexistente: resuelto.
- P0-08 Redis ambiguo: resuelto documentalmente como runtime crítico; PB1.2 debe implementar proveedor/restore real.
- P0-09 Backup/restore automatizado: no implementado por restricción explícita de no crear CI/CD/cloud/deploy; queda como gate operativo PB1.2.
- P0-10 Artefacto Docker/cloud: no implementado por restricción explícita de no crear Docker/Cloud Run/Firebase; queda como gate PB1.2.

## Veredicto después de hardening

El código queda preparado para iniciar una fase PB1.2 de runtime productivo. Aún no debe desplegarse públicamente hasta completar infraestructura real, backups automáticos, restore drill, CI/CD y pruebas contra proveedores cloud.
## PB1.1 final remediation addendum - 2026-07-31

Post-audit remediation closed the P0 application hardening findings. See:

- `docs/deployment/PB1_PRODUCTION_HARDENING_FINAL_REMEDIATION.md`
- `docs/deployment/PB1_PRODUCTION_HARDENING_FINAL_REVIEW.md`
- `docs/deployment/PB1_SECRET_ROTATION_AND_REPOSITORY_HYGIENE.md`
- `docs/deployment/PRODUCTION_SECURITY_HEADERS.md`
- `docs/deployment/PB1_REMAINING_INFRASTRUCTURE_GATES.md`

Validation evidence:

- Backend: 2548 tests, 0 failures, 0 errors, 4 skipped.
- Frontend: 1029 SUCCESS, 0 failures, 2 skipped.
- Production frontend artifact inspection: no test harness route/chunk/text in `dist/demo`.

Historical rejected verdicts remain preserved in their original reports; the remediation is recorded separately.
