# PB1.2.3G public smoke evidence

Date: 2026-08-04

## Public endpoints

- Frontend: `https://manager-4f952.web.app`
- Backend: `https://manager-staging-api.onrender.com`
- Liveness: HTTP 200, `{"status":"UP"}`
- Readiness: HTTP 200, database `UP`, Redis `UP`

## Tactical flow

An ephemeral authenticated smoke account created a Spanish Primera Division
career with Real Madrid and a 4-4-2 lineup. In the tactical editor:

1. 4-4-2 → 4-3-3 stayed in the local draft and showed 11/11 unique players.
2. Cancel returned to the confirmed 4-4-2 without a persisted change.
3. A second 4-3-3 draft was confirmed once and survived a page reload.
4. A live-match modal changed the in-game draft to 4-4-2, applied a one-pixel
   nudge, and returned to the match with the new tactical badge.

## Live and responsive flow

- First match opened at `/games/.../round/1/live`.
- Live minute advanced 11' → 17' → 22' while the round streamed updates.
- An injury opened the DT modal and paused the round; the modal exposed the
  editable pitch, bench and controlled substitution flow.
- Viewports checked: 390×844, 1366×768 and 1920×1080.
- At 390×844 the post-release document width matched the viewport (375 CSS px
  client width and 375 CSS px scroll width), with no horizontal overflow.

## Release fingerprints

- Firebase Hosting release URL: `https://manager-4f952.web.app`
- Public `index.html` ETag:
  `e336ae4de38a904bee294e4106c12bb4277153757dbbcf201b610d9e48c65e05`
- Public/local `index.html` SHA-256:
  `5A55D0C93734DAA04A682D8B7AC4AB90470567CE7E0B40F8EE682DC4F9646EC5`
- Production bundle: 52 files, 0 source maps, 0 test-harness references.
- Public/local `main-AHLJWP2U.js` SHA-256:
  `14E53BD88B71927BCA66A5FC080501425B9DE8EF5ED74B983DCAABFDA8466B0A`

No credentials, tokens, cookies or user identifiers are stored in this
evidence.
