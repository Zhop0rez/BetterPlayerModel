import os
import re

directory = r'E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java'

files_fixed = []
for root, dirs, files in os.walk(directory):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content
            
            # replace .setScreen( with .setScreenAndShow(
            # only if it is called on minecraft, mc, client, or Minecraft.getInstance()
            patterns = [
                r'\bminecraft\.setScreen\(',
                r'\bclient\.setScreen\(',
                r'\bmc\.setScreen\(',
                r'Minecraft\.getInstance\(\)\.setScreen\(',
                r'Minecraft\.getInstance\(\)\.gui\.setScreen\(' # if I accidentally changed it
            ]
            for p in patterns:
                new_content = re.sub(p, p.replace(r'\.setScreen', '.setScreenAndShow').replace(r'\.gui\.', '.').replace('\\', ''), new_content)
                
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                files_fixed.append(filepath)

print("Fixed files:")
for f in files_fixed:
    print(f)
