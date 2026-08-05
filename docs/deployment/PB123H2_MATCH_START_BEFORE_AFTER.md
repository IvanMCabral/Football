# PB1.2.3H2 — Antes y después del inicio público

Percentiles calculados por nearest-rank. La columna “click→live externo” es el
tiempo observado desde el click de la pantalla pública de carrera hasta que la
ruta `/live` fue visible; la columna “click→live trazado” comienza en la marca
T0 del componente de inicio y no incluye la navegación previa.

## N=10 warm posterior al despliegue

| Muestra | Click→live externo | Click→live trazado | POST→respuesta | Respuesta→stream | Respuesta→primer SSE |
|---:|---:|---:|---:|---:|---:|
| 1 | 519 | 519 | 519 | 304 | 305 |
| 2 | 1.489 | 367 | 367 | 319 | 320 |
| 3 | 4.050 | 284 | 284 | 413 | 414 |
| 4 | 4.431 | 302 | 302 | 515 | 515 |
| 5 | 5.673 | 275 | 275 | 421 | 421 |
| 6 | 5.591 | 1.007 | 1.007 | 288 | 288 |
| 7 | 5.164 | 302 | 301 | 422 | 422 |
| 8 | 3.981 | 373 | 373 | 302 | 302 |
| 9 | 4.388 | 327 | 327 | 218 | 218 |
| 10 | 1.912 | 290 | 290 | 262 | 263 |
| **p50** | **4.050** | **302** | **302** | **304** | **305** |
| **p95 / máximo** | **5.673 / 5.673** | **1.007 / 1.007** | **1.007 / 1.007** | **515 / 515** | **515 / 515** |

## Baseline H1

| Métrica | Antes | Después warm |
|---|---:|---:|
| Click público→live | 5.278 ms (N=1) | 4.050 ms p50 / 5.673 ms p95 |
| POST start→respuesta | no instrumentado | 302 ms p50 / 1.007 ms p95 |
| stream→primer SSE | no instrumentado | 305 ms p50 / 515 ms p95 |
| POST duplicados | no observado | 0 |
| stream duplicados | no observado | 0 |
| state/stream 404 transitorios | no observado | 0 |

La comparación no mezcla la muestra cold con los percentiles warm. El cold
observado en Render se documenta por separado en `cold-start.json`.
