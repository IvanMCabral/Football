# PB1.2.3A Render Free cold-start report

Status: `NOT MEASURED - NO RENDER SERVICE`

Render documents Free services as sleeping after 15 minutes without inbound traffic and waking in about one minute. This is a provider limit, not a measured result for this application. Source: [Render Free](https://render.com/docs/free).

Measure after provisioning: idle duration, first request latency, liveness, readiness, Flyway completion, browser behavior, retry/backoff and memory. The current Docker smoke used a 768 MB limit while Render Free supplies 512 MB; OOM and startup time are P0 validation gates.
