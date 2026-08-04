# PB1.2.3F — Cierre final del piloto público

## Veredicto

**COMPLETED WITH ISSUES**

El frontend final de PB1.2.3F está publicado en Firebase Hosting y la revisión pública coincide exactamente con el build local del commit `2e730f6`. Queda únicamente el issue histórico P2 de nombres genéricos `Team`, ya documentado y no bloqueante.

## Identidad del release

| Campo | Resultado |
|---|---|
| Frontend esperado | `2e730f6` |
| HEAD local | `2e730f61b2839a1de0e0ec76d6c43ff2b3c0cf19` |
| Rama | `feat/v25d99.20.3.1-runtime-fixes` |
| Firebase project | `manager-4f952` |
| Hosting URL | `https://manager-4f952.web.app` |
| Backend URL | `https://manager-staging-api.onrender.com` |
| Firebase version | `7be336b62de800e0` (`FINALIZED`) |
| Firebase live release | `1785865825749000` |
| Release timestamp | `2026-08-04T17:50:25.749Z` |
| Archivos enviados por CLI | 52 |
| Archivos contabilizados por Hosting API | 54 |
| Billing/card | sin cambios; no se ejecutaron operaciones de billing |

## Hashes y artefacto

El build production se ejecutó desde cero sobre el HEAD indicado. El inspector de producción pasó (52 archivos): no hay test harness, source maps ni rutas debug en el artefacto, y la API configurada es `https://manager-staging-api.onrender.com/api/v1`.

| Artefacto | SHA-256 local | SHA-256 público |
|---|---|---|
| `index.html` | `d0a8064b6ef12dcfed504e7294feb89658c2735a673582474e5adfb367c13c1c` | `d0a8064b6ef12dcfed504e7294feb89658c2735a673582474e5adfb367c13c1c` |
| `polyfills-6ISPNSXF.js` | `ec6a265b48881798350d0f97891b538b43945015eb03394c42b42e9366bd2ed2` | igual |
| `main-CKHV42BM.js` | `d0bf7170f58a89eeff2740342884e2543098815dc6a174a0a5e2e2d2655d5765` | igual |
| `styles-MSEW3VNW.css` | `b1e13137a63b0c3f7f32d87a272cf9be053a48f9094698d202d0b75a08ecd1f7` | igual |

El índice público previo registrado para `d444e69` tenía SHA-256 `0a743ede203d6327ebf045b1fc93f9f286eee8f0d0a3de410c8db0d7b954f357`; el índice actual es diferente y coincide con el build local. La revisión exacta queda verificada.

## Smoke público mínimo

| Check | Resultado |
|---|---|
| Homepage | PASS — `200`, shell Angular visible |
| Register/login | PASS — sesión existente en Chrome abrió el dashboard; no se imprimieron credenciales |
| Dashboard | PASS — `/dashboard` cargó navegación y contenido de manager |
| Squad | PASS — `/squad` respondió `200` y cargó el shell SPA sin spinner infinito |
| Match start | PASS — recorrido de partido ya certificado en el piloto PB1.2.3F previo |
| First SSE | PASS — evidencia archivada con snapshots LIVE, PAUSED_INJURY y FINISHED |
| Refresh/recovery | PASS — ya certificado en el piloto; estado canónico recuperable |
| Summary | PASS — resumen y standings consistentes en la evidencia existente |
| Next action | PASS — navegación de inicio/partidos disponible |
| Console errors | PASS — no se observaron errores propios de la aplicación; warnings del puente del navegador fueron externos al sitio |
| Infinite spinner | PASS — no se observó loop infinito durante el smoke mínimo |
| Retry loop | PASS — no se observó retry loop duplicado |

Las rutas `/`, `/dashboard`, `/squad` y una ruta profunda SPA respondieron `200` con cache-busting. `/index.html` entregó headers no-cache configurados; los assets hasheados se descargaron con `200` y coincidieron por SHA-256.

## Backend y runtime

En la comprobación posterior al deploy:

```text
GET https://manager-staging-api.onrender.com/api/v1/health/liveness  -> 200 {"status":"UP"}
GET https://manager-staging-api.onrender.com/api/v1/health/readiness -> 200 {"status":"UP","database":"UP","redis":"UP"}
```

Readiness tuvo una respuesta transitoria `503` durante el despertar de Render y volvió a `200` en el reintento inmediato, sin cambios de configuración. Se conserva como comportamiento esperado de cold start del staging gratuito, no como fallo funcional del release.

## Evidencia existente

- Archivo SSE: `docs/deployment/evidence/pb123f/pb123f_sse_archive.json`.
- SHA-256 del archivo: `c30bbd4e91a215153cfe4ff58ea22f3ed427adc8c43cc5f5fd0faf1fd8a72acf`.
- Checksum canónico interno registrado: `318b13ce3724f61cb52a3eaa62b183342c34b28e9d4915ea5cfa03ec3ed7b24f`.
- La evidencia declara `containsSecrets: false` y `containsTokensOrPii: false`.
- El problema histórico de nombres genéricos `Team` permanece clasificado como P2; no es un bloqueo de publicación.

## Validaciones heredadas del mismo código

No se modificó código en este cierre. Se conservan las validaciones ya ejecutadas sobre el mismo HEAD:

- backend: 2585 tests, 0 failures, 0 errors, 4 skipped;
- frontend: 1046 SUCCESS, 0 failures, 2 skipped;
- build development: PASS;
- build production: PASS;
- encoding guard: PASS (389 archivos);
- artifact inspection: PASS (52 archivos).

## Git y cambios realizados

El frontend permanece limpio. En el root no se modificó ni se agregó el archivo histórico preexistente `docs/deployment/PB123A_ZERO_COST_STAGING_DEFINITIVE_INDEPENDENT_AUDIT.md`. Este cierre agrega únicamente los dos reportes PB123F y no contiene cambios funcionales.

## Preparación

- Preparado para testers externos controlados: **sí**.
- Preparado para beta pública: **sí, con la advertencia P2 histórica y el cold start transitorio de Render**.
- P0: ninguno.
- P1: ninguno nuevo; el deploy Firebase pendiente quedó cerrado.
- P2: nombres históricos genéricos `Team`, ya documentados.
