# PB1.2.2 Docker Security Scan Policy

## SBOM

Every workflow run generates a CycloneDX JSON SBOM with Anchore Syft through anchore/sbom-action@v0.17.0. The SBOM is retained for seven days with the run artifact.

## Vulnerabilities

Trivy action v0.36.0 scans OS and library packages with severity CRITICAL,HIGH and --ignore-unfixed. The result is preserved as JSON. A fixable CRITICAL finding fails the workflow. HIGH findings are counted and reported for remediation; they are not hidden by an automatic fix.

A scanner failure or missing output fails the workflow. No dependency update or automatic remediation is performed by this gate.

## Secret hygiene

The workflow generates database, Redis and JWT values per job, masks them in GitHub Actions and never writes them to the evidence directory. Runtime inspection is deliberately sanitized. Logs and filesystem evidence are scanned for credentials, bearer tokens and workstation paths before a PASS result is allowed.

## Release rule

A remote green run with zero fixable CRITICAL vulnerabilities is required for PB1.2.2 DOCKER RUNTIME APPROVED. HIGH findings, if any, remain explicit P1 evidence and must be classified before public beta.
