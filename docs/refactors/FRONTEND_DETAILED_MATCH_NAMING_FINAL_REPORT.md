# MANAGER Frontend — Detailed Match Naming Final Report

Date: 2026-07-28

## Verdict

COMPLETED.

The frontend no longer uses residual `V23/V24` naming for current detailed-match components, selectors, models, services, harness imports, or visible product text. The only remaining version literals are isolated in a compatibility model for backend contract values that must still be read unchanged.

## 1. Baseline

Backend baseline:

- Commit: `e513c842 Complete full stack regression validation`
- Scope: not modified in this phase.

Frontend baseline:

- Commit: `975a528 Fix frontend regression text encoding`
- Git status before changes: clean.
- Development build before changes: passed.
- Frontend tests before changes: `1016 SUCCESS`, `0` failures, `2` skipped.

## 2. Initial references

Initial `src/app` inventory found residual references in:

- route lazy-loading for the detailed match page;
- `v24-match-detail-page` filenames, selector, class, template and styles;
- `V24LivePlayerRating`;
- substitution modal rating types;
- test harness embedded detail page imports/template;
- visible detail-page text such as `V24 Engine` and `detalle V24`;
- player season stats empty-state text;
- comments in match detail/compare models and services;
- historical test fixtures named `InJURED_V23_LEGACY`;
- backend compatibility values: `engineVersion: "V24"` and `dataSource: "V24_DETAIL"`.

## 3. Components renamed

Renamed current detailed match page artifacts:

- `v24-match-detail-page.component.ts` → `detailed-match-page.component.ts`
- `v24-match-detail-page.component.html` → `detailed-match-page.component.html`
- `v24-match-detail-page.component.scss` → `detailed-match-page.component.scss`
- `v24-match-detail-page.component.spec.ts` → `detailed-match-page.component.spec.ts`

Updated names:

- `V24MatchDetailPageComponent` → `DetailedMatchPageComponent`
- selector `app-v24-match-detail-page` → `app-detailed-match-page`
- wrapper CSS class `v24-match-detail-page` → `detailed-match-page`
- engine badge CSS `badge-v24` → `badge-detailed-engine`

Updated consumers:

- Angular route lazy loading;
- test harness embedded detail panel;
- component specs;
- generated harness flow export/shared files that import the embedded detail component.

No alias with the old component name was kept.

## 4. Models renamed

Renamed current frontend type:

- `V24LivePlayerRating` → `LivePlayerRating`

Updated consumers:

- `MatchState.homePlayerRatings`;
- `MatchState.awayPlayerRatings`;
- substitution modal model;
- substitution modal component;
- substitution rating utility and tests.

No JSON field names were changed.

## 5. Visible texts corrected

Visible/product-facing text was changed from technical version naming to product language:

- `V24 Engine` → `Motor de partido detallado`
- unavailable detail reason now says the match was played before detailed match storage, without exposing implementation version names;
- player season stats empty state now refers to detailed match data/recording;
- shot-map empty state now refers to detailed tracking;
- compare fallback text now refers to the current detailed-match route, not a versioned route.

Final visible text smoke verified that the detailed match route did not show `V23`, `V24`, `v23`, or `v24`.

## 6. Compatibility preserved

Backend contract values remain readable and unchanged:

- `engineVersion: "V24"`
- `dataSource: "V24_DETAIL"`

They are isolated in:

- `src/app/features/match-detail/models/detailed-match-compatibility.model.ts`

Constants:

- `DETAILED_MATCH_ENGINE_VERSION`
- `PLAYER_STATS_DETAILED_DATA_SOURCE`

Specs use those constants instead of scattering version literals through current code.

## 7. References remaining

Final search:

```text
rg -n "V23|V24|v23|v24" src/app
```

Remaining references:

- `DETAILED_MATCH_ENGINE_VERSION = 'V24'`
- `PLAYER_STATS_DETAILED_DATA_SOURCE = 'V24_DETAIL'`

Classification:

- backend compatibility only;
- not visible in UI;
- not used as current component/model/service naming.

## 8. Tests

Frontend suite:

```text
npm test -- --watch=false --browsers=ChromeHeadless
```

Result:

- `1016 SUCCESS`
- `0` failures
- `2` skipped

Known test-log note:

- Some tests intentionally log simulated degraded SSE/database error states while still passing.

## 9. Builds

Development build:

```text
npm run build -- --configuration development
```

Result: passed.

Production build:

```text
npm run build
```

Result: passed.

Production output:

- initial raw bundle: `420.67 kB`;
- estimated transfer: `118.10 kB`;
- output folder: `dist/demo`.

## 10. Runtime smoke

Backend and frontend were started using the current runbook flow:

- backend profile: `local,career-mutations`;
- PostgreSQL/Flyway startup completed;
- backend bound on port `8080`;
- frontend served on port `4200`;
- proxy `/api/v1` returned backend validation response through frontend.

Runtime UI smoke created a real user/career, prepared lineup, advanced a round, waited for a persisted detailed match, and opened the renamed route in local Chrome:

```text
NAMING_UI_SMOKE_OK careerId=6ac99308-dbbd-4a67-906a-dfe0d9beae6e matchId=5b5c35b8-6655-4dee-a7d0-428a7f043b09
```

Verified:

- route `/careers/:careerId/matches/:matchId/detail` loaded;
- renamed detailed page chunk loaded without 404;
- detail page rendered score, xG, possession, events/linea de tiempo and detailed-engine badge;
- browser-visible page text did not include `V23/V24/v23/v24`;
- no relevant network loading failures were reported by the smoke.

## 11. Risks

No backend or HTTP contract was changed.

The remaining risk is purely historical-contract naming in backend payload values. It is intentionally isolated in constants to avoid leaking version terms into current frontend concepts.

## 12. Git validation

Final checks required:

- `git diff --check`;
- `git status --short`;
- commit;
- test execution from the commit;
- clean working tree.

Final verdict: COMPLETED.

