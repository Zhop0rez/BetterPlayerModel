## Changelog (v1.1.5)

### Improved
* **Model catalog synchronization:** Local and server-provided models are reconciled using canonical IDs, preventing duplicate cards and missing entries.
* **Model selection stability:** Catalog refreshes no longer reorder the list unexpectedly or make page and model counters drift after selection.
* **Reconnect cleanup:** Server model state, cached assemblies, pending work, and local-only entries are cleared and rebuilt when changing servers or returning to a local world.
* **Model upload IDs:** Case, path separator, and archive-extension differences are normalized consistently. Ambiguous IDs remain separate instead of being merged into the wrong model.
* **YSM parsing compatibility:** Modern and legacy binary models, optional footer metadata, and nested legacy archive resources are handled more safely.

### Compatibility
* This 1.20.1 build does not include the experimental Vulkan GPU renderer. The CPU renderer remains the supported and compatibility-focused path.
