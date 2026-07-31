# MANAGER - Public Beta v1.1 Release Checklist

Fecha: 2026-07-31

Este checklist prepara el MVP 1 congelado para beta pública. No implica cambios de gameplay, mercado, economía, scouting ni motor.

## 1. Decisiones de infraestructura

- [ ] Dominio elegido.
- [ ] DNS delegado a Cloudflare.
- [ ] Subdominio frontend definido: `app.<dominio>` o raíz.
- [ ] Subdominio API definido: `api.<dominio>`.
- [ ] Proveedor frontend elegido.
- [ ] Proveedor backend elegido.
- [ ] Proveedor PostgreSQL elegido.
- [ ] Proveedor Redis elegido.
- [ ] Ambiente staging definido.
- [ ] Ambiente producción definido.
- [ ] Política de límites de beta definida: usuarios, carreras, tráfico esperado.

## 2. Frontend Angular

- [ ] Build development verde.
- [ ] Build production verde.
- [ ] Tests frontend verdes.
- [ ] Output directory confirmado en CI.
- [ ] URL de API configurada por ambiente.
- [ ] Deploy preview habilitado.
- [ ] Deploy production habilitado.
- [ ] HTTPS activo.
- [ ] Custom domain activo.
- [ ] Rollback de frontend probado.

## 3. Backend Spring Boot WebFlux

- [ ] Java 21 confirmado en runtime.
- [ ] Build Maven verde.
- [ ] Suite backend verde.
- [ ] Perfil `prod` definido y probado.
- [ ] Puerto configurable por variable del proveedor.
- [ ] Endpoint health/readiness real agregado antes de beta.
- [ ] CORS limitado al dominio frontend real.
- [ ] Logs sin secretos.
- [ ] Timeouts definidos.
- [ ] Pool R2DBC ajustado a límites del proveedor PostgreSQL.
- [ ] Flyway ejecuta migraciones de forma controlada.
- [ ] Rollback de backend probado.

## 4. PostgreSQL

- [ ] Base production creada.
- [ ] Usuario production con permisos mínimos.
- [ ] SSL requerido.
- [ ] Migraciones Flyway aplicadas.
- [ ] Dataset MVP 1 validado.
- [ ] Backup pre-beta creado.
- [ ] Backup automático diario configurado.
- [ ] Retención definida.
- [ ] Restore drill probado en base temporal.
- [ ] Tiempo de restore documentado.
- [ ] Conexiones máximas revisadas.

## 5. Redis

- [ ] Redis production creado.
- [ ] Autenticación activa.
- [ ] TLS/SSL activo si el proveedor lo soporta.
- [ ] Límite de memoria revisado.
- [ ] Límite de comandos revisado.
- [ ] Eviction policy entendida.
- [ ] Decisión tomada: Redis como cache o Redis como store durable temporal.
- [ ] Si Redis es durable: backup/export/restore definido.
- [ ] Si Redis es cache: recuperación validada desde PostgreSQL.

## 6. Secrets

- [ ] `.env` no se sube.
- [ ] Secretos cargados en proveedor backend.
- [ ] Secretos cargados en CI.
- [ ] Secretos de staging separados de production.
- [ ] `JWT_SECRET` production rotado.
- [ ] Passwords DB/Redis no impresos en logs.
- [ ] Tokens de deploy con permisos mínimos.
- [ ] Plan de rotación documentado.

## 7. CI/CD

- [ ] Pipeline Pull Request:
  - [ ] backend compile;
  - [ ] backend tests;
  - [ ] frontend development build;
  - [ ] frontend production build;
  - [ ] frontend tests;
  - [ ] `git diff --check`.
- [ ] Pipeline main:
  - [ ] build backend;
  - [ ] deploy staging;
  - [ ] smoke staging;
  - [ ] deploy frontend production;
  - [ ] deploy backend production con aprobación manual inicial.
- [ ] Migraciones DB separadas o claramente ordenadas.
- [ ] Backup automático antes de migraciones productivas.
- [ ] Evidencia de release guardada.

## 8. Monitoring y alertas

- [ ] Uptime frontend.
- [ ] Uptime backend health.
- [ ] Smoke superficial autenticación/API.
- [ ] Alertas configuradas.
- [ ] Sentry frontend.
- [ ] Sentry backend o equivalente.
- [ ] Logs accesibles por al menos 7 días.
- [ ] Métricas CPU/memoria/restarts.
- [ ] Métricas DB: conexiones, latencia, storage.
- [ ] Métricas Redis: comandos, memoria, errores.

## 9. Seguridad mínima

- [ ] HTTPS obligatorio.
- [ ] CORS cerrado.
- [ ] Headers básicos revisados.
- [ ] Rate limiting definido para auth.
- [ ] No hay endpoints debug/test-harness expuestos en `prod`.
- [ ] Credenciales locales no funcionan en production.
- [ ] Backups cifrados o protegidos.
- [ ] Acceso a proveedores con 2FA.

## 10. Prueba funcional beta

- [ ] Crear usuario.
- [ ] Login.
- [ ] Crear carrera.
- [ ] Elegir España.
- [ ] Elegir Argentina.
- [ ] Elegir Brasil.
- [ ] Cargar ligas.
- [ ] Cargar clubes.
- [ ] Cargar planteles.
- [ ] Auto-select.
- [ ] Guardar lineup.
- [ ] Iniciar fixture.
- [ ] Simular ronda.
- [ ] Abrir partido detallado.
- [ ] Ver eventos.
- [ ] Ver ratings.
- [ ] Ver estadísticas.
- [ ] Ver standings.
- [ ] Reiniciar backend.
- [ ] Confirmar recuperación.
- [ ] Confirmar que frontend sigue operativo.

## 11. Rollback

- [ ] Rollback frontend probado.
- [ ] Rollback backend probado.
- [ ] Backup DB antes del release.
- [ ] Procedimiento restore escrito.
- [ ] Responsable de rollback definido.
- [ ] Tiempo máximo aceptable de recuperación definido.

## 12. Go / No-Go

Go solo si:

- [ ] Frontend verde.
- [ ] Backend verde.
- [ ] DB validada.
- [ ] Redis validado.
- [ ] HTTPS y dominio activos.
- [ ] Secrets production rotados.
- [ ] Backups activos.
- [ ] Restore probado.
- [ ] Monitoring activo.
- [ ] No hay endpoints debug expuestos.
- [ ] Smoke producción aprobado.

No-Go si:

- [ ] No existe restore probado.
- [ ] Redis contiene estado durable sin estrategia de backup.
- [ ] CORS está abierto.
- [ ] Secrets locales o débiles siguen activos.
- [ ] No hay rollback claro.
- [ ] El backend no tiene health/readiness real.
