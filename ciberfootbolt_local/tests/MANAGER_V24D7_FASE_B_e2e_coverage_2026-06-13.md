# V24D7 — FASE B: Reporte de cobertura E2E real

**Fecha:** 2026-06-13 22:20 ART
**Branch:** master
**Commits:** ab55faa (codigo) sobre e5c58f0 (reporte FASE A)
**Tag objetivo:** V24D7FaseB (no pusheado, queda local hasta validacion final)

---

## 1. Alcance cumplido

FASE B del plan V24D7 (P0: HTTP/E2E tests — cobertura E2E real con ``@WebFluxTest`` + ``@MockBean``).

### Entregables

| Archivo | Líneas | Tests | Propósito |
|---|---:|---:|---|
| ``pom.xml`` (modificado) | +10 | - | Agrega ``spring-security-test 6.2.1`` (BOM Spring Boot 3.2.1) |
| ``LineupControllerE2ETest.java`` (re-habilitado) | 378 | 12 | Re-habilita el test pinned ``@Disabled`` desde V24D6T con cobertura HTTP real |
| ``AuthControllerE2ETest.java`` (nuevo) | 168 | 6 | Cobertura E2E del flow de auth: register, login, JWT, /me |
| ``WorldQueryControllerE2ETest.java`` (nuevo) | 122 | 5 | Cobertura E2E de queries del mundo: leagues, teams, players, free-players |
| ``CareerFlowE2ETest.java`` (nuevo) | 88 | 2 | Smoke-level coverage del flow de career (start, reset, get) |
| ``LaLigaSeedE2ETest.java`` (nuevo) | 130 | 6 | Validacion de la integridad del seed LaLiga en DB test |

**Total: 6 archivos, 893 insertions, 40 deletions, 31 tests nuevos (de 873 a 904).**

### Archivos NO tocados (regla dura del plan)

- ``src/main/resources/application.yaml`` — sin cambios
- ``src/main/resources/application-local.yml`` — sin cambios
- ``src/main/resources/application-v24-mutations.yml`` — sin cambios
- Codigo de produccion (controllers, services, domain) — sin cambios
- ``.env`` — sin cambios (sigue con secretos en plaintext, fuera de scope)

---

## 2. Decisiones tecnicas del approach

### 2.1 Por que ``@SpringBootTest`` y no ``@WebFluxTest`` puro

El test placeholder original (``LineupControllerE2ETest``) documentaba exactamente el problema: ``@WebFluxTest`` escanea el main ``@SpringBootApplication``, que transitivamente requiere ``LeagueR2dbcRepository`` y otros beans que no se pueden mockear trivialmente en un slice. Intente dos approaches y ambos fallaron con el error original:

- ``@WebFluxTest(LineupController.class)`` + ``@Import(GlobalExceptionHandler.class)``: NPE en ``webHttpHandlerBuilder`` por inner classes / NESTED.
- ``@WebFluxTest(LineupController.class)`` + ``@MockBean`` de los 3 collaborators: falla porque ``LeagueRepositoryAdapter`` (no es controller) requiere ``LeagueR2dbcRepository``.

**Solucion final:** ``@SpringBootTest(RANDOM_PORT)`` con la infra completa (DB test + Redis DB 15 de FASE A) + ``@MockBean`` solo de los 3 collaborators del controller (use cases + ``CareerSessionService``). El contexto Spring carga, el ``SecurityConfig`` corre, el ``GlobalExceptionHandler`` mapea errores, y los mocks interceptan solo lo que necesita el controller.

Costo: ~5-7s por clase de test (boot del contexto se cachea entre tests de la misma clase). Vale la pena: cobertura HTTP real, sin trucos.

### 2.2 Por que ``SecurityMockServerConfigurers.mockUser(name)``

El controller resuelve el ``userId`` desde ``Authentication.getName()``. Sin security config activo, no hay ``SecurityContext`` en el ``ServerWebExchange``, y el ``Authentication`` parameter seria null. ``mockUser(name)`` setea un ``UsernamePasswordAuthenticationToken`` con ``name = "userId"``, que es exactamente lo que el controller espera. No requiere JWT valido ni ``permitAll`` en el filter chain — el ``mutateWith`` setea el contexto per-request directamente.

