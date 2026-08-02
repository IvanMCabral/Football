# PB1.2.2 Docker CI Runbook

## Trigger

The workflow is .github/workflows/pb12-docker-smoke.yml. It runs on workflow_dispatch and on changes to the Dockerfile, docker ignore file, Maven build, source, tools or the workflow itself.

    gh workflow run pb12-docker-smoke.yml --ref feat/v25d99.20.3.1-runtime-fixes
    gh run list --workflow pb12-docker-smoke.yml --limit 5
    gh run watch <run-id> --exit-status
    gh run view <run-id> --log
    gh run download <run-id>

A remote run must be observed before declaring PB1.2.2 approved. A local build or a workflow file inspection is not remote evidence.

## Evidence

The job uploads a seven-day artifact containing sanitized Docker build/history/inspect data, startup and shutdown logs, health responses, negative-readiness responses, cleanup report, SBOM, Trivy JSON and target/pb12-docker-smoke-result.json. Authentication responses, tokens, passwords, database files, Redis data and .env files are kept outside the artifact directory.

## Failure handling

The workflow fails if image build, health, auth/career, restart, Flyway, shutdown, readiness, cleanup, SBOM or scan gates fail. Artifact upload uses if: always() only for evidence collection. The cleanup code stops owned containers and removes only the unique run network; it never uses docker system prune.

## Local preflight

Docker is not installed on the current Windows workstation. The equivalent local JAR smoke remains available through tools/run-production-jar-smoke.ps1, but it cannot prove image metadata, container PID 1 or docker stop.
