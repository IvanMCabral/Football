# MVP 1 Real Player Identity Review

Verdict: READY FOR MANUAL REVIEW

Cutoff date: 2026-07-29.

The dataset under `src/main/resources/data/initial/players` now contains 70 club files and 1680 explicit player records. Every club has 24 named players, stable external IDs, club assignment, MVP-playable tactical position, estimated MANAGER attributes, and exactly two explicit special traits.

Identity sources used:

- TheSportsDB public team/player pages.
- Wikipedia public club squad pages.
- One individual public player page supplement for Osasuna where the parsed club table exposed only 23 distinct records.

Manual review checklist:

- Verify names with accents render correctly.
- Verify club assignment against the cutoff date.
- Verify duplicated surnames are intentional and not duplicate identities.
- Verify tactical position normalization where `estimatedFields` contains `primaryPosition`.
- Verify no placeholder names such as `Player 1`, `P1`, or generated MANAGER identities remain.

Known limitation:

- Some public pages expose only broad or incomplete position data. For MVP playability, MANAGER normalizes each 24-player real-name roster into a balanced tactical squad. These normalized positions are explicitly marked as estimated.
