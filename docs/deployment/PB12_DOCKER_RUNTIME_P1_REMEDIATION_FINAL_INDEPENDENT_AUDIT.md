# PB1.2.2 P1 Remediation Final Independent Audit

Fecha: 2026-08-02

## Veredicto

**PB1.2.2 P1 REMEDIATION REJECTED**

La remediación técnica de las tres CVEs, la higiene de temporales, el pinning de actions e imágenes y el run remoto #24 están presentes y reportan PASS. Sin embargo, el gate crítico de coherencia run/HEAD no pasa: el run #24 ejecutó `87e28c8f`, mientras que el HEAD local y remoto actual es `3e98abf046291baa62d29046e29db020fb181e4d`. El commit posterior es documental, pero sigue siendo parte del HEAD final y no fue ejecutado por el run declarado. El rechazo no se debe a la imposibilidad de descargar el ZIP.

También queda un P0 de integridad del manifest: `containsSecrets:false` se escribe como valor constante para cada archivo, no como resultado de una comprobación por archivo. El manifest se genera después del secret scan y queda fuera del alcance de ese scan.

## 1. Baseline Git

- Rama: `feat/v25d99.20.3.1-runtime-fixes`.
- HEAD local: `3e98abf046291baa62d29046e29db020fb181e4d` (`3e98abf0`).
- HEAD remoto de la rama: `3e98abf046291baa62d29046e29db020fb181e4d`.
- Estado root antes de crear este informe: limpio.
- Estado frontend `front-ciber/project`: limpio.
- `git diff --check` actual: limpio.
- No se observan cambios de gameplay, simulación o dataset en la remediación.
- Docker no está disponible localmente; no se afirma build local.

El rango contiene el commit documental `3e98abf0` después de `87e28c8f`. El rango `87e28c8f..3e98abf0` modifica solamente documentación, pero esa diferencia impide afirmar que el run remoto auditó el HEAD exacto.

## 2. Commits auditados

| Commit | Mensaje | Alcance observado |
|---|---|---|
| `2967b9e2` | docs: record definitive PB1.2.2 Docker runtime audit | Auditoría histórica |
| `4357c81e` | Upgrade Spring patch dependencies for security advisories | Spring Boot `3.5.14 -> 3.5.16` |
| `76075f09` | Harden Docker smoke artifact hygiene | runner, workflow, Dockerfile y guard |
| `4c68e26f` | Strengthen Docker artifact hygiene guards | guard y self-test de higiene |
| `2801015e` | Document PB1.2.2 final P1 remediation | documentación de remediación |
| `87e28c8f` | Expose final Docker artifact hygiene evidence | result summary final |
| `3e98abf0` | Record final PB1.2.2 P1 evidence | documentación posterior al run #24 |

No hay inconsistencia de existencia de commits. Sí existe inconsistencia entre el `HEAD final` declarado (`87e28c8f`) y el HEAD real (`3e98abf0`).

## 3. Spring security remediation

`pom.xml` usa Spring Boot parent `3.5.16`, sin override individual ni major upgrade.

Dependency trees frescos:

- `spring-data-commons`: `3.5.13`, vía `spring-boot-starter-data-r2dbc -> spring-data-r2dbc`.
- `spring-expression`: `6.2.19`, vía `spring-boot-starter-security -> spring-security-web`.
- `spring-webflux`: `6.2.19`, vía `spring-boot-starter-webflux`.

No están presentes en el árbol actual `spring-data-commons:3.5.11`, `spring-expression:6.2.18` ni `spring-webflux:6.2.18`. No se observa override incompatible ni cambio major.

