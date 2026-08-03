# PB1.2.3A Render Free runbook

The repository now contains `render.yaml`, an official Blueprint-compatible definition for one Free Docker web service only. It does not create a database or Redis instance and contains no secret values.

## Render configuration

- Runtime: Docker, root `Dockerfile` and repository context.
- Plan: `free`.
- Branch: `feat/v25d99.20.3.1-runtime-fixes`.
- Auto deploy: off until validation is complete.
- Health: `/api/v1/health/liveness`.
- Shutdown delay: 30 seconds.
- Public URL: provider-generated `https://<service>.onrender.com`.
- API routing: Angular calls this origin directly; Firebase does not proxy API/SSE.

Render Free is 512 MB RAM and 0.1 CPU, sleeps after 15 minutes idle and wakes in about one minute. It has 750 instance-hours per workspace/month, ephemeral filesystem, one instance only and no persistent disk. Render documents Free as testing/hobby infrastructure, not production. Source: [Free services](https://render.com/docs/free), [instance types](https://render.com/docs/compute-plans).
## Variables

`render.yaml` declares non-secret values and `sync: false` placeholders for DB, Redis, JWT and CORS values. Render prompts for `sync: false` values only during initial Blueprint creation; later additions must be entered in the Dashboard. Source: [Blueprint specification](https://render.com/docs/blueprint-spec).

The application contract uses `APP_RATE_LIMIT_ENABLED`, not the ambiguous `AUTH_RATE_LIMIT_ENABLED` name. Render supplies `PORT`; the container binds `SERVER_ADDRESS=0.0.0.0`.

## Acceptance checks

1. Confirm the Dashboard still shows Free and requests no card.
2. Validate the Blueprint before creation with Render CLI or API.
3. Build the image and wait for health.
4. Verify liveness/readiness, Flyway, auth and career smoke.
5. Measure memory and startup; the current Docker smoke used 768 MB, so 512 MB is an explicit OOM risk.
6. Run the five-minute SSE/reconnect drill before treating the service as usable.
