# MANAGER - PB1.1 Production Hardening Checklist

Fecha: 2026-07-31

## P0 cerrados en esta fase

- [x] Perfil `prod` explícito creado.
- [x] Defaults inseguros removidos del perfil base.
- [x] Arranque `prod` falla si faltan variables críticas.
- [x] Secretos locales reales removidos de `src/main/resources/application-local.properties`.
- [x] CORS centralizado por allowlist configurable.
- [x] CORS wildcard eliminado de controllers.
- [x] `match-engine`, `fixtures`, `teams` y `leagues` ya no están como `permitAll`.
- [x] Admin endpoints no cargan en `prod`.
- [x] Seed endpoints no cargan en `prod`.
- [x] Debug/test harness backend no carga en `prod`.
- [x] Ruta frontend `debug/test-harness` queda deshabilitada en build production.
- [x] Redis clasificado como runtime crítico para PB1.1.
- [x] Health valida DB y Redis.
- [x] Logging base en producción pasa a `INFO` y Actuator expone solo `health,info`.

## P1 directamente relacionados abordados

- [x] `PORT` soportado para plataformas cloud.
- [x] Redis SSL configurable y activo por defecto en `prod`.
- [x] Flyway `baseline-on-migrate=false` en base/prod.
- [x] Error detail de servidor reducido en configuración productiva.

## Pendiente para PB1.2 Production Runtime

- [ ] Docker/buildpack runtime.
- [ ] Cloud Run/Firebase/Neon/Upstash configuración real.
- [ ] Backup automatizado del proveedor.
- [ ] Restore drill real.
- [ ] CI/CD.
- [ ] Medición de startup, memoria JVM y tamaño JAR.
- [ ] Rate limiting de auth/register.
- [ ] Migrar durabilidad primaria de Redis a PostgreSQL.
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
