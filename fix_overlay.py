import os

filepath = r'E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java\com\elfmcys\yesstevemodel\client\renderer\ExtraPlayerOverlay.java'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

new_content = content.replace(
    'com.elfmcys.yesstevemodel.client.ScreenFixer.getScreen((minecraft) instanceof ExtraPlayerRenderScreen)',
    '(com.elfmcys.yesstevemodel.client.ScreenFixer.getScreen(minecraft) instanceof ExtraPlayerRenderScreen)'
)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(new_content)
