import os
import re

TARGET_DIR = r"F:\BetterPlayerModel_Plugin\src\main\java"

REPLACEMENTS = [
    (r"\.getUUID\(\)", ".getUniqueId()"),
    (r"\.getScoreboardName\(\)", ".getName()"),
    (r"currentServer\.execute\(", "org.bukkit.Bukkit.getScheduler().runTask(com.example.bpmplugin.BetterPlayerModelPlugin.getInstance(), "),
    (r"currentServer\.getPlayerList\(\)\.getPlayers\(\)", "org.bukkit.Bukkit.getOnlinePlayers()"),
    (r"import com\.elfmcys\.yesstevemodel\.client\..*?;", ""),
    (r"import com\.elfmcys\.yesstevemodel\.geckolib3\..*?;", ""),
    (r"import rip\.ysm\.compat\..*?;", ""),
    (r"import rip\.ysm\.api\..*?;", ""),
    (r"import rip\.ysm\.imagestream\..*?;", ""),
    (r"\bOuterFileTexture\b", "Object"),
    (r"\bExtraAnimationButtons\b", "Object"),
    (r"\bExportResult\b", "Object"),
    (r"\bModelInfoCapability\b", "Object"),
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

print("Method refactoring completed.")
