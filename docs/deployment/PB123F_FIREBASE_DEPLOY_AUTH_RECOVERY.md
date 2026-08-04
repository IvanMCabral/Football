# PB1.2.3F — Firebase deploy auth recovery

## Alcance

Este documento registra la recuperación de la autenticación de Firebase CLI y la publicación del frontend ya validado. No se modificó código de aplicación, gameplay, backend, base de datos, plan de Firebase ni configuración de billing.

## Estado local verificado

| Elemento | Evidencia |
|---|---|
| Rama frontend | `feat/v25d99.20.3.1-runtime-fixes` |
| HEAD frontend | `2e730f61b2839a1de0e0ec76d6c43ff2b3c0cf19` |
| Commit esperado | `2e730f6` contenido en HEAD |
| Firebase project | `manager-4f952` |
| Hosting site | `manager-4f952` |
| Directorio publicado | `dist/demo/browser` |
| Firebase CLI | `15.25.1` |
| Build index SHA-256 | `d0a8064b6ef12dcfed504e7294feb89658c2735a673582474e5adfb367c13c1c` |
| `main-CKHV42BM.js` SHA-256 | `d0bf7170f58a89eeff2740342884e2543098815dc6a174a0a5e2e2d2655d5765` |
| `polyfills-6ISPNSXF.js` SHA-256 | `ec6a265b48881798350d0f97891b538b43945015eb03394c42b42e9366bd2ed2` |
| `styles-MSEW3VNW.css` SHA-256 | `b1e13137a63b0c3f7f32d87a272cf9be053a48f9094698d202d0b75a08ecd1f7` |

El build se generó nuevamente antes del deploy. El guard de encoding inspeccionó 389 archivos y el inspector del artefacto inspeccionó 52 archivos: ambos pasaron. No se publicaron source maps ni chunks del test harness; el entorno productivo usa la API absoluta de Render y `enableDebugRoutes: false`.

## Recuperación de autenticación

La primera ejecución desde la terminal no interactiva fue rechazada por la CLI (`Cannot run login in non-interactive mode`). Se abrió el flujo oficial `firebase login --reauth` en una terminal interactiva, que utilizó la sesión ya abierta en el perfil real de Chrome (`Chrome.UserData.Profile1`).

Se completó el consentimiento de Firebase CLI en esa sesión existente, sin crear perfil, incógnito, token permanente ni credencial en el repositorio. La verificación posterior fue:

```text
firebase login:list  -> cuenta autenticada presente (identificador omitido)
firebase projects:list -> manager-4f952 visible y ACTIVE
```

No se mostró ni almacenó ningún secreto. No se ejecutó ninguna operación de billing ni se agregó tarjeta.

## Deploy realizado

Comando exacto:

```text
npx --yes firebase-tools deploy --only hosting --project manager-4f952
```

Resultado: exit code `0`, `Deploy complete`, 52 archivos encontrados en `dist/demo/browser`, solo Firebase Hosting.

La API de canales de Firebase confirmó:

- versión finalizada: `projects/manager-4f952/sites/manager-4f952/versions/7be336b62de800e0`;
- release live: `projects/manager-4f952/sites/manager-4f952/channels/live/releases/1785865825749000`;
- release time: `2026-08-04T17:50:25.749Z`;
- estado de versión: `FINALIZED`;
- 54 archivos contabilizados por la API de Hosting;
- URL: `https://manager-4f952.web.app`.

## Revisión pública por hash

Se consultó el índice con cache-busting y se descargaron los assets activos. El índice público y los tres assets iniciales coincidieron byte a byte con el build local:

| Artefacto | Local | Público | Resultado |
|---|---|---|---|
| `index.html` | `d0a8064b6ef12dcfed504e7294feb89658c2735a673582474e5adfb367c13c1c` | igual | PASS |
| `polyfills-6ISPNSXF.js` | `ec6a265b48881798350d0f97891b538b43945015eb03394c42b42e9366bd2ed2` | igual | PASS |
| `main-CKHV42BM.js` | `d0bf7170f58a89eeff2740342884e2543098815dc6a174a0a5e2e2d2655d5765` | igual | PASS |
| `styles-MSEW3VNW.css` | `b1e13137a63b0c3f7f32d87a272cf9be053a48f9094698d202d0b75a08ecd1f7` | igual | PASS |

El índice anterior registrado para `d444e69` era `0a743ede203d6327ebf045b1fc93f9f286eee8f0d0a3de410c8db0d7b954f357`; el índice actual es distinto y coincide con el build de `2e730f6`. La revisión pública ya no sirve el artefacto anterior.

`/index.html` respondió `200` con `Cache-Control: no-cache, no-store, must-revalidate`, `X-Content-Type-Options: nosniff` y `Referrer-Policy: strict-origin-when-cross-origin`. La raíz reescrita y las rutas SPA respondieron `200`; sus headers observados por Firebase CDN fueron `Cache-Control: max-age=3600`.

## Resultado

La autenticación CLI fue recuperada mediante el Chrome existente y el release exacto de `2e730f6` quedó publicado y verificado por hashes. El cierre funcional y la evidencia pública se encuentran en `PB123F_FINAL_PUBLIC_PILOT_CLOSURE.md`.
