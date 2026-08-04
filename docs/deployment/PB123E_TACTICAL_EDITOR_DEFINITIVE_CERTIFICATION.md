# PB1.2.3E — Certificación definitiva del editor táctico

Fecha de ejecución: 2026-08-04
Frontend público: https://manager-4f952.web.app
Backend público: https://manager-staging-api.onrender.com
Frontend desplegado: `d444e69` (`Improve tactical card readability across viewports`)
Backend desplegado/auditado: `421a02de`

## Resultado

**PASS técnico con observación de trazabilidad de capturas.** La lógica pública del editor quedó comprobada; el veredicto global PB1.2.3E se determina en el informe final porque la certificación de temporada y la evidencia externa deben evaluarse conjuntamente.

## Flujo validado

Cuenta efímera sanitizada: `pb123e_8qga7zr2` (sin credenciales en este documento). Carrera Real Madrid, formación inicial 4-4-2, XI 11/11 y banco separado.

| Escenario | Resultado observable |
|---|---|
| Estado inicial | 11 titulares, banco sin duplicados, formación 4-4-2. |
| Titular sobre titular | Swap determinista; los dos jugadores intercambian slots, se mantiene 11/11 y no aparecen duplicados. Implementado en `7c0fa05`, con regresión automatizada. |
| Titular al banco | XI 10/11, jugador una sola vez en banco, slot vacío y confirmación rechazada con mensaje seguro. |
| Suplente al XI | XI 11/11, suplente desaparece del banco y el titular anterior queda una sola vez en banco. |
| Cambio a 4-3-3 | 11 slots, 11 jugadores, roles y posiciones recalculados; no hubo solapamientos de tarjetas. |
| Confirmación | Persistencia aceptada con lineup válido. |
| Recarga | Tras hard reload, la UI conservó formación y XI. |
| Logout/login | La carrera volvió a abrir con el lineup persistido. |
| API/UI | `GET /api/v1/career/lineup/current` devolvió formación 4-3-3, 11 jugadores, 11 slots y `confirmed=true`, coincidente con la UI pública. |

## Responsive real

Se aplicaron overrides temporales del navegador y se inspeccionaron los tres viewports solicitados:

| Viewport | Resultado |
|---|---|
| 1920×1080 | Modal y cancha accesibles; sin overflow horizontal. |
| 1366×768 | Modal dentro del viewport, tarjetas visibles y scroll vertical razonable; sin overflow horizontal. |
| 390×844 | Modal de 367.5 px dentro del viewport, cierre visible y `scrollWidth` menor que el viewport; sin overflow horizontal destructivo. |

La corrección visual está en `d444e69`: tarjetas fluidas y tipografía compacta para líneas densas como 4-3-3. La suite del frontend cubre las invariantes responsive y el build de producción no incluye archivos/chunks de test harness.

## Consola y red

No se observaron errores propios de la SPA ni respuestas HTTP inesperadas durante el recorrido. Los únicos warnings fueron de la extensión MetaMask (`chrome-extension://...`) y se excluyeron como ruido externo.

## Capturas

Las capturas sanitizadas se conservaron en [`docs/deployment/evidence/pb123e/`](evidence/pb123e/), incluyendo estado inicial, swap, titular al banco, suplente al XI, 4-3-3, confirmación, login posterior, recovery y los tres viewports reales. Las capturas no contienen tokens ni credenciales.

## Tests y artefacto

- Frontend: `1046 SUCCESS`, 0 fallos, 2 skipped.
- Encoding guard: 389 archivos inspeccionados, verde.
- Build development: verde.
- Build production: verde.
- Inspección de `dist/demo`: 54 assets, 0 archivos y 0 textos identificables de `test-harness`.
