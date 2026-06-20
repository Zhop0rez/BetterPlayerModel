import os
import re

src_dir = 'E:/BPM_Workspace_Fixes/BPM_26.2/src/main/java'
for root, _, files in os.walk(src_dir):
    for file in files:
        if file.endswith('.java'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            def fix_at(m):
                args = m.group(1)
                args = re.sub(r',\s*remap\s*=\s*false', '', args)
                args = re.sub(r'remap\s*=\s*false\s*,', '', args)
                args = re.sub(r'remap\s*=\s*false', '', args)
                return f'@At({args.strip()})'
            
            content = re.sub(r'@At\s*\(\s*(.*?)\s*\)', fix_at, content)
            
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
