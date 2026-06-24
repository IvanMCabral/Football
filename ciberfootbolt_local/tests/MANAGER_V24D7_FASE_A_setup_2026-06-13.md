# V24D7 — FASE A: Reporte de setup de test infrastructure

**Fecha:** 2026-06-13 21:50 ART
**Branch:** master
**Commit:** 5ca041b
**Tag objetivo:** V24D7FaseA (no pusheado, queda local hasta validación final)

---

## 1. Alcance cumplido

FASE A del plan V24D7 (P0: HTTP/E2E tests — setup de test infrastructure).
El objetivo era dejar la base lista para que FASE B y C escriban cobertura E2E real.

### Entregables

| Archivo | Líneas | Propósito |
|---|---:|---|
| `src/test/resources/application-test.yml` | 78 | Profile `test` aislado: DB `football_manager_test`, Redis DB 15, Flyway off, RANDOM_PORT |
| `src/test/java/com/footballmanager/AbstractIntegrationTest.java` | 66 | Base class con `@SpringBootTest(RANDOM_PORT)`, `WebTestClient`, cleanup Redis |
| `src/test/java/com/footballmanager/adapters/in/web/e2e/SmokeE2ETest.java` | 105 | 5 smoke tests que validan que la infra funciona end-to-end |
| `ciberfootbolt_local/tests/db_test_dump.sql` | 7709 | Snapshot de la DB dev (schema + data) para bootstrap de la DB test |

**Total: 4 archivos, 7958 insertions** (mayormente el dump SQL).

### Archivos NO tocados (regla dura del plan)

- `src/main/resources/application.yaml` — sin cambios
- `src/main/resources/application-local.yml` — sin cambios
- `src/main/resources/application-v24-mutations.yml` — sin cambios
- `pom.xml` — sin cambios (no agregamos dependencias nuevas)
- Ningún archivo de producción — sin cambios

---

## 2. Diseño técnico

### 2.1 Aislamiento del backend dev

El backend real está corriendo en :8080 con DB `football_manager` y Redis DB 0.
Para que los tests no choquen ni contaminen:

- **DB separada:** `football_manager_test` en el mismo Postgres. Bootstrappeada desde `db_test_dump.sql`. Flyway desactivado en test (los archivos SQL de migraciones no están en el repo, viven en runtime).
- **Redis DB 15:** el backend dev usa DB 0; los tests usan DB 15 (Redis soporta 16 DBs lógicas por default). `AbstractIntegrationTest.cleanRedis()` hace `FLUSHDB` antes de cada test → tests hermeticos.
- **Puerto random:** `@SpringBootTest(WebEnvironment.RANDOM_PORT)` evita colisión con :8080.

### 2.2 Stack de testing

- `WebTestClient` (viene con `spring-boot-starter-test`) — para HTTP sin levantar Tomcat.
- `StepVerifier` (viene con `reactor-test`) — para validar flujos reactivos del R2DBC/Redis.
- NO se agregó TestContainers (no hay Docker instalado).
- NO se agregaron dependencias nuevas — todo lo necesario ya estaba en el pom.

### 2.3 Por qué `@SpringBootTest` y no `@WebFluxTest`

El prompt de FASE A menciona ambos. Optamos por `@SpringBootTest` full context para el smoke porque:

- Es la única forma de validar que el stack entero (SecurityConfig, JwtTokenProvider, R2DBC, Redis, controllers) carga correctamente en el ambiente de test.
- Para los tests E2E de FASE B/C, la estrategia será híbrida: `@SpringBootTest` para flows críticos (carrer full lifecycle) y `@WebFluxTest` con `@MockBean` para coverage rápida de controllers individuales (resuelve el `@Disabled` del `LineupControllerE2ETest` que está pinned como gap).

### 2.4 Bootstrap de la DB test

`football_manager_test` se inicializa una vez con:
```
psql -U postgres -h localhost -c "CREATE DATABASE football_manager_test"
psql -U postgres -h localhost -d football_manager_test -f db_test_dump.sql
```

El dump se regenera con:
```
pg_dump -U postgres -h localhost -d football_manager --no-owner --no-privileges --clean --if-exists > db_test_dump.sql
```

**Decisión deliberada:** el dump vive en `ciberfootbolt_local/tests/` (subdir infra del repo) en vez de `src/test/resources/`. Razón: 1.6 MB de SQL no debería ir en el classpath de tests; solo es bootstrap manual.

---

