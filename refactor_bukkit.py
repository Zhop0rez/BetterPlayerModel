import os
import re

TARGET_DIR = r"F:\BetterPlayerModel_Plugin\src\main\java"

REPLACEMENTS = [
    # Imports
    (r"import net\.minecraft\.server\.level\.ServerPlayer;", "import org.bukkit.entity.Player;"),
    (r"import net\.minecraft\.world\.entity\.player\.Player;", ""),
    (r"import net\.minecraft\.server\.MinecraftServer;", "import org.bukkit.Server;"),
    (r"import dev\.architectury\.utils\.GameInstance;", "import org.bukkit.Bukkit;"),
    (r"import dev\.architectury\.platform\.Platform;", "import org.bukkit.plugin.java.JavaPlugin;\nimport com.example.bpmplugin.BetterPlayerModelPlugin;"),
    (r"import net\.minecraft\.network\.chat\.Component;", "import net.kyori.adventure.text.Component;"),
    (r"import net\.minecraft\.resources\.Identifier;", "import org.bukkit.NamespacedKey;"),
    
    # Class usages
    (r"\bServerPlayer\b", "Player"),
    (r"\bMinecraftServer\b", "Server"),
    (r"GameInstance\.getServer\(\)", "Bukkit.getServer()"),
    (r"Platform\.getConfigFolder\(\)", "BetterPlayerModelPlugin.getInstance().getDataFolder().toPath().getParent()"),
    (r"Platform\.getMod\((.*?)\)\.findResource\((.*?)\)", "java.util.Optional.empty()"), # Mock out findResource for now
    (r"YesSteveModel\.LOGGER\.info", "BetterPlayerModelPlugin.getInstance().getLogger().info"),
    (r"YesSteveModel\.LOGGER\.warn", "BetterPlayerModelPlugin.getInstance().getLogger().warning"),
    (r"YesSteveModel\.LOGGER\.error", "BetterPlayerModelPlugin.getInstance().getLogger().severe"),
    (r"YesSteveModel\.MOD_ID", '"better_player_model"'),
    (r"Component\.translatable\((.*?)\)", "Component.text(\\1)"),
    (r"Component\.literal\((.*?)\)", "Component.text(\\1)"),
]

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    new_content = content
    for pattern, replacement in REPLACEMENTS:
        new_content = re.sub(pattern, replacement, new_content)

    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Refactored: {filepath}")

for root, _, files in os.walk(TARGET_DIR):
    for file in files:
        if file.endswith(".java"):
            process_file(os.path.join(root, file))

print("Refactoring completed.")
