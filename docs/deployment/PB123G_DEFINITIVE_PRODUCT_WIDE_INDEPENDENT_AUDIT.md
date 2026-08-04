# MANAGER — PB1.2.3G Auditoría independiente definitiva producto completo

**Fecha de ejecución:** 2026-08-04  
**Alcance:** auditoría independiente de los despliegues públicos, el código versionado y las suites locales.  
**Regla aplicada:** solo se consideró evidencia reproducida durante esta ejecución; los informes anteriores se trataron como contexto histórico, no como prueba.

## Veredicto ejecutivo

**REJECTED**

No hay hallazgos P0 de disponibilidad o exposición directa de datos. Sin embargo, permanecen hallazgos P1 que impiden certificar el producto para testers externos o beta pública:

1. El cambio de formación en el editor táctico refluye los once jugadores por índice de slot, coloca jugadores en roles incompatibles y persiste el cambio inmediatamente. En una prueba 4-4-2 → 4-3-3 un delantero terminó en CM con penalización -51%; al recargar la alineación persistió fuera de rol.
2. npm audit --omit=dev reportó 7 vulnerabilidades high en dependencias Angular de producción. No se ejecutó npm audit fix ni se modificaron dependencias.

La infraestructura pública responde y las suites locales están verdes, pero esas condiciones no compensan la pérdida de integridad táctica ni el riesgo de seguridad de dependencias. La certificación debe repetirse después de corregir ambos P1 y ejecutar la matriz de dos temporadas.

## 1. Identidad pública, hashes y estado Git

| Elemento | Evidencia |
|---|---|
| Frontend | https://manager-4f952.web.app |
| Backend | https://manager-staging-api.onrender.com |
| Root branch | feat/v25d99.20.3.1-runtime-fixes |
| Root HEAD | 16583ad50fcd9d5734b72befad999b54c11c811f |
| Frontend HEAD | 2e730f61b2839a1de0e0ec76d6c43ff2b3c0cf19 |
| Deploy frontend | release Firebase 7be336b62de800e0 |
| index.html SHA-256 | local/public d0a8064b6ef12dcfed504e7294feb89658c2735a673582474e5adfb367c13c1c |
| main JS SHA-256 | local/public d0bf7170f58a89eeff2740342884e2543098815dc6a174a0a5e2e2d2655d5765 |
| polyfills SHA-256 | local/public ec6a265b48881798350d0f97891b538b43945015eb03394c42b42e9366bd2ed2 |
| styles SHA-256 | local/public b1e13137a63b0c3f7f32d87a272cf9be053a48f9094698d202d0b75a08ecd1f7 |

El root quedó con los artefactos de esta auditoría y con el archivo preexistente docs/deployment/PB123A_ZERO_COST_STAGING_DEFINITIVE_INDEPENDENT_AUDIT.md sin trackear. El frontend quedó limpio. No se hizo commit, push, reset, stash ni cambio de rama.

## 2. Validación HTTP pública

| Superficie | Resultado |
|---|---|
| GET frontend / | 200 |
| GET frontend /login | 200 |
| GET frontend /dashboard | 200 |
| GET frontend /squad | 200 |
| GET frontend /standings | 200 |
| GET frontend /matches | 200 |
| GET backend /api/v1/health/liveness | 200, status UP |
| GET backend /api/v1/health/readiness | 200, database UP, redis UP |
| OPTIONS CORS desde frontend | 200, origin permitido, credentials true |
| OPTIONS CORS desde origin externo | 403 |
| OPTIONS CORS con wildcard | 403 |

La evidencia completa está en docs/deployment/evidence/pb123g/network/public-http.json, cors-preflight.json y public-errors.json.

## 3. Matriz de rutas

Las rutas SPA se enumeraron desde front-ciber/project/src/app/app.routes.ts y se probaron con navegación directa en Firebase. Los deep links HTML devolvieron 200 y la aplicación cargó su shell.

| Ruta | Resultado público |
|---|---|
| /, /dashboard | PASS; / redirige a /dashboard |
| /login, /register | PASS; formularios visibles y botones inicialmente deshabilitados |
| /career/setup | PASS; pantalla carga |
| /teams, /teams/create, /teams/manage, /teams/:id, /choose-team | No ejecutada con una carrera nueva |
| /players/create, /players/manage | PASS parcial; pantalla y selector visibles |
| /squad | PASS parcial; carrera autenticada visible |
| /standings, /matches | PASS parcial; shell y navegación visibles |
| /matches/create, /matches/:id | No ejecutada |
| /careers/:careerId/matches/:matchId/detail | Carga shell; no se ejecutó partido nuevo |
| /careers/:careerId/matches/:matchId/compare | No ejecutada |
| /games/:id, /games/:gameId/round/:round/live, /games/:gameId/round/:round/summary | No ejecutada en esta auditoría |
| /games/:gameId/champion, /games/:gameId/match/:matchId/live, /games/:id/play-round | No ejecutada en esta auditoría |
| /debug/test-harness | Redirige a /dashboard; no existe ruta pública de producción |

