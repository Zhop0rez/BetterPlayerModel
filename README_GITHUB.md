# Better Player Model (Fabric)

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.11-brightgreen.svg)
![Loader](https://img.shields.io/badge/loader-Fabric-orange.svg)

**Better Player Model** is a high-performance model loader and animation engine for Minecraft Fabric. It provides a universal bridge for custom 3D models, supporting both open formats and legacy encrypted files.

## 🚀 Key Features

*   **Universal Support:** Seamlessly load `.json` (Blockbench) and encrypted `.ysm` (Yes Steve Model) files.
*   **Bedrock Animation Engine:** Full **MoLang** implementation for fluid, math-driven animations.
*   **Decryption Pipeline:** Integrated **OpenYSM**-based parser for handling legacy protected models.
*   **Optimized Performance:** Built for Fabric 1.21.11 with extreme FPS stability in mind.
*   **Multiplayer Sync:** Optional server-side component for full model synchronization between players.

## 🛠 For Users

### Installation
1.  Download the latest release from [GitHub Releases](../../releases) or [Modrinth](https://modrinth.com).
2.  Place the `.jar` in your `mods` folder.
3.  Place your models in `config/yes_steve_model/custom`.

## 💻 For Developers

### Building from source
To build the project locally, ensure you have JDK 21 installed, then run:
```bash
git clone https://github.com/YOUR_USERNAME/BetterPlayerModel.git
cd BetterPlayerModel
./gradlew build
```
The compiled jar will be located in `build/libs`.

### Project Structure
- `src/main/java`: Core logic and MoLang engine.
- `src/main/resources`: Mixins and Fabric metadata.

## 📜 Credits
This project is an evolution of the Yes_Steve_Model ecosystem. We are grateful to the original creators:
- **[OpenYSM Team](https://github.com/OpenYSM)**: For the YSMParser and core engine technology.
- **[Micaftic](https://github.com/Micaftic)**: For the Fox-model-loader architecture.
- **[Elfmcys](https://github.com/Elfmcys)**: For the original Yes Steve Model concepts.

## 📄 License
This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
