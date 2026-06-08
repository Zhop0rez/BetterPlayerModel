# Better Player Model

A high-performance custom model loader and animation engine for Minecraft Fabric. This project provides a universal bridge for 3D player models, supporting both open standard formats and legacy encrypted files.

## Overview

Better Player Model is designed to bring Bedrock Edition's animation capabilities to Java Edition. By integrating the MoLang engine and a specialized model parsing pipeline, it allows for fluid, dynamic animations that react to in-game state changes.

## Key Features

*   **Universal Format Support:** Full compatibility with Blockbench (.json) and encrypted Yes_Steve_Model (.ysm) files.
*   **YSM Parser Integration:** Built-in engine based on OpenYSM technology to decrypt and unpack legacy .ysm models.
*   **Bedrock Animation Engine:** Comprehensive MoLang implementation supporting standard queries (q.), variables (v.), and complex mathematical expressions.
*   **Optimized Performance:** Specifically tuned for Fabric 1.21.11 to ensure minimal impact on frame rates during bone transformations and rendering.
*   **Multiplayer Synchronization:** Optional server-side component to synchronize custom models and animations between clients.

## Installation

1.  Download the latest release from the [Releases](../../releases) section.
2.  Place the `.jar` file in your Minecraft `mods` folder.
3.  Place your custom models in the `config/better_player_model/custom` directory.

## Development

### Building from Source
To compile the project locally, ensure you have JDK 21 installed:

```bash
git clone https://github.com/Zhop0rez/BetterPlayerModel.git
cd BetterPlayerModel
./gradlew build
```

The compiled binary will be available in `build/libs`.

## Credits and Acknowledgments

This project is a specialized adaptation and evolution of the Yes_Steve_Model ecosystem. Recognition is given to the original developers and the open-source community:

*   **OpenYSM Team:** For the YSMParser and core engine technology.
*   **Micaftic:** For the Fox-model-loader architecture.
*   **Elfmcys:** For the original Yes_Steve_Model concepts.

### Where to find models?
Looking for high-quality player models? You can browse and download hundreds of community-made models from the Yes-steve-model community repository:
**[Download Models Here](https://github.com/Elaina69/Yes-Steve-Model-Repo/tree/main)**

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for the full text.