Clasificación individual: `CVE-2026-41695 REMEDIATED`, `CVE-2026-41850 REMEDIATED` y `CVE-2026-41842 REMEDIATED`. Las advisories primarias describen las versiones corregidas: [CVE-2026-41695](https://spring.io/security/cve-2026-41695/), [CVE-2026-41850](https://spring.io/security/cve-2026-41850/) y [CVE-2026-41842](https://spring.io/security/cve-2026-41842/).

## 4. Workflow del HEAD

El workflow actual contiene:

- `permissions: contents: read`.
- checkout fijado por SHA completo con comentario `v4.2.2`.
- SBOM, Trivy y upload-artifact fijados por SHA completo y comentario de versión.
- credenciales efímeras enmascaradas; no secretos productivos.
- build Docker real y lifecycle smoke.
- SBOM CycloneDX, Trivy, secret scan final, manifest y upload.
- `if: always()` limitado a evidencia/cleanup/scans necesarios.
- sin `continue-on-error` en gates críticos.
- sin publicación de imagen.
- sin `docker system prune`.

Las actions están correctamente pinned en el HEAD actual y también en el revision que ejecutó el run #24.

## 5. Dockerfile y supply chain

El Dockerfile usa build y runtime separados, usuario `manager`, healthcheck con `curl`, entrypoint `exec java`, sin `latest` ni secretos.

Imágenes fijadas por digest:

- build: `maven:3.9.11-eclipse-temurin-21@sha256:463a1849665463254b2dd56e3a5b316f1596bc93d0571065c06ea05bb48ab8f4`.
- runtime: `eclipse-temurin:21.0.11_10-jre-alpine-3.23@sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645`.

Los comentarios documentan el tag humano, la plataforma linux/amd64 y la verificación de digest. La renovación futura de digests es P1 de política, no un bloqueo de esta remediación.

## 6. Runner y AUTH_TMP

El runner actual:

- crea `AUTH_TMP` dentro de `$PB12_RUN_DIR/auth-tmp`;
- aplica `umask 077`, `mkdir` y permisos `700`;
- no lo sube mientras existe;
- lo elimina en cleanup normal y de error;
- verifica que desapareció;
- marca `authTempExists` y `authTempCleanupVerified` en el result JSON;
- revoca PASS si la eliminación falla.

El run #24 publicó `authTempExists=false` y `authTempCleanupVerified=true`.

La prueba negativa `tools/test-pb12-artifact-hygiene.sh` existe y fuerza un fallo de eliminación. En esta estación se ejecutó, pero quedó `SKIPPED` porque el entorno Bash no dispone de `jq`; no se lo cuenta como PASS local. El guard Java sí exige la presencia de las comprobaciones críticas.

## 7. Secret scan final

El workflow ejecuta un secret scan después de que el runner captura logs finales y termina el cleanup, antes del upload. Busca bearer tokens, JWT/Redis/DB secrets, URLs con credenciales, `.env` y cookies en `$PB12_RUN_DIR` y en `target/pb12-docker-smoke-result.json`.

El run #24 publicó `finalArtifactSecretScanPassed=true` y `secretLeaksDetected=0`.

P1 de alcance documental: el scan excluye `pb12-final-secret-scan.json` y `pb12-artifact-manifest.json`; ambos se generan durante el mismo step, y el manifest se genera después del scan. Los contenidos generados son estructuralmente sanitizados, pero la afirmación de que se escanea exactamente todo lo subido no es literal.

## 8. Manifest

El manifest se genera dinámicamente y lista archivos, tamaño, SHA-256 y tipo. Se incluye en el artifact.

P0: en el bloque Python del workflow, cada entrada se construye con:

```python
'containsSecrets': False
```

Ese campo crítico no deriva del secret scan ni de una inspección por archivo; es una constante. Además, el manifest se crea después del scan y queda excluido de él. La metadata de paths, tamaños y hashes sí es dinámica, pero `containsSecrets=false` no es evidencia calculada. Esto impide cerrar P1 como aprobado bajo el criterio estricto declarado.

## 9. Run remoto #24

Fuente primaria: [GitHub Actions run #24](https://github.com/IvanMCabral/Football/actions/runs/30769400337).

- Run ID: `30769400337`.
- Run number: `24`.
- Conclusión: `success`.
- Head SHA del run: `87e28c8fb1547e8c047aab881760f7ee4d06007c`.
- Branch: `feat/v25d99.20.3.1-runtime-fixes`.
- Workflow: `PB1.2.2 Docker smoke`.
- Duración total: `2m05s`; job `2m02s`.
- Artifact: uno, producido y no expirado al momento de la consulta.

## 10. Coherencia run/HEAD

Resultado: **FAIL crítico**.

| Referencia | SHA |
|---|---|
| Run #24 | `87e28c8fb1547e8c047aab881760f7ee4d06007c` |
| HEAD declarado por el pedido | `87e28c8f` |
| HEAD local real | `3e98abf046291baa62d29046e29db020fb181e4d` |
| HEAD remoto actual | `3e98abf046291baa62d29046e29db020fb181e4d` |

El run #24 fue real y exitoso, pero no auditó el HEAD actual. El run #22 anterior tampoco puede utilizarse como evidencia final.

## 11. Resultado remoto contrastado

El summary público del run #24 publica y el runner de `87e28c8f` sustenta:

- Docker build: PASS.
- Image ID: `sha256:69c0787e75da31ebb907d42b0cac75e1c9b4244924607f02bf513e618337b203`.
- Image size: `258928412` bytes.
- Runtime user/UID: `manager` / `122`.
- Java PID 1: `true`.
- Healthcheck: `true`.
- Liveness/readiness: `200/200`.
- Register/login/me: PASS.
- Career creada/recuperada: PASS.
- Flyway run 1/run 2: `1/1`.
- Second startup: `true`.
- `docker stop=true`; `docker kill=false`.
- Graceful markers y orden: `true/true`.
- Shutdown: `2507 ms`; exit code `143`.
- Redis-down readiness: `503`; el runner exige liveness `200`.
- PostgreSQL-down readiness: `503`; el runner exige liveness `200`.
- Residual containers/networks: `0/0`.
- Cleanup verified: `true`.
- Auth temp: `false/true`.
- Final secret scan: `true`; leaks `0`.
- Trivy: `0 critical / 0 high`.

Estos datos provienen del summary/metadata remotos y del código versionado del runner; el ZIP no fue inspeccionado localmente.

## 12. Artifact metadata

- Nombre: `pb12-docker-smoke-30769400337`.
- Tamaño visible en GitHub: `88.9 KB`; API: `91058` bytes.
- Digest: `sha256:70d557c0309a5f0bc64daa8c71ba160f843cceb21d99e7cc7dfd76afd0947bd0`.
- Artifact ID: `8840025768`.
- Asociado al run: `30769400337` / `#24`.
- Retención visible por API: creado `2026-08-02T22:09:15Z`, expira `2026-08-09T22:09:14Z`, `expired=false`.
- ZIP descargado localmente: no.

La respuesta HTTP 401 del endpoint de descarga se clasifica como **EXTERNAL EVIDENCE ACCESS LIMITATION**, no como runtime failure.

## 13. Backend tests

Evidencia local fresca sobre el HEAD actual:

- `mvn -q -DskipTests test-compile`: PASS.
- `mvn -q test`: PASS.
- Reports: `2779` tests, `0` failures, `0` errors, `4` skipped.

El esperado documental era `2572`; la diferencia es `+207` tests. La suite está verde, por lo que no se clasifica como regresión. La documentación debe actualizarse como P1 de consistencia.

## 14. Frontend

- `npm run pretest`: PASS; encoding guard, `385` archivos.
- Development build: PASS.
- Production build: PASS.
- `inspect-production-artifact.mjs`: PASS; `52` archivos.
- ChromeHeadless: `1029 SUCCESS`, `0` failures, `2` skipped.

## 15. Documentation consistency

La documentación de remediación describe correctamente Spring Boot `3.5.16`, las tres CVEs cerradas, run #24, artifact digest, Trivy `0/0`, hygiene de AUTH_TMP y ausencia de deploy cloud.

Inconsistencias detectadas:

- `PB12_REMAINING_CLOUD_GATES.md` todavía enlaza el run #22 como evidencia del gate Docker, aunque el run final es #24.
- Varios documentos declaran `2572` tests; la suite fresca del HEAD real es `2779`.
- Los documentos declaran que #24 ejecutó el “exact HEAD `87e28c8f`”, pero el branch actual avanzó a `3e98abf0`.
- El manifest se describe como hygiene evidence, pero `containsSecrets:false` es constante y el manifest no entra en el secret scan.

Las auditorías históricas no fueron modificadas.

## 16. P0

1. **Run/HEAD mismatch:** el run remoto final #24 no corresponde al HEAD local/remoto actual `3e98abf0`.
2. **Manifest trust field hardcoded:** `containsSecrets:false` no es calculado por archivo y el manifest se genera fuera del alcance del secret scan.

No se encontraron CVEs pendientes, HIGH/CRITICAL en el summary remoto, AUTH_TMP residual, cleanup residual, force kill en PASS ni suite roja.

## 17. P1

- Descargar/revisar el artifact requiere autenticación desde esta estación; la limitación externa es aceptable como evidencia secundaria, no como causa del rechazo.
- Generar un nuevo run después de que el branch quede en el HEAD exacto a auditar.
- Hacer que `containsSecrets` sea derivado de una verificación real y escanear el manifest/secret-scan report final o excluirlos explícitamente del conjunto subido con justificación.
- Actualizar el enlace de `PB12_REMAINING_CLOUD_GATES.md` de run #22 a run #24.
- Actualizar los conteos documentales a `2779` o explicar formalmente el universo de tests declarado.
- Mantener política de renovación de digests, provenance/firma, staging cloud, backup/restore, SSE y observabilidad.

## 18. Readiness PB1.2.3

**No preparado para cierre final PB1.2.3 en este estado**, debido a los P0 de coherencia e integridad del manifest.

Una vez que exista un run verde sobre el HEAD real y el manifest deje de afirmar campos hardcodeados, permanecen como gates separados: managed PostgreSQL/Redis, TLS, backup/restore, deployment cloud, routing/CORS/HTTPS, SSE, observabilidad y rollback.

Docker-ready no equivale a Internet-ready.

## Conclusión

La remediación sustantiva está implementada: Spring queda en versiones corregidas, actions e imágenes están pinned, AUTH_TMP se limpia, el secret scan final existe, el run #24 es público y exitoso, Trivy es `0/0` y las suites locales están verdes. No obstante, la auditoría independiente no puede cerrar P1 porque el run remoto no corresponde al HEAD actual y el manifest contiene un campo de secreto hardcodeado fuera del scan. El veredicto es **PB1.2.2 P1 REMEDIATION REJECTED**.