Para los endpoints en ``permitAll`` (todos los de ``/api/v1/career/...``, ``/api/v1/auth/...``, ``/api/v1/world/...`` per SecurityConfig), ``mockUser`` funciona aunque no haya token. Para endpoints con ``@authenticated()`` (como ``/api/v1/dashboard/**``), los tests futuros necesitaran JWT real o ``mockJwt()``.

### 2.3 Re-habilitacion del placeholder V24D6T

El ``LineupControllerE2ETest`` original (1 test placeholder) documentaba el gap exacto y proponia re-habilitarlo. La re-habilitacion cubre ahora 12 escenarios reales:

| Endpoint | Escenario | Status esperado |
|---|---|---|
| ``POST /auto-select`` | happy path, 11 players | 200 |
| ``POST /auto-select`` | short-handed (7 players) | 200 + warning ``LINEUP_SHORT_HANDED`` |
| ``POST /auto-select`` | phase = IN_MATCH | 422 + ``LINEUP_STATE_ERROR`` |
| ``POST /auto-select`` | formation blank | 422 + ``LINEUP_VALIDATION_ERROR`` |
| ``POST /auto-select`` | NotEnoughPlayersException | 422 + ``LINEUP_MINIMUM_PLAYERS_NOT_MET`` |
| ``POST /manual-select`` | happy path, 11 IDs | 200 |
| ``POST /manual-select`` | size != 11 | 422 + ``LINEUP_VALIDATION_ERROR`` |
| ``POST /manual-select`` | empty list | 422 + ``LINEUP_VALIDATION_ERROR`` |
| ``POST /confirm`` | happy path | 200 (controller usa ``Mono<Void>``, no 204) |
| ``POST /confirm`` | phase = IN_MATCH | 422 + ``LINEUP_STATE_ERROR`` |
| ``GET /current`` | empty lineup | 200 + formation + players=[] + confirmed=false |
| ``GET /current`` | full 11-player lineup | 200 + 11 players |

El placeholder original (``@Disabled("V24D6T gap...")``) se elimino.

---

## 3. Resultados de los tests

### 3.1 Por test class

| Test class | Tests | Tiempo | Cobertura |
|---|---:|---:|---|
| ``LineupControllerE2ETest`` | 12 | 5.3s | 4 endpoints (auto-select, manual-select, confirm, current) |
| ``AuthControllerE2ETest`` | 6 | 7.7s | 5 endpoints (register, login, /me) |
| ``WorldQueryControllerE2ETest`` | 5 | 6.8s | 5 endpoints (leagues, teams, players, etc.) |
| ``CareerFlowE2ETest`` | 2 | 5.6s | 2 endpoints (reset, current) — smoke-level |
| ``LaLigaSeedE2ETest`` | 6 | 6.4s | 4 DB checks + 2 HTTP checks de la integridad del seed |

### 3.2 Suite completa

``````
[INFO] Tests run: 904, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
``````

| Metrica | FASE A (869+N) | FASE B (904+N) | Delta |
|---|---:|---:|---:|
| Tests totales | 874 | 904 | +30 |
| Failures | 0 | 0 | 0 |
| Errors | 0 | 0 | 0 |
| Skipped | 1 | 0 | -1 (placeholder re-habilitado) |
| Tiempo total | ~baseline | +35s E2E | +5% |

**Sin regresiones.** El unico cambio en codigo de produccion: ``pom.xml`` agrega ``spring-security-test`` (test scope, no afecta runtime).

---

## 4. Bloqueos identificados y resueltos

| Bloqueo | Resolucion |
|---|---|
| ``@WebFluxTest`` puro requiere mockear todo el ``@SpringBootApplication`` (R2DBC repos, etc.) | Cambio a ``@SpringBootTest`` con ``AbstractIntegrationTest`` de FASE A + ``@MockBean`` de los 3 collaborators del controller |
| NPE en ``webHttpHandlerBuilder`` con ``@Nested`` classes | Aplanar los tests (sin ``@Nested``) |
| ``Authentication`` parameter sin security config | ``SecurityMockServerConfigurers.mockUser(name)`` setea el principal per-request |
| ``ReactiveServerCommands`` no tiene ``ping()`` | (FASE A) usar set+get roundtrip en su lugar |
| Username UNIQUE en tabla ``users`` rompia test de duplicate email | Username con ``UUID.randomUUID().toString().substring(0, 12)`` |
| ``expectBody(JsonNode.class).value(...)`` retorna null para Mono body | Cambio a ``expectBody().jsonPath(...)`` |
| ``R2DBC DatabaseClient.sql().map().collectList()`` no existe | Usar ``.all().collectList().block()`` o ``.one().block()`` |

---

## 5. Lo que NO se hizo en FASE B (y por que)

- **MatchController E2E tests:** el flow start-career -> play-match -> match-detail es complejo (requiere career persistido + lineup + Redis state). El ``CareerFlowE2ETest`` cubre el smoke level. Profundizar requiere más setup, queda para FASE C o una iteracion posterior.
- **V24DetailedMatchControllerE2ETest:** el plan V24D7 lo menciona pero requiere un match pre-jugado en Redis, lo cual es setup pesado. Se omite en esta fase por costo/beneficio.
- **Mutations edge cases (P1b del plan):** fuera de scope de FASE B (que es P0).
- **Coverage report (``mvn jacoco``):** no se genero reporte numerico. La suite actual es smoke-level + integration-level; la cobertura E2E real es la métrica de valor, no line coverage.
- **TestContainers:** no aplica (no hay Docker en este ambiente).

---

## 6. Proximos pasos (FASE C — outline)

1. **MatchControllerE2ETest:** POST /api/v1/career/match/start, GET /api/v1/career/match/{id}/detail.
2. **RoundControllerE2ETest:** POST /api/v1/career/round/start, GET /api/v1/career/round/{n}/state.
3. **TeamOVRQueryServiceE2ETest:** GET /api/v1/world/leagues/{id}/teams-with-ovr (logica de OVR calculada).
4. **LaLigaSeedControllerE2ETest:** POST /api/v1/world/seed-la-liga idempotencia (verificar que ejecutar 2 veces no duplica).
5. **PlayerSeasonStatsControllerE2ETest:** GET /api/v1/players/{id}/season-stats.
6. **CareerFlowE2ETest expansion:** agregar el flow completo start career -> auto-select -> confirm -> next-round -> match detail, con un career seed en Redis (no solo DB).

---

## 7. Criterios de exito del plan V24D7 — estado parcial

- [x] FASE A: setup de test infrastructure (commit 5ca041b)
- [x] FASE B: tests E2E flow principal (commit ab55faa)
- [ ] FASE C: tests E2E endpoints secundarios
- [ ] FASE D: reporte final y tag V24D7
- [x] 869+N tests pasan → **904 actual**
- [x] mvn test limpio
- [ ] npx tsc + ng build (no hubo cambios frontend)
- [ ] Reporte final escrito (este es el de FASE B, falta el final consolidado)
- [ ] Working tree limpio (cambios FASE A+B commiteados, quedan untracked de sesiones previas que NO son de V24D7)

---

*Reporte FASE B V24D7. Commit: ab55faa. Branch: master.*