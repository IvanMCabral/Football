# PB1.2.3H7.9F.2.2 — Render One-Off Job enablement

Date: 2026-08-15

## Result

`PB1.2.3H7.9F.2.2 BLOCKED`

The Render plan review was completed without changing infrastructure. The
authenticated service UI identifies Starter as the cheapest qualifying tier for
One-Off Jobs, but it also requires adding a payment card before the plan change
can be selected. No card, plan, billing setting, region, scaling setting or
service command was changed.

## Provider evidence

| Item | Observed value |
|---|---|
| Service | `manager-staging-api` |
| Service ID | `srv-d9nvldtaeets73coqiog` |
| Current plan | Free |
| Minimum qualifying plan | Starter |
| Displayed monthly price | USD 7/month |
| One-Off Job capability | enabled by every paid instance type, including Starter |
| Free-plan limitation | no SSH, scaling, One-Off Jobs or persistent disks |
| Plan-change prerequisite | Render UI requires a payment card |
| Proration / exact charge | not displayed; not inferred |
| Reversion to Free | not confirmed; no mutation attempted |

The authenticated Render UI states that One-Off Jobs run standalone actions
using the base service's latest build image. Official Render documentation
confirms that a One-Off Job inherits the base service's latest successful build
artifact and configured environment-variable snapshot:

- <https://render.com/docs/one-off-jobs>
- <https://render.com/docs/ssh>

## Runtime state retained

The planned base service is the approved runtime:

- branch: `feat/v25d99.20.3.1-runtime-fixes`;
- approved code SHA: `24560a2b69fd94fce273ebb6aa7fe41b9e81c7ed`;
- live deployment short SHA observed in Render: `24560a2`;
- instance count: 1;
- autoscaling: Off;
- region: Oregon.

## Blocking condition

The minimum provider change is financially gated by a payment card. Adding a
payment method and confirming a paid plan are account and financial actions
that were not performed by this gate. The task must resume only after the
account holder has added a card and explicitly authorizes the Starter plan
change at the Render confirmation screen.

## Safety record

- services created: 0;
- services deleted: 0;
- plan changes: 0;
- billing changes: 0;
- environment changes: 0;
- Redis writes/deletes: 0;
- PostgreSQL writes: 0;
- canary jobs launched: 0;
- secret values recorded: 0.
