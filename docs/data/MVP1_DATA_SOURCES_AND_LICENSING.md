# MVP 1 Data Sources and Licensing

Consulta: 2026-07-29.

## Verdict

`APPROVED WITH ISSUES`.

The repository contains a redistributable explicit fictional dataset. It does not contain a legally validated real-player roster dataset.

## Product decision captured

The MVP 1 final target asks for explicit, reviewable player files rather than runtime-generated rosters. This repository now satisfies that structural requirement with fictional MANAGER-owned players in `src/main/resources/data/initial/players`.

The requirement for real player identities remains open because no redistribution-safe source for all players in Spain, Argentina and Brazil has been validated.

## Public identity data

| Area | Source | Owner | Consulted | Fields used | Redistribution status | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| Spain first division clubs | `https://www.laliga.com/en-GB/laliga-easports/clubs` | LaLiga | 2026-07-29 | club identity and 20-club scope | public reference only; not a player roster license | medium |
| Argentina first division clubs | `https://www.afa.com.ar/a/posts/se-realizo-el-sorteo-de-la-liga-profesional-2026-ya-se-conocen-los-grupos-del-torneo-apertura-y-torneo-clausura` | AFA | 2026-07-29 | competition reference and clubs | public reference only; not a player roster license | medium |
| Brazil Serie A clubs | `https://www.cbf.com.br/futebol-brasileiro/times/campeonato-brasileiro/serie-a` and `https://www.cbf.com.br/futebol-brasileiro/tabelas/campeonato-brasileiro/serie-a` | CBF | 2026-07-29 | club identity and league reference | public reference only; not a player roster license | medium |
| Open football data reference | `https://openfootball.github.io/` | football.db contributors | 2026-07-29 | open-data reference only | not imported automatically | low |

## MANAGER-created data

The following fields in `src/main/resources/data/initial/players` are fictional and MANAGER-created:

- player names;
- display names;
- birth dates;
- nationality;
- primary and secondary positions;
- preferred foot;
- height;
- shirt number;
- ratings;
- attributes;
- market value;
- salary proxy;
- special attributes;
- potential/reputation proxies.

## Data not copied

The project does not copy:

- Football Manager ratings;
- EA Sports ratings;
- eFootball ratings;
- Transfermarkt or closed-provider valuations;
- photos;
- badges;
- protected biographies or descriptions.

## Real roster requirement

To ship real player identities, one of these must happen first:

1. obtain a licensed roster data source that allows redistribution in the repository;
2. keep a private/local importer outside the public repo;
3. perform legal review and document allowed fields per source;
4. keep the redistributable fictional dataset as the public baseline.

Until then, real roster status remains `APPROVED WITH ISSUES`, not `COMPLETED`.
