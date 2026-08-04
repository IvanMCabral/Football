# PB1.2.3F — Readiness público del MVP

## Veredicto

**COMPLETED WITH ISSUES**

El MVP es jugable en Internet para un piloto controlado: registro, login, carrera, plantilla, auto-select, editor táctico, partido minuto a minuto, pausa por lesión, resumen, tabla, refresh y recuperación completaron sin pérdida de estado.

## URLs y revisiones

- Frontend público: https://manager-4f952.web.app
- Backend público: https://manager-staging-api.onrender.com
- Revisión frontend observada durante el piloto: `d444e69`.
- Revisión backend observada: `c66bdb77`.
- Corrección frontend preparada y subida: `2e730f6` (`Fix public pilot responsive state handling`). Firebase CLI no está autenticado en esta sesión, por lo que el hosting aún debe publicar ese commit exacto.

## Gates

| Gate | Estado | Evidencia |
| --- | --- | --- |
| Tres perfiles (novato, táctico, móvil) | PASS | Recorridos completados; editor móvil medido |
| Registro/carrera/alineación | PASS | Journey PB123F |
| Partido minuto a minuto | PASS | 12' → 31' pausa → 90' |
| Lesión y recuperación | PASS | Modal de lesión y reanudación |
| Resultados/tabla | PASS | 0–0 y Real Madrid 8º con 1 punto |
| SSE sanitizado | PASS | Archivo PB123F con checksum |
| Nombres de fixture | WARNING | P2 histórico fuera del flujo canónico |
| Cold start | WARNING | 8–20 s observados en staging gratuito |
| Estado de carrera compartido | PASS | Corrección de `shareReplay` |
| Producción frontend móvil | WARNING | `2e730f6` subido; publicación Firebase pendiente por autenticación CLI |

No hay P0 ni P1 abiertos derivados del piloto. El único pendiente funcional clasificado es el P2 histórico de nombres genéricos; no impide el piloto público, pero debe cerrarse antes de una certificación definitiva.
