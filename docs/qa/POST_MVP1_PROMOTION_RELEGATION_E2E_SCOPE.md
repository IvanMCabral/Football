# Post-MVP 1 Promotion/Relegation E2E Scope

Classification: `IMPLEMENTED BUT NOT FULLY CERTIFIED`

Promotion/relegation behavior is present in the product surface and the certified career reached a state where promotions are available. This closure did not execute a fresh promotion/relegation-specific two-season simulation because the task was evidence/reproducibility-only and explicitly forbade new feature work or DB mutation.

Required certification before claiming full release coverage:

1. Create a fresh short-league career in a lower division.
2. Complete season 1 through the UI/API path used by players.
3. Capture final standings and promotion/relegation candidates.
4. Trigger the season transition.
5. Verify division membership changes for promoted/relegated teams.
6. Verify fixtures, standings and user-team context after the transition.
7. Repeat restart/recovery after the transition.
8. Save browser screenshots, API JSON and direct persistence evidence.

Exit criterion: promotion/relegation can be marked fully certified only when the above evidence is reproducible and independently reconciled.
