# Changelog

## 1.0.3

- Stabilized the server model catalog by normalizing model IDs and ignoring duplicate entries during directory scans.
- Reloads now replace the catalog atomically and resynchronize online players after the scan completes.
- Improved model upload validation, duplicate-ID rejection, and cleanup of incomplete or timed-out upload sessions.
- Added bounded model-state resynchronization after catalog delivery, model transfers, reconnects, and model selection changes.
- Validated requested texture IDs and fall back to a valid model texture when a client requests an unavailable texture.
- Improved compatibility with YSM archives and raw format-32 model uploads, including safer parser error handling.
