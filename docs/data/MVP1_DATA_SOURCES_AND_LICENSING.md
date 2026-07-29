# MVP 1 Data Sources and Licensing

Verdict: APPROVED WITH PRODUCT-ACCEPTED DISTRIBUTION RISK

Cutoff date: 2026-07-29.

The product owner accepts using publicly visible real player names and club affiliation for the MVP 1 dataset. This document does not claim that redistribution is legally guaranteed.

## Public identity fields

The dataset may use public sources for:

- player name;
- display name;
- club at cutoff date;
- primary football role when available;
- league and country;
- shirt number when available.

Sources used:

- TheSportsDB public team/player pages.
- Wikipedia public club squad pages.
- Individual public player pages when club squad tables were incomplete.

## MANAGER-created fields

The following fields are created or estimated by MANAGER:

- tactical normalized position when source position is incomplete;
- secondary positions;
- date of birth when unavailable in parsed source;
- nationality fallback;
- height;
- preferred foot;
- all attributes;
- market value;
- salary if later introduced;
- exactly two special traits.

## Excluded data

The dataset does not copy:

- Football Manager ratings;
- EA Sports ratings;
- eFootball ratings;
- Transfermarkt valuations;
- salaries from commercial providers;
- photos;
- badges;
- protected biographies;
- provider-specific descriptive text.

## Distribution conclusion

The MVP can proceed with real public player identities because the product owner explicitly accepts the risk. Public release should still keep this risk visible and avoid presenting the dataset as legally guaranteed for unrestricted redistribution.
