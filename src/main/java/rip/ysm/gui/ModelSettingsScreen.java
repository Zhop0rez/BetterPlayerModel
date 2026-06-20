package rip.ysm.gui;

import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.client.gui.custom.AbstractConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.ExtraAnimationButtons;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.CheckboxConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RadioConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RangeConfig;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import rip.ysm.gui.components.BooleanOptionRow;
import rip.ysm.gui.components.RadioOptionRow;
import rip.ysm.gui.components.SliderOptionRow;
import rip.ysm.gui.components.groups.IdentifiedGroup;
import rip.ysm.gui.components.buttons.FooterButton;
import rip.ysm.gui.molang.MolangOption;

import java.util.ArrayList;
import java.util.List;

public class ModelSettingsScreen extends OptionScreen {

    private final ModelAssembly modelAssembly;

    private final AnimatableEntity<?> animatable;

    @Nullable
    private final String initialGroupId;

    private int previewLeft, previewTop, previewRight, previewBottom;

    private float yaw = 200.0f;

    private float pitch = 0.0f;

    private float zoom = 90.0f;

    private float offsetX = 0.0f;

    private float offsetY = 0.0f;

    private boolean draggingPreview;

    private int draggingButton = -1;

    public ModelSettingsScreen(ModelAssembly modelAssembly, AnimatableEntity<?> animatable, @Nullable Screen parent, @Nullable String initialGroupId) {
        super(Component.translatable("gui.better_player_model.model_settings.title"), parent);
        this.modelAssembly = modelAssembly;
        this.animatable = animatable;
        this.initialGroupId = initialGroupId;
    }

    @Override
    protected int computePanelWidth() {
        return Math.min(this.width - 40, 640);
    }

    @Override
    protected int computePanelHeight() {
        return Math.min(this.height - 40, 360);
    }

    @Override
    protected boolean shouldUseCompactTabs() {
        return this.width < 620;
    }

    @Override
    protected int computeRowAreaRight() {
        return panelRight - previewWidth() - 4;
    }

    private int previewWidth() {
        if (compactTabs) {
            int panelW = panelRight - panelLeft;
            return Mth.clamp(panelW / 3, 110, 180);
        }
        return 200;
    }

    @Override
    protected void init() {
        super.init();
        removeWidget(applyBtn);
        removeWidget(undoBtn);
        removeWidget(cancelBtn);
        applyBtn.visible = false;
        undoBtn.visible = false;
        cancelBtn.visible = false;
        applyBtn.active = false;
        undoBtn.active = false;
        saveBtn.setMessage(Component.translatable("gui.better_player_model.config.done"));
        saveBtn.setX(panelRight - saveBtn.getWidth());
        FooterButton resetBtn = new FooterButton(panelLeft, panelBottom - 56, 70, 20, Component.translatable("gui.better_player_model.config.reset"), this::onReset);
        addRenderableWidget(resetBtn);
        previewLeft = panelRight - previewWidth();
        previewTop = rowAreaTop;
        previewRight = panelRight;
        previewBottom = panelBottom - 60;
        if (initialGroupId != null) {
            for (OptionGroup g : groups) {
                if (g instanceof IdentifiedGroup ig && initialGroupId.equals(ig.id)) {
                    selectGroup(g);
                    break;
                }
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onReset() {
        for (OptionGroup g : groups) {
            for (OptionRow<?> row : g.getRows()) {
                Option<?> opt = row.getOption();
                if (opt != null) {
                    if (opt.get() instanceof Boolean) {
                        ((Option<Boolean>) opt).setPending(false);
                    } else if (opt.get() instanceof Double) {
                        ((Option<Double>) opt).setPending(0.0);
                    } else if (opt.get() instanceof Integer) {
                        ((Option<Integer>) opt).setPending(0);
                    }
                    row.refresh();
                }
            }
        }
        if (activeGroup != null) {
            selectGroup(activeGroup);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(minecraft, parentScreen);
    }

    @Override
    protected void collectBlurRegions(List<int[]> out) {
        super.collectBlurRegions(out);
        out.add(new int[]{previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop});
    }

    @Override
    protected void registerGroups() {
        List<ExtraAnimationButtons> ordered = new ArrayList<>(modelAssembly.getModelData().getModelProperties().getExtraAnimationButtons().values());
        ordered.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (ExtraAnimationButtons cfgGroup : ordered) {
            IdentifiedGroup g = new IdentifiedGroup(cfgGroup.getId(), groupLabel(cfgGroup));
            int formIndex = 0;
            for (AbstractConfig form : cfgGroup.getConfigForms()) {
                OptionRow<?> row = buildRow(cfgGroup.getId(), formIndex, form);
                if (row != null) g.add(row);
                formIndex++;
            }
            groups.add(g);
        }
    }

    private String groupLabel(ExtraAnimationButtons group) {
        String fallback = group.getName() == null || group.getName().isEmpty() ? group.getId() : group.getName();
        return ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.name".formatted(group.getId()), fallback);
    }

    @Nullable
    private OptionRow<?> buildRow(String groupId, int formIndex, AbstractConfig form) {
        String title = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.title".formatted(groupId, formIndex), form.getTitle());
        String desc = ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.description".formatted(groupId, formIndex), form.getDescription());
        if (form instanceof CheckboxConfig cfg) {
            return new BooleanOptionRow(0, 0, 0, 22, MolangOption.ofBoolean(title, desc, animatable, cfg.getValue()));
        }
        if (form instanceof RangeConfig cfg) {
            return new SliderOptionRow(0, 0, 0, 22, MolangOption.ofDouble(title, desc, animatable, cfg.getValue()), cfg.getMin(), cfg.getMax(), cfg.getStep(), "");
        }
        if (form instanceof RadioConfig cfg) {
            OrderedStringMap<String, String> labels = cfg.getLabels();
            List<String> texts = new ArrayList<>(labels.size());
            String[] writeExprs = new String[labels.size()];
            for (int i = 0; i < labels.size(); i++) {
                texts.add(ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "properties.extra_animation_buttons.%s.config_forms.%d.labels.%d".formatted(groupId, formIndex, i), labels.getKeyAt(i)));
                writeExprs[i] = labels.getValueAt(i);
            }
            return new RadioOptionRow(0, 0, 0, 22, MolangOption.ofIndex(title, desc, animatable, cfg.getValue(), writeExprs), texts);
        }
        return null;
    }

    @Override
    protected void renderExtras(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(previewLeft, previewTop, previewRight, previewBottom, 0x66000000);
        renderPreview(g, partialTick);
    }

    private void renderPreview(GuiGraphicsExtractor g, float partialTick) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!(animatable instanceof LivingAnimatable<?> la)) return;
        g.enableScissor(previewLeft, previewTop, previewRight, previewBottom);
        float cx = (previewLeft + previewRight) / 2.0f + offsetX;
        float cy = previewTop + (previewBottom - previewTop) * 0.65f + offsetY;
        ModelPreviewRenderer.renderEntityPreview(g, previewLeft, previewTop, previewRight, previewBottom, cx, cy, zoom, pitch, yaw, partialTick, (com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity) la, RendererManager.getPlayerRenderer(), false);
        g.disableScissor();
    }
}

