import os
import re

def rewrite_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    orig = content
    
    # Render methods
    content = re.sub(r'public void render\(GuiGraphicsExtractor ([a-zA-Z0-9_]+), int ([a-zA-Z0-9_]+), int ([a-zA-Z0-9_]+), float ([a-zA-Z0-9_]+)\)', r'public void extractRenderState(GuiGraphicsExtractor \1, int \2, int \3, float \4)', content)
    content = re.sub(r'protected void renderWidget\(GuiGraphicsExtractor ([a-zA-Z0-9_]+), int ([a-zA-Z0-9_]+), int ([a-zA-Z0-9_]+), float ([a-zA-Z0-9_]+)\)', r'protected void extractWidgetRenderState(GuiGraphicsExtractor \1, int \2, int \3, float \4)', content)
    content = re.sub(r'public void renderWidget\(GuiGraphicsExtractor ([a-zA-Z0-9_]+), int ([a-zA-Z0-9_]+), int ([a-zA-Z0-9_]+), float ([a-zA-Z0-9_]+)\)', r'public void extractWidgetRenderState(GuiGraphicsExtractor \1, int \2, int \3, float \4)', content)
    
    # super calls
    content = re.sub(r'super\.render\(', r'super.extractRenderState(', content)
    content = re.sub(r'super\.renderWidget\(', r'super.extractWidgetRenderState(', content)
    
    # GuiGraphics methods
    content = re.sub(r'\.drawString\(', r'.text(', content)
    content = re.sub(r'\.drawCenteredString\(', r'.centeredText(', content)
    content = re.sub(r'\.renderTooltip\(', r'.setTooltipForNextFrame(', content)
    
    # PoseStack methods
    content = re.sub(r'\.pose\(\)\.pushPose\(\)', r'.pose().pushMatrix()', content)
    content = re.sub(r'\.pose\(\)\.popPose\(\)', r'.pose().popMatrix()', content)
    
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
