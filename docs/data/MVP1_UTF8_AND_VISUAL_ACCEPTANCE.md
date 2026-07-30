# MVP 1 UTF-8 and Visual Acceptance

Date: 2026-07-30

## Scope

This report closes the remaining MVP 1 UI integrity finding: player names, player trait labels, trait descriptions and squad text must render as valid UTF-8 in the frontend, without preserving corrupt text in either visible UI or test expectations.

## Corrected frontend files

| File | Evidence |
| --- | --- |
| `src/app/shared/components/player-card/player-card.component.html` | Player card visible text no longer contains corrupt energy, suspension or trait labels. |
| `src/app/shared/components/player-card/player-card.component.ts` | Player trait rendering keeps backend compatibility while exposing clean labels and descriptions. |
| `src/app/shared/components/player-card/player-card.component.spec.ts` | UTF-8 expectations now assert correct names and accents, and reject corrupt visible text. |
| `src/app/features/players/squad-management/squad-management.component.ts` | Squad labels and messages use valid Spanish UTF-8 text. |
| `src/app/features/players/squad-management/squad-management.component.html` | Squad empty-state and lineup text render valid accents. |
| `tools/check-visible-text-encoding.mjs` | Automated guard scans visible frontend text before the test suite runs. |

The constants that intentionally preserve backend compatibility remain isolated in the detailed match compatibility model and were not repurposed as user-facing labels.

## Browser acceptance

The in-app browser transport was unavailable during this closure, so the visual smoke was executed with local Chrome through the DevTools protocol against the running frontend and backend. The smoke used public application routes and public HTTP APIs.

| Country | League | Club | Route | Players | Sample | Traits | Mojibake | Console errors | Screenshot |
| --- | --- | --- | --- | ---: | --- | ---: | --- | ---: | --- |
| ESP | Spanish Primera Division | Real Madrid | `/squad` | 24 | Federico Valverde, CM | 2 | no | 0 | `D:/temp/mvp1-browser-smoke/esp-squad.png` |
| ARG | Argentine Primera Division | River Plate | `/squad` | 24 | Juan Carlos Portillo, CB | 2 | no | 0 | `D:/temp/mvp1-browser-smoke/arg-squad.png` |
| BRA | Brazilian Serie A | Flamengo | `/squad` | 24 | Ayrton Lucas, CDM | 2 | no | 0 | `D:/temp/mvp1-browser-smoke/bra-squad.png` |

The Spain run recorded two `favicon.ico` 404 responses. They are unrelated to the squad/player rendering path and did not produce application console errors or broken squad data. Argentina and Brazil had no bad application responses.

## Automated validation

| Validation | Result |
| --- | --- |
| Frontend visible text encoding guard | 381 files scanned, passed |
| Focused player-card test | 11 success, 0 failures |
| Frontend development build | passed |
| Frontend production build | passed |
| Full frontend suite | 1022 success, 0 failures, 2 skipped |

## Verdict

The remaining player UI mojibake and corrupt test expectations are resolved. UTF-8 rendering is now covered by both focused component tests and a pre-test encoding guard, and the three-league browser smoke confirms clean squad rendering on the real runtime.
