import os
import re

directory = r'E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java'
pattern = re.compile(r'(\w+)\.screen([^\(\w])')

files_fixed = []
for root, dirs, files in os.walk(directory):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content
            # find all matches
            matches = pattern.findall(content)
            changed = False
            for m in matches:
                if m[0] in ['minecraft', 'client', 'Minecraft.getInstance()', 'mc', 'Minecraft']:
                    new_content = re.sub(r'\b' + re.escape(m[0]) + r'\.screen([^\(\w])', m[0] + r'.gui.screen()\1', new_content)
                    changed = True
            
            # also handle Minecraft.getInstance().screen
            if 'Minecraft.getInstance().screen' in new_content:
                new_content = new_content.replace('Minecraft.getInstance().screen', 'Minecraft.getInstance().gui.screen()')
                changed = True
                
            if changed:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                files_fixed.append(filepath)

print("Fixed files:")
for f in files_fixed:
    print(f)
