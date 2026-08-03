# PB1.2.3A zero-cost staging smoke report

Status: `NOT RUN - NO PUBLIC STAGING URL`

The smoke suite cannot be honestly reported until Render, Neon, Upstash and Firebase are provisioned in authenticated dashboards. No URL, response, token or provider log is fabricated.

Required checks after provisioning:

- Render liveness/readiness and startup/Flyway;
- register, login, `/me`, career creation and recovery;
- lineup, fixtures, standings and detailed match;
- Firebase routing and exact CORS;
- browser reload and logout/login recovery;
- controlled Render redeploy and Redis loss response.
