import os

src_dir = r"E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java\com\elfmcys\yesstevemodel\client\gui\button"
for file in os.listdir(src_dir):
    if file.endswith(".java"):
        filepath = os.path.join(src_dir, file)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        content = content.replace("protected void renderContents(", "protected void extractContents(")
        content = content.replace("public void renderContents(", "public void extractContents(")
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)

print("Renamed renderContents to extractContents")
