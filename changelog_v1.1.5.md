## Changelog (v1.1.5)

### Added
* **Experimental Vulkan GPU renderer:** Added a RenderPearl/Sodium-compatible GPU skinning path for supported third-person models.

### Improved
* **Model catalog synchronization:** Local models and server-provided models are now reconciled using canonical model IDs. This keeps the model browser consistent in singleplayer, on servers with the Paper plugin, and on servers without a server-side BPM component.
* **Model selection stability:** Catalog refreshes no longer create repeated cards, unexpectedly reorder the list, remove the wrong entry, or make the page and model counters drift after selecting a model.
* **Reconnect cleanup:** Server model state, cached assemblies, pending model work, and local-only entries are cleared and rebuilt when changing servers or returning to a local world.
* **Model upload IDs:** Case, path separator, and archive-extension differences are normalized consistently. Ambiguous IDs remain separate instead of being merged into the wrong model.
* **YSM parsing compatibility:** Improved handling of modern and legacy binary models, large animation sections, and optional footer metadata. Invalid input is rejected safely without corrupting the active model catalog.

### Known limitation
* **Vulkan GPU renderer:** On Vulkan, the CPU renderer is recommended. The GPU path is experimental, may be slower than CPU rendering, and may render models that rely on unusual YSM effects differently or omit some effects.
