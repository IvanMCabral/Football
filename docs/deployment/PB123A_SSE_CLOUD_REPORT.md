# PB1.2.3A Render Free SSE report

Status: `NOT RUN - NO RENDER SERVICE`

The selected routing is direct browser-to-Render HTTPS. Firebase rewrites are intentionally not used for SSE. Render documents WebSocket support and TLS termination, but this application still requires an actual long-lived HTTP/SSE test; provider documentation alone is not evidence of stable match streaming.

Required evidence: authenticated connection, events, heartbeat, more than five minutes, pause/resume, tactical change, substitution, reconnect after cold start/redeploy, two users and two simultaneous sessions. Record cuts, duplicate events, lost events and buffering without storing tokens.

If SSE is unstable on Free, keep the resource USD 0, mark `RENDER FREE SSE REJECTED`, and do not open public beta.
