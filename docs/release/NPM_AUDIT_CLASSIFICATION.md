# NPM Audit Classification

Date: 2026-07-31
Command: `npm audit --json`
Action taken: classification only; no fixes applied.

## Summary

| Severity | Count |
| --- | ---: |
| Critical | 3 |
| High | 32 |
| Moderate | 19 |
| Low | 1 |
| Total | 55 |

Dependency metadata:

- Production dependencies: `16`.
- Development dependencies: `1002`.
- Optional dependencies: `136`.
- Peer dependencies: `1`.
- Total dependencies: `1017`.

## Production classification

Potential production-facing advisories are present through direct Angular runtime packages:

- `@angular/common`.
- `@angular/core`.
- `@angular/compiler`.
- `@angular/forms`.
- `@angular/platform-browser`.
- `@angular/router`.
- `@angular/animations`.

These include high-severity Angular advisories. They are not fixed in this freeze because dependency updates were explicitly out of scope.

## Development/tooling classification

Many high/critical advisories are in build/test/dev-server tooling:

- `@angular-devkit/*`.
- `@angular/build`.
- `@angular/cli`.
- `vite`.
- `webpack-dev-server`.
- `tar`.
- `websocket-driver`.
- `undici`.
- `@modelcontextprotocol/sdk`.
- Babel-related tooling packages.

These primarily affect local development/build tooling. They still require remediation planning before a hardened release process.

## Release gate interpretation

Security status for MVP 1 baseline freeze: `WARNING`.

Reason:

- The app is playable and suites are green.
- No dependency fixes were authorized.
- Production-facing Angular advisories exist and must be remediated or risk-accepted before a stronger release security claim.
