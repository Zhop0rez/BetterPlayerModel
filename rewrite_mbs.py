import os
import re

def rewrite_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    orig = content
    
    # Replace MultiBufferSource with SubmitNodeCollector in imports and signatures
    content = re.sub(r'import net\.minecraft\.client\.renderer\.MultiBufferSource;', r'import net.minecraft.client.renderer.SubmitNodeCollector;\nimport com.mojang.blaze3d.vertex.VertexConsumer;', content)
    
    content = re.sub(r'MultiBufferSource\.BufferSource', r'SubmitNodeCollector', content)
    content = re.sub(r'MultiBufferSource', r'VertexConsumer', content)
    content = re.sub(r'bufferSource\.getBuffer\((.*?)\)', r'\1', content) # this is a hack, we need proper logic, but wait, ufferSource.getBuffer is only used in IGeoRenderer which I can manually fix.
    
    if orig != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Rewrote {filepath}")

def main():
    src_dir = r"E:\BPM_Workspace_Fixes\BPM_26.2\src"
    for root, dirs, files in os.walk(src_dir):
        for file in files:
            if file.endswith(".java"):
                rewrite_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
