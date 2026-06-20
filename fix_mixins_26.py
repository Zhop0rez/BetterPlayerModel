import os
import re

def rewrite_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    orig = content
    
    # 1. Add remap = false
    # Find @Inject, @WrapWithCondition, @ModifyVariable
    content = re.sub(r'@Inject\((.*?)(?<!remap = false)(?<!remap=false)\)', r'@Inject(\1, remap = false)', content)
    content = re.sub(r'@Inject\(\s*remap = false\s*,', r'@Inject(', content) # Clean up if it was empty
    content = re.sub(r'@WrapWithCondition\((.*?)(?<!remap = false)(?<!remap=false)\)', r'@WrapWithCondition(\1, remap = false)', content)
    
    # Clean up double commas if they happened
    content = re.sub(r',\s*,', r',', content)
    content = re.sub(r'\(\s*,', r'(', content)
    
    # Fix imports
    content = content.replace('net.minecraft.client.renderer.state.CameraRenderState', 'net.minecraft.client.renderer.state.level.CameraRenderState')
    content = content.replace('net.minecraft.client.renderer.block.BlockRenderDispatcher', 'net.minecraft.client.renderer.block.BlockModelResolver')
    content = content.replace('BlockRenderDispatcher blockRenderDispatcher', 'BlockModelResolver blockModelResolver')
    content = content.replace('BlockRenderDispatcher ysm', 'BlockModelResolver ysm')
    content = content.replace('"blockRenderDispatcher"', '"blockModelResolver"')
    content = content.replace('net.minecraft.client.resources.model.AtlasManager', 'net.minecraft.client.resources.model.sprite.AtlasManager')
    
    # Fix TextureFormat
    content = content.replace('import com.mojang.blaze3d.textures.TextureFormat;', '')
    
    # Fix BufferSourceMixin
    if "BufferSourceMixin" in filepath:
        content = content.replace('net.minecraft.client.renderer.SubmitNodeCollector', 'net.minecraft.client.renderer.RenderBuffers')
    
    if orig != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Rewrote {filepath}")

def main():
    src_dir = r"E:\BPM_Workspace_Fixes\BPM_26.2\src"
    for root, dirs, files in os.walk(src_dir):
        for file in files:
            if file.endswith("Mixin.java") or file.endswith("Accessor.java") or file.endswith("OuterFileTexture.java"):
                rewrite_file(os.path.join(root, file))

if __name__ == "__main__":
    main()
