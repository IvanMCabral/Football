# PB1.2.2 Spring Security Advisory Remediation

Date: 2026-08-02

## Baseline and policy

The independent Docker audit recorded three fixable HIGH advisories on the PB1.2.2 run #22. They were runtime dependencies managed by the Spring Boot BOM, not false positives. The remediation uses the smallest available Spring Boot 3.5 patch update and keeps the Spring Framework, Spring Data and Spring Security lines aligned. No major upgrade or gameplay change was made.

| Component | Before | After | Source |
|---|---:|---:|---|
| Spring Boot parent | 3.5.14 | 3.5.16 | Maven Central metadata |
| Spring Framework | 6.2.18 | 6.2.19 | Spring Boot 3.5.16 BOM |
| Spring Data Commons | 3.5.11 | 3.5.13 | Spring Boot 3.5.16 BOM |
| Spring Security Web | 6.5.10 | 6.5.11 | Spring Boot 3.5.16 BOM |
| Java | 21 | 21 | Existing runtime baseline |

## Advisory inventory

### CVE-2026-41695 — spring-data-commons

- Vulnerable version observed: `3.5.11`.
- Fixed versions reported by the advisory: `3.5.12` and `4.0.6`.
- Final version: `3.5.13` through `spring-boot-starter-data-r2dbc` and the Spring Boot BOM.
- Dependency tree: `spring-boot-starter-data-r2dbc -> spring-data-r2dbc -> spring-data-commons`.
- Exploit condition: externally controlled generic property-path processing.
- MANAGER applicability: runtime dependency; the application does not expose Spring Data REST or a generic property-path API to untrusted callers. The dependency was nevertheless upgraded because a supported fix is available.
- Previous mitigation: restricted application API surface and ownership checks.
- Applied change: Spring Boot parent `3.5.14 -> 3.5.16`.

### CVE-2026-41850 — spring-expression

- Vulnerable version observed: `6.2.18`.
- Fixed versions reported by the advisory: `6.2.19` and `7.0.8`.
- Final version: `6.2.19`, managed by Spring Boot 3.5.16.
- Dependency tree: `spring-boot-starter-security -> spring-security-web -> spring-expression`.
- Exploit condition: evaluation of attacker-controlled SpEL.
- MANAGER applicability: runtime dependency; no user-controlled SpEL parser or expression endpoint is exposed. The dependency was nevertheless upgraded.
- Previous mitigation: no externally supplied expressions and normal Spring Security authorization.
- Applied change: Spring Boot parent `3.5.14 -> 3.5.16`.

### CVE-2026-41842 — spring-webflux

- Vulnerable version observed: `6.2.18`.
- Fixed versions reported by the advisory: `6.2.19` and `7.0.8`.
- Final version: `6.2.19`, managed by Spring Boot 3.5.16.
- Dependency tree: `spring-boot-starter-webflux -> spring-webflux`.
- Exploit condition: the advisory's affected resource-serving behavior.
- MANAGER applicability: WebFlux is a real runtime dependency; production does not serve arbitrary static filesystem resources. The dependency was nevertheless upgraded.
- Previous mitigation: classpath resources only and no user-controlled filesystem path mapping.
- Applied change: Spring Boot parent `3.5.14 -> 3.5.16`.

## Validation

- Focused production runtime guard: PASS.
- Dependency trees after the change report Spring Data Commons `3.5.13`, Spring Expression `6.2.19` and Spring WebFlux `6.2.19`.
- Full backend and frontend suites are rerun on the final HEAD before the PB1.2.2 P1 verdict.
- The final Docker smoke must report `CRITICAL=0` and `HIGH=0`; a remaining HIGH keeps the remediation open.

## Ownership and deadline

The Spring Boot parent remains the single owner of these aligned versions. Future Spring advisories must be handled by a patch-level BOM update before public beta; individual dependency overrides are not permitted without a dependency-tree justification.
