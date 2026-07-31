# Production Security Headers

Date: 2026-07-31

## Backend responsibility implemented for PB1.1

The backend emits minimum defensive headers on WebFlux responses:

- `X-Request-Id` generated or propagated.
- `X-Content-Type-Options: nosniff`.
- `X-Frame-Options: DENY`.
- `Cache-Control: no-store`.

These headers are intentionally conservative and do not depend on a specific CDN.

## PB1.2 proxy/CDN responsibility

The public edge must own final browser policy headers:

- HSTS after HTTPS/domain is final.
- CSP after all frontend asset origins are known.
- Referrer-Policy.
- frame-ancestors.
- static asset cache policy.

Do not enable edge HSTS before the production domain and HTTPS rollback path are confirmed.
