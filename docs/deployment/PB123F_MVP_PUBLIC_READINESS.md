# PB1.2.3F — Readiness público del MVP

## Veredicto

**BLOCKED**

El MVP es jugable en Internet para un piloto controlado: registro, login, carrera, plantilla, auto-select, editor táctico, partido minuto a minuto, pausa por lesión, resumen, tabla, refresh y recuperación completaron sin pérdida de estado.

El cierre público queda bloqueado por una dependencia externa de publicación: Firebase CLI no está autenticado en esta sesión. El commit frontend `2e730f6` ya está subido y compilado, pero no puede desplegarse ni verificarse en `manager-4f952.web.app` hasta disponer de una sesión Firebase CLI válida. El sitio público sigue sirviendo `d444e69`, donde el status bar mantiene overflow horizontal a 390 × 844.

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
| Producción frontend móvil | FAIL | `2e730f6` subido; publicación Firebase bloqueada por autenticación CLI |

No hay P0 derivados del backend ni del motor. Queda un P1 operativo abierto: publicar y verificar `2e730f6` en Firebase. El P2 histórico de nombres genéricos sigue clasificado y no se alteró durante el piloto.

## Bloqueo externo exacto

Comando ejecutado: `npx --yes firebase-tools deploy --only hosting --project manager-4f952`

Error: `Failed to authenticate, have you run firebase login?`

Alternativas locales intentadas: CLI global (no instalado), `npx firebase-tools` (disponible), proyecto explícito `manager-4f952`, búsqueda de `FIREBASE_TOKEN`/credenciales de gcloud y workflows de deploy. No hay token, gcloud autenticado ni workflow de Firebase disponible en el entorno. No se puede publicar ni certificar el revision exacto sin credenciales externas.
