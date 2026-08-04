# PB1.2.3G — Cierre disciplinado de alcance

Fecha de validación: 2026-08-04  
Rama: `feat/v25d99.20.3.1-runtime-fixes`

## Resultado

**PB1.2.3G APPROVED WITH ISSUES**

No se reprodujeron P0 ni P1 pertenecientes al alcance congelado. Quedan únicamente limitaciones P2/P3 de evidencia y del plan gratuito, documentadas sin convertirlas en requisitos nuevos.

## Criterios congelados

Se ejecutaron exactamente los ocho gates definidos para este cierre:

1. Clasificación del diff `82e74713a32eeb3467be6ec683c2da61e816b326..9e8ca36711ca5dd1db3cd186d4200c1715dd202f`.
2. Clasificación del helper local `--signal-existing`.
3. Flujo táctico público autenticado con cuenta efímera.
4. Responsive público en 1920×1080, 1366×768 y 390×844.
5. Logout/login real del flujo táctico.
6. Sustitución pública válida bajo una precondición natural del partido.
7. Consola y errores propios del sitio durante el recorrido.
8. Revalidación de suites, builds y auditoría de producción después de la corrección reproducida.

## Clasificación del diff Render → HEAD

El diff contiene 73 archivos. La clasificación por contenido es:

- `DOCUMENTATION_ONLY` y `EVIDENCE_ONLY`: reportes, JSON, capturas y checksums de `docs/deployment/**`.
- `TEST_ONLY`: `AuthControllerE2ETest`, `RuntimeOperationMetricsTest` y ajustes de `RateLimitingWebFilterTest`.
- `PRODUCTIVE_BACKEND_RUNTIME`: `AuthController`, `CareerViewController`, `DashboardController`, `RedisCareerRepository`, `UserRepositoryAdapter`, `AuthUseCaseImpl` y `RuntimeOperationMetrics`.
- No se detectaron cambios productivos de configuración, Docker, Render ni workflows en ese rango.

Por lo tanto, el HEAD sí podía producir un JAR distinto y el SHA observado inicialmente en Render no era solo nominal. Se construyó y se desplegó el HEAD exacto. El Docker build de ese HEAD incluye el runtime Java productivo clasificado arriba.

## Runtime equivalence / redeploy exacto

- Root runtime auditado: `9e8ca36711ca5dd1db3cd186d4200c1715dd202f`.
- Render revision live: `9e8ca36711ca5dd1db3cd186d4200c1715dd202f`.
- Backend redeployed: sí, manualmente desde el dashboard de Render con el commit exacto.
- `GET /api/v1/health/liveness`: `200`, `{"status":"UP"}`.
- `GET /api/v1/health/readiness`: `200`, base de datos y Redis `UP`.
- `GET /`: `401`, comportamiento esperado porque la raíz está protegida; no se clasificó como fallo de la aplicación.

Los logs de arranque confirmaron aplicación live, Flyway actualizado y conexiones de PostgreSQL/Redis disponibles.

## Helper `--signal-existing`

`tools/GracefulProcessGroupRunner.cs` y `tools/run-production-jar-smoke.ps1` son tooling local de lifecycle PB1.2.1:

- no se empaquetan dentro del JAR;
- no se copian a la imagen runtime del `Dockerfile`;
- no se ejecutan en Render;
- no participan en requests, SSE, partidos ni persistencia;
- no pueden afectar al tester público.

El runner usa state file privado del workspace, PID recién persistido, proceso vivo, puerto esperado, ownership del workspace, limpieza de procesos/puertos y resultados fail-closed. `process-group-id` se transporta y se registra para la recuperación, pero no se presentó una validación independiente adicional del grupo de Windows. No se reprodujo un PASS falso dentro del runner oficial. Disposición: `PB1.2.1 TOOLING HARDENING`, fuera del gate PB1.2.3G; no se modificó.

## UI táctica pública

Se usó una cuenta efímera en `https://manager-4f952.web.app` y una carrera pequeña de Primera División:

- registro y creación de carrera: correctos;
- auto-select inicial: `4-4-2`, `11/11`;
- cambio en draft a `4-3-3`: preview `11/11`, sin modificar el estado externo;
- cambio a `4-2-3-1` y movimiento de un jugador por píxel: aplicado solo al draft;
- cierre con `X` y reload: volvió a la formación/coordenadas confirmadas anteriores;
- confirmación de `4-3-3`: persistió tras reload y tras logout/login;
- una única selección de guardado observada por acción de confirmación;
- no se observó duplicación de jugadores, falta de arquero ni pérdida de estado;
- el rol táctico de las tarjetas y la compatibilidad de roles se conservaron.

## Preview, cancel, confirm y doble confirmación

El selector, el drag y cancelar dejaron el backend en `4-4-2`. El botón de guardar apareció deshabilitado durante el estado incompleto y la confirmación dejó `4-3-3` persistida. No se observó una segunda mutación al repetir la acción desde la UI. La superficie de Chrome disponible no expone un panel de red con conteo exacto de requests; por eso no se afirma una captura wire-level de POST, solo el resultado observable y la guardia de UI.

## Logout/login

El botón real `Salir` llevó a `/login`; el login posterior con la misma cuenta efímera volvió al dashboard y a la carrera con `4-3-3` y `11/11`.

## Swap y banco

- Swap titular ↔ titular: Rodrygo y Endrick intercambiaron posiciones; el cambio se reflejó en el draft y persistió tras guardar y reabrir.
- Titular → banco: dejó `10/11`; guardar permaneció deshabilitado y, tras reload, el backend siguió sin cambios.
- Suplente → XI: Franco Mastantuono entró al XI y Rodrygo quedó en banco; `11/11`, sin duplicados, persistido tras reload.
- Arquero: Andriy Lunin permaneció como GK.

