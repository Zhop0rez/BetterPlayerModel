import os
import re

directory = r'E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java'

for root, dirs, files in os.walk(directory):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content
            
            # replace obj.gui.screen() with com.elfmcys.yesstevemodel.client.ScreenFixer.getScreen(obj)
            new_content = re.sub(r'([a-zA-Z0-9_\(\)\.]+)\.gui\.screen\(\)', r'com.elfmcys.yesstevemodel.client.ScreenFixer.getScreen(\1)', new_content)
            
            # replace obj.setScreen(screen) with com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(obj, screen)
            # handle 'minecraft.setScreen(foo)'
            new_content = re.sub(r'\bminecraft\.setScreen\((.*?)\)', r'com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(minecraft, \1)', new_content)
            new_content = re.sub(r'\bclient\.setScreen\((.*?)\)', r'com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(client, \1)', new_content)
            new_content = re.sub(r'\bmc\.setScreen\((.*?)\)', r'com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(mc, \1)', new_content)
            new_content = re.sub(r'Minecraft\.getInstance\(\)\.setScreen\((.*?)\)', r'com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(Minecraft.getInstance(), \1)', new_content)
                
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
