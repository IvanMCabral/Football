# MANAGER - Public Beta v1.1 Cost Estimate

Fecha: 2026-07-31

Objetivo: estimar costos para publicar el MVP 1 en Internet priorizando opciones gratuitas o de muy bajo costo.

Los precios y límites de free tier cambian. Antes de contratar o abrir beta, confirmar nuevamente en las páginas oficiales de cada proveedor.

## 1. Escenario recomendado

| Componente | Proveedor | Costo estimado | Comentario |
|---|---|---:|---|
| Dominio | Registrador a elección | USD 10-20/año | Costo anual típico para dominio común. |
| DNS/HTTPS/CDN | Cloudflare Free | USD 0/mes | DNS, SSL y CDN para frontend. |
| Frontend Angular | Cloudflare Pages Free | USD 0/mes | Muy buen encaje para build estático. |
| Backend Spring Boot | Railway Hobby | USD 5+/mes | Costo bajo, simple de operar. Vigilar uso. |
| PostgreSQL | Neon Free/Launch o Supabase Free/Pro | USD 0-25/mes | Free para beta chica; paid si se necesita backup/retención/SLA. |
| Redis | Upstash Free/Pay as you go | USD 0-10/mes | Free si el volumen de comandos entra en cuota. |
| Monitoring uptime | UptimeRobot/Better Stack Free | USD 0/mes | Suficiente para beta inicial. |
| Error tracking | Sentry Free | USD 0/mes | Suficiente para bajo volumen. |
| Backups externos | Cloudflare R2/Backblaze B2/S3 compatible | USD 0-2/mes | Depende de tamaño y retención. |

Total mensual esperado:

- Beta chica: USD 5-10/mes.
- Beta con base de datos paid o más tráfico: USD 15-40/mes.
- Dominio aparte: USD 10-20/año.

## 2. Escenario costo cero aproximado

| Componente | Proveedor | Costo estimado | Riesgo |
|---|---|---:|---|
| Frontend | Cloudflare Pages Free | USD 0 | Bajo. |
| Backend | Oracle Cloud Always Free VM | USD 0 | Medio/alto por operación, seguridad y disponibilidad de recursos. |
| PostgreSQL | Neon Free o Postgres self-host | USD 0 | Neon free tiene límites; self-host requiere backups serios. |
| Redis | Upstash Free o Redis self-host | USD 0 | Upstash tiene cuota; self-host requiere operación. |
| DNS/HTTPS | Cloudflare Free | USD 0 | Bajo. |

Conclusión: viable para pruebas técnicas, pero menos recomendable para una beta pública con usuarios reales.

## 3. Escenario PaaS simple

| Componente | Proveedor | Costo estimado | Comentario |
|---|---|---:|---|
| Frontend | Cloudflare Pages o Vercel | USD 0 | Cloudflare recomendado para static Angular. |
| Backend | Render Starter o Railway Hobby | USD 5-10+/mes | Evitar servicios free dormidos. |
| PostgreSQL | Supabase Pro / Neon Launch / proveedor PaaS | USD 19-25+/mes | Mejor para backups/retención. |
| Redis | Upstash paid o proveedor PaaS | USD 0-10+/mes | Depende del volumen. |
| Monitoring/logs | Free tiers | USD 0-10/mes | Sube si se centralizan logs. |

Total mensual esperado: USD 25-60/mes.

## 4. Comparación resumida de costos y límites

| Proveedor | Rol posible | Bajo costo / free tier | Observación de costo |
|---|---|---|---|
| Railway | Backend, DB, Redis | Free limitado; Hobby pago bajo | Muy práctico, pero no asumir producción gratis. |
| Render | Backend, static, DB | Free web con sleep; Postgres Free temporal | Para producción real conviene paid. |
| Fly.io | Backend container | Trial/créditos; sin free continuo estable | Potente, pero puede facturar por uso. |
| Oracle Cloud Free | VM/self-host | Always Free | Costo bajo, operación alta. |
| Supabase | PostgreSQL | Free con límites | Buena opción si 500 MB alcanza; paid mejora backups/SLA. |
| Neon | PostgreSQL | Free con allowances | Muy buen candidato para beta chica. |
| Upstash | Redis | Free con 256 MB y 500K comandos/mes | Vigilar comandos si hay muchas live sessions. |
| Cloudflare Pages | Frontend | Free generoso | Recomendación principal para Angular. |
| Cloudflare Tunnel | Exponer servicios | Free útil | Mejor para self-host/ops, no como hosting principal. |
| Vercel | Frontend | Hobby free | Excelente DX; revisar condiciones de uso para beta pública. |

## 5. Riesgos de costo

### Backend

- Simulaciones o tráfico inesperado pueden subir CPU/memoria.
- Railway/Fly cobran por uso; configurar límites y alertas.
- Render free duerme, lo que da mala experiencia.

### PostgreSQL

- El dataset inicial es razonable, pero crece con usuarios, carreras, fixtures, eventos y estadísticas.
- Backups y retención suelen requerir plan pago.
- Serverless Postgres puede tener límites de conexiones.

### Redis

- Upstash cobra por comandos fuera del free tier.
- Live sessions pueden generar muchos comandos si el partido minuto a minuto se consulta frecuentemente.
- Si Redis contiene estado durable, el costo no es solo comandos: también importa persistencia/restore.

### Logs

- Logs verbosos pueden subir costos rápidamente.
- Mantener nivel `INFO` y evitar logs por minuto/jugador salvo debug temporal.

## 6. Recomendación económica

Para una beta pública controlada:

1. Cloudflare Pages: USD 0.
2. Cloudflare DNS/HTTPS: USD 0.
3. Railway Hobby backend: desde USD 5/mes.
4. Neon Free para comenzar; pasar a paid si se abre a más usuarios o se necesita mejor retención.
5. Upstash Free para comenzar; pasar a paid si los comandos superan cuota.
6. UptimeRobot/Better Stack Free.
7. Sentry Free.
8. Backups externos baratos.

Presupuesto inicial realista:

- Mínimo profesional: USD 5-10/mes.
- Recomendado con margen: USD 20-40/mes.
- Más dominio: USD 10-20/año.

## 7. Umbrales para pasar a pago

Pasar a planes pagos cuando ocurra cualquiera:

- más de 20-50 testers activos;
- Redis supera 70% de comandos mensuales;
- DB supera 70% del storage free;
- se necesita backup PITR o retención seria;
- cold starts afectan la experiencia;
- logs/monitoring gratuitos dejan de alcanzar;
- se anuncia beta fuera de círculo cerrado.

## 8. Fuentes de precios y límites

- Railway pricing: https://railway.com/pricing
- Railway plans docs: https://docs.railway.com/reference/pricing/plans
- Render free tier docs: https://render.com/docs/free
- Render PostgreSQL docs: https://render.com/docs/postgresql-creating-connecting
- Fly.io pricing/docs: https://fly.io/docs/about/pricing/
- Oracle Cloud Free Tier: https://www.oracle.com/cloud/free/
- Oracle Always Free resources: https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm
- Supabase pricing: https://supabase.com/pricing
- Neon pricing: https://neon.com/pricing
- Neon plans docs: https://neon.com/docs/introduction/plans
- Upstash Redis pricing: https://upstash.com/pricing/redis
- Cloudflare Pages: https://pages.cloudflare.com/
- Cloudflare Workers and Pages pricing: https://developers.cloudflare.com/workers/platform/pricing/
- Vercel plans: https://vercel.com/docs/plans
