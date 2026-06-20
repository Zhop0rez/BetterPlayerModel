package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import com.elfmcys.yesstevemodel.capability.ModelInfoCapability;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.client.animation.AnimationTracker;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.client.upload.ModelUploadSession;
import com.elfmcys.yesstevemodel.client.upload.UploadManager;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.client.upload.IResourceLocatable;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SRequestSwitchModelPacket;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.elfmcys.yesstevemodel.util.PlatformUtil;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.ysm.architectury.utils.GameInstance;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class ModelButton extends Button {

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    public final boolean isStarred;

    private final int backgroundColor;

    public final ModelAssembly renderContext;

    public final PlayerPreviewEntity modelIdHolder;

    private final String previewModelId;

    private final String previewTextureName;

    private final String modelId;

    private final String modelName;

    private final String authorName;

    private final double animationDuration;

    private final boolean disablePreviewRotation;

    private final Component displayName;

    @Nullable
    private IResourceLocatable backgroundTexture;

    @Nullable
    private IResourceLocatable foregroundTexture;

    @Nullable
    private String cachedLanguage;

    @Nullable
    private List<Component> tooltipLines;

    @Nullable
    private List<Component> detailedTooltipLines;

    private long lastHoverTime;

    public ModelButton(int x, int y, boolean isAuthLocked, PlayerPreviewEntity playerPreviewEntity, ModelAssembly textureRegistry) {
        super(x, y, 52, 90, createDisplayName(playerPreviewEntity, textureRegistry), button -> {
        }, DEFAULT_NARRATION);
        this.backgroundTexture = null;
        this.foregroundTexture = null;
        this.tooltipLines = null;
        this.detailedTooltipLines = null;
        this.lastHoverTime = -1L;
        this.isStarred = isAuthLocked;
        this.backgroundColor = isAuthLocked ? 2130706432 : -12369342;
        this.renderContext = textureRegistry;
        this.modelIdHolder = playerPreviewEntity;
        this.previewModelId = playerPreviewEntity.getModelId();
        this.previewTextureName = textureRegistry.getAnimationBundle().getDefaultTextureName();
        this.disablePreviewRotation = textureRegistry.getModelData().getModelProperties().isDisablePreviewRotation();
        this.displayName = Component.literal(FileTypeUtil.getNameWithoutArchiveExtension(playerPreviewEntity.getModelId()));
        this.backgroundTexture = textureRegistry.getTextureRegistry().getGuiBackground() == null ? null : UploadManager.getOrCreateLocatableWithSize(textureRegistry.getTextureRegistry().getGuiBackground(), true, 200);
        this.foregroundTexture = textureRegistry.getTextureRegistry().getGuiForeground() == null ? null : UploadManager.getOrCreateLocatableWithSize(textureRegistry.getTextureRegistry().getGuiForeground(), true, 200);
        Object2ReferenceMap<String, Animation> object2ReferenceMapM3399xe6e508ff = textureRegistry.getAnimationBundle().getMainAnimations();
        if (object2ReferenceMapM3399xe6e508ff.containsKey("hover")) {
            this.modelId = "hover";
        } else {
            this.modelId = "empty";
        }
        if (object2ReferenceMapM3399xe6e508ff.containsKey("hover_fadeout")) {
            this.modelName = "hover_fadeout";
            this.animationDuration = object2ReferenceMapM3399xe6e508ff.get("hover_fadeout").animationLength * 50.0f;
        } else {
            this.modelName = "empty";
            this.animationDuration = 0.0d;
        }
        if (object2ReferenceMapM3399xe6e508ff.containsKey("focus")) {
            this.authorName = "focus";
        } else {
            this.authorName = "empty";
        }
    }

    private static MutableComponent createDisplayName(PlayerPreviewEntity previewEntity, ModelAssembly modelAssembly) {
        Metadata metadata2 = modelAssembly.getModelData().getExtraInfo();
        if (metadata2 == null || StringUtils.isBlank(metadata2.getName())) {
            return Component.literal(FileTypeUtil.getNameWithoutArchiveExtension(previewEntity.getModelId()));
        }
        return Component.literal(ModelMetadataPresenter.getLocalizedModelString(modelAssembly, "metadata.name", metadata2.getName()));
    }

    public Component getMessage() {
        if (GeneralConfig.SHOW_MODEL_ID_FIRST.get().booleanValue()) {
            return this.displayName;
        }
        return super.getMessage();
    }

    @Override
    public void onPress(InputWithModifiers modifiers) {
        LocalPlayer localPlayer;
        if (!this.isStarred && (localPlayer = Minecraft.getInstance().player) != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                String selectedModelId = this.modelIdHolder.getModelId();
                String selectedTextureName = resolveSelectionTextureName();
                cap.initModelWithTexture(selectedModelId, selectedTextureName);
                if (!NetworkHandler.isClientConnected()) {
                    return;
                }
                if (!ClientModelManager.isLocalOnlyModel(selectedModelId)) {
                    sendSwitchModel(selectedModelId, selectedTextureName);
                    return;
                }
                if (!ClientModelManager.isOysmServer()) {
                    return;
                }
                Component uploadError = ClientModelManager.uploadLocalModelForSelection(selectedModelId);
                if (uploadError != null) {
                    localPlayer.sendSystemMessage(uploadError);
                    return;
                }
                if (!ClientModelManager.isLocalOnlyModel(selectedModelId)) {
                    sendSwitchModel(selectedModelId, selectedTextureName);
                    return;
                }
                sendSwitchAfterUpload(selectedModelId, selectedTextureName, localPlayer);
            });
        }
    }

    private static void sendSwitchModel(String modelId, String textureName) {
        NetworkHandler.sendToServer(new C2SRequestSwitchModelPacket(modelId, textureName));
        applyIntegratedServerSwitch(modelId, textureName);
    }

    private static void applyIntegratedServerSwitch(String modelId, String textureName) {
        MinecraftServer server = GameInstance.getServer();
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (server == null || localPlayer == null) {
            return;
        }
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(localPlayer.getUUID());
        if (serverPlayer == null) {
            return;
        }
        ModelInfoCapability.get(serverPlayer).ifPresent(cap -> {
            AuthModelsCapability.get(serverPlayer).ifPresent(auth -> {
                boolean changed = false;
                if (!ServerModelManager.getServerModelInfo().containsKey(modelId)) {
                    cap.resetToDefault();
                    changed = true;
                } else if (ServerModelManager.getAuthModels().contains(modelId) && !auth.containsModel(modelId)) {
                    cap.resetToDefault();
                    changed = true;
                } else {
                    String resolvedTexture = ServerModelManager.resolveTextureOrDefault(modelId, textureName);
                    if (resolvedTexture == null) {
                        cap.resetToDefault();
                        changed = true;
                    } else {
                        cap.setModelAndTexture(modelId, resolvedTexture);
                        changed = true;
                    }
                }
                cap.stopAnimation(serverPlayer);
                if (changed) {
                    C2SRequestSwitchModelPacket.sendImmediateModelState(serverPlayer, cap);
                }
            });
        });
    }

    private static void sendSwitchAfterUpload(String modelId, String textureName, LocalPlayer player) {
        ModelUploadSession.Listener listener = new ModelUploadSession.Listener() {
            @Override
            public void onSessionUpdate(ModelUploadSession session) {
                if (session == null || !Objects.equals(modelId, session.getModelId())) {
                    return;
                }
                if (session.getState() == ModelUploadSession.State.COMPLETED) {
                    ModelUploadSession.removeListener(this);
                    ClientModelManager.markLocalModelUploaded(modelId);
                    sendSwitchModel(modelId, textureName);
                    return;
                }
                if (session.getState() == ModelUploadSession.State.FAILED) {
                    ModelUploadSession.removeListener(this);
                    if (session.getLastStatus() == 1 && session.getUploadId() == 0L) {
                        ClientModelManager.markLocalModelUploaded(modelId);
                        sendSwitchModel(modelId, textureName);
                    } else if (player != null) {
                        player.sendSystemMessage(session.getMessage());
                    }
                }
            }
        };
        ModelUploadSession.addListener(listener);
        listener.onSessionUpdate(ModelUploadSession.getInstance());
    }

    private String resolveSelectionTextureName() {
        String textureName = this.previewTextureName;
        if (this.modelIdHolder.isModelReady()) {
            try {
                textureName = this.modelIdHolder.getCurrentTextureName();
            } catch (RuntimeException ignored) {
                textureName = this.previewTextureName;
            }
        }
        if (StringUtils.isBlank(textureName) || !this.renderContext.getAnimationBundle().getTextures().containsKey(textureName)) {
            textureName = this.previewTextureName;
        }
        if (StringUtils.isBlank(textureName) && !this.renderContext.getAnimationBundle().getTextures().isEmpty()) {
            textureName = this.renderContext.getAnimationBundle().getTextures().getKeyAt(0);
        }
        return StringUtils.defaultString(textureName);
    }

    private void ensurePreviewReady() {
        if (!this.modelIdHolder.isModelReady()) {
            this.modelIdHolder.initModelWithTexture(this.previewModelId, this.previewTextureName);
        }
    }

    private static boolean isShiftDown(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
    }

    public void renderTooltip(GuiGraphicsExtractor GuiGraphicsExtractor, Screen screen, int mouseX, int mouseY) {
        if (isHovered()) {
            GuiGraphicsExtractor.pose().pushMatrix();
            GuiGraphicsExtractor.pose().translate(0.0f, 0.0f);
            Minecraft minecraft = Minecraft.getInstance();
            String selected = minecraft.getLanguageManager().getSelected();
            if (!Objects.equals(this.cachedLanguage, selected)) {
                this.cachedLanguage = selected;
                this.detailedTooltipLines = null;
                this.tooltipLines = null;
            }
            if (isShiftDown(minecraft)) {
                if (this.detailedTooltipLines == null) {
                    this.detailedTooltipLines = ModelMetadataPresenter.buildModelTooltip(this.renderContext, selected, this.modelIdHolder.getModelId(), true);
                }
                GuiGraphicsExtractor.setComponentTooltipForNextFrame(minecraft.font, this.detailedTooltipLines, mouseX, mouseY);
/*                 GuiGraphicsExtractor.renderComponentTooltip(Minecraft.getInstance().font, this.detailedTooltipLines, mouseX, mouseY);
 */
            } else {
                if (this.tooltipLines == null) {
                    this.tooltipLines = ModelMetadataPresenter.buildModelTooltip(this.renderContext, selected, this.modelIdHolder.getModelId(), false);
                }
                GuiGraphicsExtractor.setComponentTooltipForNextFrame(minecraft.font, this.tooltipLines, mouseX, mouseY);
/*                 GuiGraphicsExtractor.renderComponentTooltip(Minecraft.getInstance().font, this.tooltipLines, mouseX, mouseY);
 */
            }
            GuiGraphicsExtractor.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        return !this.isStarred && super.mouseClicked(event, flag);
    }

    @Override
    protected void extractContents(net.minecraft.client.gui.GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor GuiGraphicsExtractor = extractor;
        ensurePreviewReady();
        AnimationTracker c0117x8455a741Mo1262xaffeef43 = this.modelIdHolder.getAnimationStateMachine();
        if (isHovered()) {
            this.lastHoverTime = PlatformUtil.getMillis();
            c0117x8455a741Mo1262xaffeef43.setPreviousAnimation(this.modelId);
        } else if (PlatformUtil.getMillis() - this.lastHoverTime < this.animationDuration) {
            c0117x8455a741Mo1262xaffeef43.setPreviousAnimation(this.modelName);
        } else {
            c0117x8455a741Mo1262xaffeef43.setPreviousAnimation("empty");
        }
        if (isFocused()) {
            c0117x8455a741Mo1262xaffeef43.setQueuedAnimation(this.authorName);
        } else {
            c0117x8455a741Mo1262xaffeef43.setQueuedAnimation("empty");
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int x = getX();
        int y = getY();
        GuiGraphicsExtractor.fillGradient(x, y, x + this.width, y + this.height, this.backgroundColor, this.backgroundColor);
        if (this.backgroundTexture != null) {
            GlStateManager._enableBlend(0);
            GlStateManager._blendFuncSeparate(770, 771, 1, 0);
            GuiGraphicsExtractor.blit(this.backgroundTexture.getResourceLocation().get(), x, y, x + this.width, y + this.height, 0.0f, 1.0f, 0.0f, 1.0f);
            GlStateManager._disableBlend(0);
        }
        int previewBottom = y + this.height - 20;
        GuiGraphicsExtractor.enableScissor(x, y, x + this.width, previewBottom);
        ModelPreviewRenderer.renderLivingEntityPreview(GuiGraphicsExtractor, x, y, x + this.width, previewBottom, x + (this.width / 2.0f), y + (this.height / 2.0f) + 20.0f, 30.0f, partialTick, this.modelIdHolder, RendererManager.getPlayerRenderer(), this.disablePreviewRotation, true);
        GuiGraphicsExtractor.disableScissor();
        int starZ = 3500;
        if (this.foregroundTexture != null) {
            GlStateManager._enableBlend(0);
            GlStateManager._blendFuncSeparate(770, 771, 1, 0);
            GuiGraphicsExtractor.blit(this.foregroundTexture.getResourceLocation().get(), x, y, x + this.width, y + this.height, 0.0f, 1.0f, 0.0f, 1.0f);
            GlStateManager._disableBlend(0);
        }
        if (this.isStarred) {
            GuiGraphicsExtractor.fillGradient(x, y, x + this.width, y + this.height, -1625152990, -1625152990);
        }
        List listSplit = font.split(getMessage(), 45);
        if (listSplit.size() > 1) {
            drawCenteredString(GuiGraphicsExtractor, font, (FormattedCharSequence) listSplit.get(0), x + (this.width / 2), (y + this.height) - 19, 0xFFF3F3E0);
            drawCenteredString(GuiGraphicsExtractor, font, (FormattedCharSequence) listSplit.get(1), x + (this.width / 2), (y + this.height) - 10, 0xFFF3F3E0);
        } else {
            drawCenteredString(GuiGraphicsExtractor, font, getMessage(), x + (this.width / 2), (y + this.height) - 15, 0xFFF3F3E0);
        }
        if (!this.isStarred && isHoveredOrFocused()) {
            GuiGraphicsExtractor.fillGradient(x, y + 1, x + 1, (y + this.height) - 1, -790560, -790560);
            GuiGraphicsExtractor.fillGradient(x, y, x + this.width, y + 1, -790560, -790560);
            GuiGraphicsExtractor.fillGradient((x + this.width) - 1, y + 1, x + this.width, (y + this.height) - 1, -790560, -790560);
            GuiGraphicsExtractor.fillGradient(x, (y + this.height) - 1, x + this.width, y + this.height, -790560, -790560);
        }
        if (minecraft.player != null) {
            StarModelsCapability.get(minecraft.player).ifPresent(cap -> {
                if (cap.containsModel(this.modelIdHolder.getModelId())) {
                    int iconX = (x + this.width) - 14;
                    GuiGraphicsExtractor.blit(ICON_TEXTURE, iconX, y, iconX + 16, y + 16, 0.0f, 16.0f / 256.0f, 0.0f, 16.0f / 256.0f);
                }
            });
        }
    }

    private static void drawCenteredString(GuiGraphicsExtractor GuiGraphicsExtractor, Font font, Component component, int centerX, int y, int color) {
        GuiGraphicsExtractor.text(font, component, centerX - (font.width(component) / 2), y, color, true);
    }

    private static void drawCenteredString(GuiGraphicsExtractor GuiGraphicsExtractor, Font font, FormattedCharSequence formattedCharSequence, int centerX, int y, int color) {
        GuiGraphicsExtractor.text(font, formattedCharSequence, centerX - (font.width(formattedCharSequence) / 2), y, color, true);
    }
}




