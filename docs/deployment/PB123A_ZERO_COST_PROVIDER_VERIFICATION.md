# PB1.2.3A zero-cost provider verification

Date checked: 2026-08-02

This is a pre-provisioning verification. No provider account was accessed, no payment method was added, and no resource was created. Dashboard-only facts (account eligibility, CAPTCHA, repository authorization and region availability) remain unverified until an authenticated human session is available.

## Official evidence

| Provider | Verified from official source | USD 0 posture | Card status | Result |
| --- | --- | --- | --- | --- |
| Firebase Hosting Spark | Spark needs no payment information; Hosting has no-cost quotas of 10 GB storage and 10 GB/month transfer | yes within quota | no card for Spark | PASS, subject to quota shutdown |
| Render Free Web Service | Free Docker web service, 512 MB/0.1 CPU, 750 instance-hours/workspace/month, sleeps after 15 minutes, HTTPS and logs | yes within quota | dashboard account requirement not stated by docs | UNVERIFIED without login; stop if card is requested |
| Neon Free | $0, no time limit, no credit card, 0.5 GB and 100 CU-hours/project/month, six-hour restore window | yes within quota | no card | PASS, subject to limits |
| Upstash Redis Free | $0, 256 MB, 500K commands/month and 10 GB bandwidth; TLS; entering a card upgrades to pay-as-you-go | yes within quota | no card for Free; never enter one | PASS for staging, not durable-beta approval |

Sources: [Firebase plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [Firebase Hosting quotas](https://firebase.google.com/docs/hosting/usage-quotas-pricing), [Render Free](https://render.com/docs/free), [Render compute plans](https://render.com/docs/compute-plans), [Neon pricing](https://neon.com/pricing), [Upstash pricing](https://upstash.com/pricing/redis).

## Absolute payment gate

If any dashboard asks for a card, billing account, deposit, paid trial, pay-as-you-go activation or auto-upgrade, stop that resource and record `PROVIDER BLOCKED BY PAYMENT REQUIREMENT`. Do not bypass the gate.

## Current gate

`PROVIDER ACCESS NOT AUTHENTICATED`: the repository contains no provider credentials or active sessions for Render, Neon, Upstash or Firebase. Provisioning and payment verification require the user to authenticate manually in each dashboard/CLI.