La matriz de controles observada se conserva en evidence/pb123g/buttons/button-route-matrix.json y el inventario completo en route-inventory.json.

## 4. Matriz de botones observada

Controles comunes: Inicio, Partidos, Salir, Football Manager.  
Dashboard: Crear club, Crear jugador, Gestionar jugadores y equipos, Gestionar ligas y equipos, Ver partidos, Programar partido, Ver tabla completa, Jugar Fecha 2, Borrar carrera.  
Login: Entrar (disabled con formulario vacío).  
Registro: Registrarse (disabled con formulario vacío).  
Squad: Volver, Ver Fixture, Tabla, Palmarés, Ascensos, Plantilla, Estadísticas, Gestionar, Ir a confirmar.  
Editor táctico: Auto seleccionar, Editar Formación Visual, Confirmar y jugar, cierre ×, selector con doce formaciones.

No se ejecutaron botones destructivos (Borrar carrera, acciones de administración, seed o harness) contra datos persistentes.

## 5. Responsive y visual

Se inspeccionó visualmente el frontend público con Chrome existente, incluyendo la pantalla de squad y el modal de formación; se guardaron capturas sanitizadas:

- evidence/pb123g/screenshots/squad-public.png
- evidence/pb123g/screenshots/tactical-editor-public.png

| Viewport solicitado | Resultado |
|---|---|
| 1920×1080 | PASS; viewport medido 1920×855, sin overflow horizontal |
| 1536×864 | No medido en esta ejecución |
| 1366×768 | No medido en esta ejecución |
| 1280×720 | No medido en esta ejecución |
| 1024×768 | No medido en esta ejecución |
| 768×1024 | No medido en esta ejecución |
| 430×932 | No medido en esta ejecución |
| 390×844 | No medido en esta ejecución |
| 360×800 | No medido en esta ejecución |

La instrumentación Chrome conectada no expuso un cambio de viewport confiable; no se convirtió esa ausencia en un PASS. La certificación responsive queda abierta como P2.

## 6. Editor táctico y formaciones

El modal público mostró las doce opciones esperadas: 4-4-2, 4-3-3, 3-5-2, 4-2-3-1, 5-3-2, 4-1-4-1, 3-4-3, 3-5-2-CDM, 5-4-1, 3-4-1-2, 4-2-2-2 y 4-1-2-3.

### P1-TACTICAL-001 — Reflow por índice y persistencia inmediata

- **Severidad:** P1.
- **Reproducción:** abrir /squad, abrir Ir a confirmar, observar 4-4-2 y seleccionar 4-3-3.
- **Esperado:** conservar los once jugadores y asignar slots compatibles, o pedir una confirmación explícita antes de persistir.
- **Observado:** 11/11 permaneció, pero un ST fue ubicado en CM con -51%; chemistry 74/99 → 68/99; effective team 92% → 85%; ATT 120% → 134%; MID 111% → 85%; DEF 106% → 90%. El modal mostró penalización fuerte y el slot quedó OFF.
- **Persistencia:** tras recargar squad, la alineación continuó 11/11 con roles fuera de posición.
- **Causa probable:** onFormationChange envía manual-select inmediatamente y reflowCurrentPlayersIntoFormation asigna jugadores por índice, no por ajuste de rol.
- **Superficie afectada:** editor, química, valoración, preview y futuro partido.
- **Evidencia:** evidence/pb123g/tactical/formations-observed.json, formation-change-observation.md, captura y DOM del editor.

Este hallazgo es suficiente para REJECTED aunque el backend no haya fallado.

## 7. Sustituciones

No se ejecutó una sustitución real en un partido nuevo durante esta auditoría. Los contratos y suites existentes cubren sustituciones, pero no sustituyen evidencia pública de minuto, persistencia y efecto posterior. Estado: NOT TESTED; P2 de evidencia.

## 8. Dos temporadas completas

No se completaron dos temporadas completas de forma independiente en esta ejecución. La carrera pública observada estaba en temporada 1, jornada 2/38, con 1 partido y 0 victorias. No se inventaron campeón, tabla final ni ascensos.

