# Public formation-change observation

- Public route: `https://manager-4f952.web.app/squad`
- Starting state observed: 4-4-2, 11/11, chemistry 74/99, effective team 92%.
- The editor exposes the twelve expected formation choices.
- Selecting 4-3-3 kept the same eleven players but reflowed them by slot order. One striker was placed in a CM slot and received a -51% role penalty; attack rose to 134% while midfield fell to 85% and defence to 90%.
- The dropdown invokes the manual-select endpoint immediately. No explicit confirmation was requested for this formation change.
- Reloading the squad restored 11/11 from the persisted backend state, which retained the off-position roles observed after the change.
- This is recorded as an application finding in the independent report; no production data was edited directly.