## 3. Resultados de los tests

### 3.1 Smoke E2E Test (5/5 PASS, 6.5s)

| # | Test | Resultado | Tiempo |
|---|---|---|---|
| 1 | `GET /api/v1/health` → 200 "OK" | PASS | 0.05s |
| 2 | R2DBC `SELECT 1` contra test DB | PASS | 0.04s |
| 3 | Redis DB 15 set+get roundtrip | PASS | 0.04s |
| 4 | `GET /api/v1/world/teams?userId=...` → 200 | PASS | 0.05s |
| 5 | `GET /api/v1/world/leagues?userId=...` → 200 | PASS | 0.04s |

**Conclusión:** la infra de test funciona. Contexto Spring carga, R2DBC se conecta, Redis se conecta, controllers responden, SecurityConfig no bloquea endpoints permitidos.

### 3.2 Suite completa

```
[INFO] Tests run: 874, Failures: 0, Errors: 0, Skipped: 1
[INFO] BUILD SUCCESS
```

| Métrica | Antes (V24D6U6) | Después (V24D7FaseA) | Delta |
|---|---:|---:|---:|
| Tests totales | 869 | 874 | +5 |
| Failures | 0 | 0 | 0 |
| Errors | 0 | 0 | 0 |
| Skipped | 1 (`LineupControllerE2ETest` pinned) | 1 (mismo) | 0 |
| Tiempo total | ~baseline | +6.5s (smoke) | +0.5% |

**Sin regresiones. 0 cambios en código de producción.**

---

## 4. Bloqueos identificados y resueltos

| Bloqueo | Resolución |
|---|---|
| No hay Docker → TestContainers no viable | Usar Postgres y Redis locales en DBs separadas |
| Backend dev corriendo en :8080 | `@SpringBootTest(RANDOM_PORT)` |
| Backend dev comparte DB y Redis | DB test `football_manager_test` + Redis DB 15 |
| Archivos de migraciones Flyway no commiteados al repo | DB test bootstrappeada desde dump; `spring.flyway.enabled=false` en test |
| `LineupControllerE2ETest` pinned como `@Disabled` por gap E2E | Resoluble en FASE B con `@WebFluxTest` + `SecurityMockServerConfigurers` |
| `.env` con credenciales DB y JWT secret commiteado | **NO TOCADO en este PR** (observado, queda para decisión de seguridad) |

---

## 5. Lo que NO se hizo (intencional)

- **FASE B, C, D:** no son parte de FASE A. Quedan para próximas iteraciones.
- **No se re-habilitó `LineupControllerE2ETest`:** se hace en FASE B con la estrategia híbrida.
- **No se tocó `.env`:** contiene secretos en plaintext; es decisión de seguridad separada.
- **No se agregó `spring-boot-starter-actuator`:** el smoke usa `/api/v1/health` (custom), que es el health probe del proyecto.
- **No se truncó la DB entre tests:** los tests son idempotentes y no dependen de row counts; el seed LaLiga es estable.

---

## 6. Próximos pasos (FASE B — outline)

1. Re-habilitar `LineupControllerE2ETest` con `@WebFluxTest(LineupController.class)` + `@MockBean LineupCommandUseCase` + `@MockBean LineupQueryUseCase` + `SecurityMockServerConfigurers.springSecurity()` + `mutateWith(mockUser())`.
2. Agregar `WorldControllerE2ETest`, `AuthControllerE2ETest`, `MatchControllerE2ETest` con el mismo patrón.
3. Para flows end-to-end reales (carrer → match → stats), un `CareerFlowE2ETest` con `@SpringBootTest` full context que use el `careerId` real.
4. Coverage target: 40% → 70% (target relajado del plan, realista para 1 sprint).

---

## 7. Criterios de éxito del plan V24D7 — estado parcial

- [x] FASE A: setup de test infrastructure
- [ ] FASE B: tests E2E flow principal
- [ ] FASE C: tests E2E endpoints secundarios
- [ ] FASE D: reporte final y tag V24D7
- [ ] 869+N tests pasan → **874 actual**
- [x] mvn test limpio
- [ ] npx tsc + ng build (no hubo cambios frontend)
- [ ] Reporte final escrito (este es el de FASE A, falta el final consolidado)
- [ ] Working tree limpio (cambios FASE A commiteados, quedan untracked de sesiones previas que NO son de FASE A)

---

*Reporte FASE A V24D7. Commit: 5ca041b. Branch: master.*