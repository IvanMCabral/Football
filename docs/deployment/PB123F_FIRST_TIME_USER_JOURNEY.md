# PB1.2.3F — Primer recorrido de usuario

## Cuenta y carrera observadas

Se utilizó una cuenta efímera de piloto, sin registrar credenciales en el repositorio. La carrera pública fue España → Primera División → Real Madrid, dificultad media y velocidad normal.

## Mediciones observadas

| Tramo | Resultado | Tiempo visible | Clasificación |
| --- | --- | ---: | --- |
| Registro → dashboard | Dashboard con 70 clubes y 1680 jugadores | ~10 s | Lenta por despertar |
| Selección de país/división/club | 20 equipos cargados tras un estado inicial de carga | ~12 s | Lenta por backend |
| Crear carrera → plantilla | Carrera creada; plantilla disponible | ~12,7 s + ~15 s | Lenta por carga inicial |
| Auto-selección | 11/11, 4-4-2, química 80/99 | ~5 s | Visible |
| Confirmar alineación → partido | Partido 1 abierto | ~13,4 s | Lenta por backend |
| Partido en vivo | Minutos avanzan; 0-0 final | ~90 s de simulación | Aceptable |
| Lesión | Pausa en 31' para Jude Bellingham | inmediata al evento | Aceptable |
| Resumen | Resultados, tabla y Real Madrid 8º con 1 punto | ~8 s de hidratación | Visible |

El flujo completó registro, carrera, auto-select, alineación, partido, lesión, resumen, tabla, retorno al equipo y recuperación. Las demoras de 8–20 s son compatibles con el cold start de un staging gratuito; no se observó una espera crítica sostenida (>20 s) ni un loop de spinner.

## Estado final observado

- Real Madrid 0–0 Real Oviedo.
- Fecha 1 finalizada a los 90'.
- Tabla persistida: Real Madrid 8º, 1 PJ, 1 punto.
- Plantilla disponible al volver a `Squad`.