## Responsive público

Las mediciones DOM posteriores a la corrección fueron:

| Viewport | Documento | Overflow horizontal | Modal/cierre | Selector | Banco | Guardar |
|---|---:|---|---|---|---|---|
| 1920×1080 | 1905/1905 | No | Visible | Visible | Visible | Visible |
| 1366×768 | 1351/1351 | No | Visible | Visible | Visible | Visible |
| 390×844 | 375/375 | No | Visible | Visible | Visible mediante scroll vertical | Visible |

La pantalla live también quedó sin overflow horizontal en los tres tamaños. El defecto móvil real reproducido antes del deploy era el colapso/desborde del banco; se corrigió únicamente en `squad-editor-modal.component.css` y se volvió a desplegar el build exacto.

## Partido, SSE, lesión y sustitución

El partido público progresó de minuto 4 a 20, quedó pausado por lesión en el minuto 20, y continuó en 62, 69, 76 y 90 con marcador estable y la misma ronda. Se observó lesión de Franco Mastantuono y el modal permitió una sustitución válida por Rodrygo. El marcador pasó de `0-0` a `0-1` sin reiniciar minuto ni ronda; el partido terminó en `0-1` y el reload recuperó la misma ronda.

La UI mostró una única progresión de stream y no presentó retry loop. La API de control de Chrome utilizada en esta sesión no expone los headers/status físicos del EventSource, por lo que no se certifica aquí el `200 text/event-stream` wire-level; la evidencia existente también declara esa limitación. Esto es P2 de observabilidad, no un fallo reproducido del producto.

## Consola y red

- Errores propios del sitio en consola: ninguno después de excluir únicamente extensiones, MetaMask, Codex/Computer Use y bridge warnings.
- CORS, spinner infinito, pérdida de estado, drag roto y 404/500 repetidos: no reproducidos.
- Health público: liveness/readiness `200`.
- Conteo wire-level exacto de POST y headers SSE: no disponible en la capability de inspección usada; queda expresamente como limitación de evidencia.

## Correcciones realizadas

Única corrección funcional de este cierre:

- Frontend: reglas responsive del editor táctico en `src/app/components/squad-editor-modal/squad-editor-modal.component.css` para apilar paneles, envolver banco y acciones, y eliminar overflow móvil.

El backend no requirió una nueva corrección en este cierre; se redeployó porque el diff auditado sí contenía cambios Java productivos.

## Tests, builds y auditoría de producción

- Backend `mvn -q -DskipTests test-compile`: verde.
- Backend suite completa: `2585` tests, `0` failures, `0` errors, `4` skipped.
- Frontend Karma completo: `1049 SUCCESS`, `0` failures, `2` skipped.
- Frontend encoding guard: verde, 389 archivos revisados.
- Frontend development build: verde.
- Frontend production build: verde.
- `npm audit --omit=dev`: 0 vulnerabilidades de producción.
- Artefacto production: 54 archivos, 0 source maps, 0 referencias al test harness.

## Deploys y hashes

- Frontend: `https://manager-4f952.web.app`.
- Backend: `https://manager-staging-api.onrender.com`.
- Firebase release posterior a la corrección: completada.
- SHA-256 `index.html` local/público: `9F01D634190DB4AD0B48248A2D453505250669773983E552404B937C901EE811` / idéntico.
- SHA-256 `main-MR2CIEDG.js` local/público: `39945B1BE152F52289845B3D750F95F04C44C80F8D1A681F0D29A905682881F3` / idéntico.
- SHA-256 `chunk-5VEIFBHY.js` local/público: `69A776F59037520CD14D2A9955AACF3964CCD1B18D81D61860DA8F0BA80DA3EC` / idéntico.
- Frontend commit desplegado: `b080df42d9df33e574a3458952883cfa7a951cf4`.

## Severidad

- P0: ninguno.
- P1: ninguno reproducido dentro de PB1.2.3G.
- P2: status/mime físico de SSE no observable con la capability utilizada; cold start y estado inicial transitorio propios del plan gratuito; validación adicional de process-group-id del helper local.
- P3: no se ejecutó un nuevo piloto con testers humanos externos durante este cierre; el flujo queda preparado para ese piloto.

## Testers externos y beta pública

El recorrido con cuenta efímera dejó la UI táctica, el login, la persistencia de lineup, la sustitución y el partido público preparados para un piloto controlado. La beta pública queda técnicamente preparada con la salvedad del cold start del tier gratuito y la limitación de evidencia wire-level de SSE. No se modificaron gameplay, probabilidades, calendario, datos deportivos ni arquitectura cloud.

## Commits, push y estado

- Commit frontend: `b080df4 fix(ui): keep tactical editor usable on mobile`.
- Commit root de este cierre: se creará únicamente con este reporte.
- Push: se ejecutará después de crear el commit documental y verificar ambos estados.
- Los dos reportes de auditoría histórica preexistentes y sin seguimiento (`PB123A_ZERO_COST_STAGING_DEFINITIVE_INDEPENDENT_AUDIT.md` y `PB123G_P1_REMEDIATION_DEFINITIVE_INDEPENDENT_AUDIT.md`) se preservan y no se incluyen en este cierre.

## Conclusión

El alcance congelado de PB1.2.3G queda cerrado sin P0/P1 válidos. El backend está desplegado desde el HEAD exacto auditado, el frontend público coincide byte a byte con el build local corregido y el flujo táctico público es jugable en los tres tamaños exigidos. El estado es **APPROVED WITH ISSUES** únicamente por las limitaciones P2/P3 explícitas; no hay un defecto funcional pendiente que justifique `REJECTED`.
