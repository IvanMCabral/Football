# MVP 1 Data Sources and Licensing

Consulta: 2026-07-29.

## Política

El dataset MVP 1 separa identidad pública mínima de competiciones/clubes y datos generados de jugadores. No se copian planteles reales completos ni atributos comerciales. Los jugadores importados por esta fase son ficticios, determinísticos y marcados con `source_system = manager-mvp1-generated`.

## Fuentes públicas verificables

| Área | Fuente | Uso | Restricción |
| --- | --- | --- | --- |
| España primera división | LaLiga official clubs page, `https://www.laliga.com/en-GB/laliga-easports/clubs` | Nombres de clubes y alcance de 20 equipos | Identidad pública; no se copian estadísticas ni planteles. |
| Argentina primera división | AFA official 2026 draw note, `https://www.afa.com.ar/a/posts/se-realizo-el-sorteo-de-la-liga-profesional-2026-ya-se-conocen-los-grupos-del-torneo-apertura-y-torneo-clausura` | Formato 2026 y clubes de referencia | Identidad pública; no se copian planteles. |
| Brasil Serie A | CBF official Serie A teams/table pages, `https://www.cbf.com.br/futebol-brasileiro/times/campeonato-brasileiro/serie-a` and `https://www.cbf.com.br/futebol-brasileiro/tabelas/campeonato-brasileiro/serie-a` | Clubes de Serie A 2026 | Identidad pública; no se copian planteles. |
| Referencia abierta | football.db, `https://openfootball.github.io/` | Referencia de filosofía open data y licenciamiento abierto | No se importa automáticamente en esta fase. |

## Datos generados

- Jugadores, edades, alturas, atributos, valores de mercado y rasgos especiales son generados de forma determinística.
- No representan planteles reales.
- La política está versionada en `src/main/resources/data/mvp1/players/generated-player-policy.json`.

## Datos no disponibles legalmente en esta fase

- Planteles reales completos con atributos jugables.
- Ratings estilo videojuegos comerciales.
- Salarios/valores oficiales de mercado individualizados.

## Restricción operacional

Si en el futuro se incorporan jugadores reales, debe agregarse una fuente legal reproducible por entidad o dataset antes de importar.
