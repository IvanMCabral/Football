# MVP 1 Three-League Dataset Validation Report

## Verdict

`APPROVED WITH ISSUES`.

The explicit dataset is structurally valid and runtime-ready. It is fictional, not a validated real roster.

## Coverage

| Metric | Expected | Status |
| --- | ---: | --- |
| Countries | 3 | OK |
| Leagues | 3 | OK |
| Clubs | 70 | OK |
| Players per club | 24 | OK |
| Players total | 1680 | OK |
| Special traits per player | 2 | OK |
| Trait rows | 3360 | OK |

## Source validation

The source files under `src/main/resources/data/initial/players` include:

- `externalId`;
- `fullName`;
- `displayName`;
- `dateOfBirth`;
- `nationalityCode`;
- `clubExternalId`;
- `primaryPosition`;
- `secondaryPositions`;
- `preferredFoot`;
- `heightCm`;
- `shirtNumber`;
- all numeric gameplay attributes;
- exactly two explicit `specialAttributes`;
- provenance;
- estimated field markers.

## Automated evidence

Focused command:

```bash
mvn -q -Dtest='ThreeLeagueDatasetImporterTest,ThreeLeagueDatasetRuntimeAcceptanceE2ETest' test
```

Result after explicit dataset conversion: green, 5 tests, 0 failures, 0 errors, 0 skipped.

## Known issue

The explicit dataset is not a real roster. Real-player source licensing remains unresolved.
