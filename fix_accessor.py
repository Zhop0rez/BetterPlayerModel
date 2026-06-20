import os
import re

src_dir = 'E:/BPM_Workspace_Fixes/BPM_26.2/src/main/java'
for root, _, files in os.walk(src_dir):
    for file in files:
        if file.endswith('.java'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Fix @Accessor(remap = false, "...")
            content = re.sub(r'@Accessor\(\s*remap\s*=\s*false\s*,\s*"([^"]+)"\s*\)', r'@Accessor(remap = false, value = "\1")', content)
            
            # Fix @Invoker(remap = false, "...")
            content = re.sub(r'@Invoker\(\s*remap\s*=\s*false\s*,\s*"([^"]+)"\s*\)', r'@Invoker(remap = false, value = "\1")', content)
            
            # Fix duplicate remap = false in @Shadow
            content = content.replace('remap = false, remap = false', 'remap = false')
            
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
