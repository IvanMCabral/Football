# PB1.2.3H7.9F.2.2 — Provider accounting

Date: 2026-08-15

## Plan evidence

The provider's authenticated plan screen was inspected read-only.

| Tier | Displayed monthly price | One-Off Job support |
|---|---:|---|
| Free | USD 0/month | No |
| Starter | USD 7/month | Yes |

Starter is therefore the minimum qualifying tier. The UI requires a payment
card before selecting it. It did not display a proration amount, a pending
charge or a reversible-to-Free guarantee, so none is asserted here.

## Job billing evidence

Render's current One-Off Job documentation states that a job uses the base
service's latest successful build artifact and configured environment snapshot,
and that a running job is billed at the per-second rate for its selected
instance type. The plan change and any job charge remain unperformed.

The documented Create Job API exposes `startCommand` and optional `planId`; it
does not document a per-job environment-variable override. Therefore a future
gate must not assume that the six canary-only inputs can be injected job-local.
It must inspect the enabled job form or an officially supported secure
mechanism before changing the normal service environment.

## Storage accounting

No fresh Upstash Management API T0/T1 sample was taken. The task was blocked
before the point at which a fresh sample would be immediately relevant to the
single authorized job. Historical storage values are not used as current
admission evidence.

## Cost decision

No price above Starter was considered, no add-on was selected, and no provider
or billing configuration changed.
