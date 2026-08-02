# Documentation

This directory contains maintained references for the current application. Completed plans, implementation checklists, screenshots, and issue mirrors remain available in Git history.

| Document | Purpose |
| --- | --- |
| [`design.md`](design.md) | Module boundaries, runtime flow, and architecture |
| [`ui-design.md`](ui-design.md) | Current television theme, layout, and focus behavior |
| [`navigation-faults.md`](navigation-faults.md) | Open screen-stack, D-pad navigation, and focus-transfer faults |
| [`player-controls.md`](player-controls.md) | Player controls, seeking, overlays, and playback options |
| [`i18n.md`](i18n.md) | String resources and locale support |
| [`jellyfin-api-coverage.md`](jellyfin-api-coverage.md) | Implemented Jellyfin workflows and known gaps |
| [`jellyfin-api-surface.md`](jellyfin-api-surface.md) | Endpoint and data-model reference derived from the checked-in OpenAPI document |
| [`releasing.md`](releasing.md) | Versioning, signing, and GitHub release workflow |

The checked-in source is authoritative when a document and implementation disagree. Update the relevant document in the same change when modifying a public contract or workflow.