| Evidencia requerida | Estado |
|---|---|
| Temporada 1 completa | NOT TESTED |
| Campeón y tabla recomputada | NOT TESTED |
| Temporada 2 completa | NOT TESTED |
| Promoción/descenso | NOT TESTED |
| Consistencia tras reload | Solo estado parcial de jornada 2 |

La falta de esta evidencia no se oculta: queda como P2 de certificación pendiente.

## 9. Integridad de resultados

No se observó un marcador nuevo en esta ejecución y no se recalcularon campeones. Por lo tanto no se afirma que los resultados de dos temporadas sean plausibles ni que los 0-0 estén íntegros. Estado: NOT TESTED.

## 10. Persistencia, logout y recovery

Se comprobó que la squad autenticada se recupera después de navegación y recarga, y que 11/11 se consulta desde el backend. No se ejecutaron logout/login, recuperación de carrera, reset ni cold recovery en esta auditoría para no mutar datos de una sesión existente. Estado: PASS parcial; P2 de evidencia.

## 11. Cold start, restart y readiness

El backend respondió liveness 10/10. Readiness respondió 9/10; la primera muestra falló durante el cold start de Render Free y las siguientes devolvieron database UP/redis UP. P50/P95:

| Endpoint | P50 | P95 | Éxito |
|---|---:|---:|---:|
| liveness | 261 ms | 380 ms | 10/10 |
| readiness | 777 ms | 884 ms | 9/10 |
| Firebase / | 22 ms | 24 ms | 10/10 |

Interpretación: no es una caída sostenida, pero sí una ventana observable de disponibilidad durante cold start. Se clasifica P2 de operación y debe ser considerada en el proveedor.

## 12. Rendimiento

Solo se midieron diez muestras por endpoint de salud y la raíz HTML. No se extrapolan esos números a SSE, simulación, standings, detailed match ni dos temporadas. Evidencia: evidence/pb123g/performance/health-p50-p95.json.

## 13. Redis y PostgreSQL

El readiness público informó database UP y redis UP. No se modificó ni inspeccionó directamente ninguna base; no se imprimieron credenciales. La auditoría no certifica backups, restore drill, TTL ni pérdida/reconexión de Redis. Esas propiedades permanecen P2 de operación.

## 14. SSE y LiveSession

No se abrió una sesión de partido nueva ni se validó SSE con un cliente independiente en esta ejecución. El archivo histórico docs/deployment/evidence/pb123f/pb123f_sse_archive.json se reconoce como evidencia anterior, no como prueba PB123G. Estado: NOT TESTED; P2.

## 15. Errores, consola y red

- CORS allowed/denied/preflight se verificó por HTTP.
- Las respuestas 401 observadas usan contrato controlado UNAUTHORIZED sin stack trace ni SQL.
- La captura de consola del navegador no está expuesta por el puente usado; no se declaró “sin errores” por inferencia.
- El bridge emitió únicamente warnings propios de su instrumentación en ejecuciones anteriores; no se usaron como errores de la aplicación.
- Se detectó texto mojibake en el mensaje productivo de rate limit del backend (IntentÃ¡). Es un defecto P2 de calidad visible cuando se obtiene 429.

## 16. Perfil de producción y endpoints sensibles

El código y el test de contexto real ProductionMappingContextTest muestran que editor, test-harness, seed y mutadores world sensibles no se registran en profile prod. Las llamadas públicas sin autenticación a esas rutas devolvieron 401 por el filtro de seguridad; no se trató ese 401 como prueba suficiente por sí sola, y se conservó la evidencia del mapping real del test.

El frontend production redirigió /debug/test-harness a /dashboard. El artefacto de 52 archivos no contiene ocurrencias de test-harness y el inspector de producción pasó.

## 17. Seguridad de dependencias

npm audit --json terminó con:

- total: 56;
- critical: 3;
- high: 34;
- moderate: 18;
- low: 1.

npm audit --omit=dev --json terminó con 7 vulnerabilidades high y 0 critical en dependencias Angular de producción (@angular/core, @angular/common, @angular/compiler, @angular/forms, @angular/platform-browser, @angular/router, @angular/animations). No se actualizó ningún paquete. Se clasifica P1-SEC-002 por afectar el bundle entregado a usuarios.

## 18. Suites locales

| Suite | Resultado |
|---|---|
| Backend mvn -q -DskipTests test-compile | PASS |
| Backend mvn -q test | 2.585 tests, 0 failures, 0 errors, 4 skipped |
| Frontend build development | PASS |
| Frontend build production | PASS |
| Frontend encoding guard | PASS, 389 archivos |
| Frontend production artifact inspector | PASS, 52 archivos |
| Frontend Karma | 1.046 SUCCESS, 0 failures, 2 skipped (1.048 total) |

Evidencia de logs y resúmenes: evidence/pb123g/checks/.

