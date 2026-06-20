import os

directory = r'E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java'

for root, dirs, files in os.walk(directory):
    for file in files:
        if file.endswith('.java'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content
            
            new_content = new_content.replace('this.bminecraft', 'this.minecraft')
            new_content = new_content.replace('\x08minecraft', 'minecraft')
                
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
