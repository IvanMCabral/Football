# PB1.2.3F — Evidencia SSE sanitizada

## Archivo

`docs/deployment/evidence/pb123f/pb123f_sse_archive.json`

## Sesión

- Carrera/fecha: `5281bf46-0001-4a62-b2c0-4d1d04acc942`, ronda 1.
- Partido: Real Madrid 0–0 Real Oviedo.
- Secuencias observadas: 1 (12', LIVE), 2 (31', PAUSED_INJURY), 3 (90', FINISHED).
- Minuto y secuencia monotónicos: sí.
- Resultado final consistente con resumen y tabla: sí.

El archivo conserva sólo estado operativo sanitizado: secuencia, timestamp de captura, minuto, estado, marcador y cantidad de eventos visible. No incluye tokens, cookies, correos, contraseñas, payloads completos, claves de Redis ni datos personales. El checksum documentado es SHA-256 del bloque canónico `snapshots` + `assertions`.

La evidencia es de observación pública renderizada, no una copia de mensajes privados del broker SSE. El transporte físico queda fuera del alcance del navegador controlado y no se afirma más de lo observado.