## 19. Análisis WebFlux

El inventario productivo encontró .block() acotado a escritores legacy/batch y a la persistencia síncrona de detalle dentro del workflow de simulación de liga, donde el código documenta explícitamente el límite de batch. También hay un .subscribe() en el ejecutor de lifecycle. No se encontró evidencia en esta auditoría de un .block() nuevo en un controller request-path, pero el lifecycle y la semántica de errores requieren una revisión específica PB1.2.3H. Estado: P2 de seguimiento, no P0.

## 20. Matriz de responsive, partido y recuperación

La siguiente matriz distingue PASS observado, NOT TESTED y evidencia histórica:

| Área | Estado |
|---|---|
| Shell, navegación y deep links | PASS |
| Login/registro vacío y validación visual | PASS parcial |
| Squad autenticada | PASS parcial |
| Editor táctico visual | PASS visual / FAIL funcional P1 |
| Arrastre por píxel | NOT TESTED en esta ejecución |
| Sustitución minuto a minuto | NOT TESTED |
| SSE | NOT TESTED |
| Partido completo | NOT TESTED |
| Dos temporadas | NOT TESTED |
| Campeón, tabla y promoción | NOT TESTED |
| Logout/recovery | NOT TESTED |
| Mobile actual en ocho viewports | NOT TESTED |
| Debug excluido de producción | PASS |

## 21. Hallazgos consolidados

| ID | Sev. | Estado | Superficie |
|---|---|---|---|
| P1-TACTICAL-001 | P1 | ABIERTO | Cambio de formación y persistencia de roles |
| P1-SEC-002 | P1 | ABIERTO | Vulnerabilidades high en dependencias de producción |
| P2-COLD-003 | P2 | ABIERTO | Readiness transitoriamente no disponible en cold start |
| P2-SEASON-004 | P2 | ABIERTO | No hay evidencia independiente de dos temporadas |
| P2-RESP-005 | P2 | ABIERTO | Viewports mobile y tablet no medidos en esta ejecución |
| P2-MATCH-006 | P2 | ABIERTO | Sustituciones, SSE, recuperación y partido nuevo no ejecutados |
| P2-RATE-007 | P2 | ABIERTO | Rate limit en memoria no escala horizontalmente |
| P3-TEXT-008 | P3 | ABIERTO | Mojibake en comentarios y mensaje 429 del backend |

## 22. P0/P1/P2/P3

- **P0 abiertos:** ninguno observado.
- **P1 abiertos:** P1-TACTICAL-001, P1-SEC-002.
- **P2 abiertos:** P2-COLD-003, P2-SEASON-004, P2-RESP-005, P2-MATCH-006, P2-RATE-007.
- **P3 abiertos:** P3-TEXT-008.

## 23. Preparación para testers externos

**NO PREPARADO.** El editor puede degradar una alineación real sin confirmación y las dependencias de producción presentan vulnerabilidades high. Los testers no deben recibir el enlace como piloto certificado hasta resolver ambos P1.

## 24. Preparación para beta pública

**NO PREPARADO / REJECTED.** La infraestructura básica está viva, pero la integridad táctica y la seguridad de dependencias son gates de producto. La ausencia de una certificación independiente de dos temporadas mantiene además una brecha P2.

## 25. Evidencia y reproducibilidad

Todos los artefactos sanitizados de esta ejecución están en:

D:/ProyectosOpenCode/MANAGER/docs/deployment/evidence/pb123g/

No contienen contraseñas, tokens, cookies, correos personales ni identificadores de sesión. Los screenshots muestran únicamente UI de producto y nombres de plantel público. El archivo untracked previo fue preservado.

## 26. Orden de cierre

1. Corregir el reflow táctico y separar preview de persistencia; agregar prueba pública de reload y de las doce formaciones.
2. Actualizar Angular y dependencias transitivas hasta eliminar los 7 high de producción; repetir build, artifact scan y audit.
3. Completar dos temporadas en una cuenta efímera, con campeón, standings, recuperación, SSE, sustituciones y resultados recomputados.
4. Repetir responsive en los ocho viewports y medir partido/SSE, no solo health.
5. Repetir esta auditoría con hashes nuevos y exigir veredicto APPROVED.

**Resultado final:** REJECTED por dos P1 reproducidos.  
**No se modificó código productivo, frontend, tests, configuración, base de datos ni infraestructura de forma directa. La selección de formación del piloto se ejecutó mediante la UI pública para reproducir P1-TACTICAL-001 y pudo cambiar la alineación de esa carrera; no se realizaron escrituras SQL ni mutaciones administrativas. No se creó commit y no se hizo push.**
