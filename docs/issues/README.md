# Issues

Findings logged while implementing Jellyfin user track defaults, player buffering, and the mpv
player core. Each file states what was checked, what is wrong, and whether it is fixed or open.

| # | Issue | Status |
|---|---|---|
| [001](001-jellyfin-api-gaps.md) | Jellyfin API surface — 13 gaps, 3 closed | Open |
| [002](002-mpv-track-index-namespace.md) | mpv `aid`/`sid` were set to Jellyfin stream indices | **Fixed** |
| [003](003-mpv-core-gaps.md) | Remaining mpv player-core gaps | Partly fixed |
| [004](004-org-json-stubbed-in-unit-tests.md) | `org.json` silently returns defaults in JVM unit tests | **Fixed** |

Every endpoint, schema field, and enum value referenced in these notes was verified against
`jellyfin-openapi.json` at the repo root; mpv API against the `dev.jdtech.mpv:libmpv` 0.4.1 AAR
(`javap` over its `classes.jar`).
