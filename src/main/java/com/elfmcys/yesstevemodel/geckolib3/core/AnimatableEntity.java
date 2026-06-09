package com.elfmcys.yesstevemodel.geckolib3.core;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.animation.ControllerActionResolver;
import com.elfmcys.yesstevemodel.client.animation.debug.AnimationFrameProfiler;
import com.elfmcys.yesstevemodel.audio.IAudioStreamFactory;
import com.elfmcys.yesstevemodel.client.event.ClientTickEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.AnimationState;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.manager.AnimationData;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.IAnimationController;
import com.elfmcys.yesstevemodel.client.animation.molang.PhysicsManager;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import rip.ysm.api.entity.EntityDataBridge;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.storage.IForeignVariableStorage;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.core.processor.AnimationProcessor;
import com.elfmcys.yesstevemodel.geckolib3.core.processor.IBone;
import com.elfmcys.yesstevemodel.geckolib3.model.provider.data.EntityModelData;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.AnimationContext;
import com.elfmcys.yesstevemodel.geckolib3.core.util.RateLimiter;
import com.elfmcys.yesstevemodel.geckolib3.util.MovementQuery;
import com.elfmcys.yesstevemodel.util.log.ILogger;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AnimatableEntity<TEntity extends Entity> {

    private final EntityFrameStateTracker<TEntity> positionTracker;

    public final TEntity entity;

    private AnimatedGeoModel currentModel;

    private Object2ReferenceMap<String, List<IValue>> animationMap;

    public boolean wasAnimationActiveLastTick;

    public boolean hasUpdatedThisTick;

    public boolean isTickTriggered;

    public boolean wasEvaluatedLastFrame;

    public float seekTime;

    private int lastAnimationEvaluationFrameId = -1;

    private float lastAnimationEvaluationSeekTime = Float.NaN;

    private boolean lastAnimationEvaluationActive;

    @Nullable
    private AnimatedGeoModel lastAnimationEvaluationModel;

    private int lastAnimationEvaluationBoneCount;

    private int lastAnimationEvaluationControllerCount;

    private boolean lastAnimationEvaluationPreview;

    private boolean lastAnimationEvaluationExtraPlayer;

    private final AnimationData manager = new AnimationData();

    public float lastTick = -1.0f;

    public boolean isFirstFrameAfterReset = true;

    public boolean needsReset = false;

    public boolean modelInitialized = false;

    public Map<String, AnimationState> animationStates = Maps.newHashMap();

    private final AnimationProcessor<TEntity> animationProcessor = new AnimationProcessor<>(this);

    private final RateLimiter rateLimiter = new RateLimiter();

    public final PhysicsManager physicsManager = new PhysicsManager();

    public interface AnimationControllerVisitor extends Consumer<Consumer<IAnimationController<?>>> {
    }

    public abstract Identifier getTextureLocation();

    public abstract boolean isModelReady();

    public abstract float getHeightScale();

    public abstract float getWidthScale();

    @Nullable
    public abstract Animation getAnimation(String str);

    public abstract void registerAnimationControllers();

    public AnimatableEntity(TEntity tentity) {
        this.entity = tentity;
        this.positionTracker = createPositionTracker(tentity);
        this.rateLimiter.setRefreshRate(getRefreshRate());
    }

    public void reset() {
        this.currentModel = null;
        this.animationMap = null;
        this.animationProcessor.reset();
        this.physicsManager.clear();
        this.rateLimiter.reset();
        this.manager.clear();
        this.positionTracker.reset();
        this.lastTick = -1.0f;
        this.wasAnimationActiveLastTick = false;
        this.hasUpdatedThisTick = false;
        this.isTickTriggered = false;
        this.isFirstFrameAfterReset = true;
        this.needsReset = false;
        this.wasEvaluatedLastFrame = false;
        this.seekTime = 0.0f;
        this.lastAnimationEvaluationFrameId = -1;
        this.lastAnimationEvaluationSeekTime = Float.NaN;
        this.lastAnimationEvaluationActive = false;
        this.lastAnimationEvaluationModel = null;
        this.lastAnimationEvaluationBoneCount = 0;
        this.lastAnimationEvaluationControllerCount = 0;
        this.lastAnimationEvaluationPreview = false;
        this.lastAnimationEvaluationExtraPlayer = false;
        this.animationStates.clear();
    }

    public EntityFrameStateTracker<TEntity> createPositionTracker(TEntity tentity) {
        return new EntityFrameStateTracker<>(tentity);
    }

    public EntityFrameStateTracker<TEntity> getPositionTracker() {
        return this.positionTracker;
    }

    public float getSeekTime() {
        return this.seekTime;
    }

    public <T extends AnimatableEntity<TEntity>> void addAnimationController(IAnimationController controller) {
        this.manager.addAnimationController(controller);
    }

    public AnimationData getAnimationData() {
        return this.manager;
    }

    @Nullable
    public IValue resolveExpression(String str) {
        return null;
    }

    public Optional<IAudioStreamFactory> getAudioStreamFactory(String str) {
        return Optional.empty();
    }

    @Nullable
    public final List<IValue> getAnimationExpressions(String str) {
        return this.animationMap.get(str);
    }

    public PhysicsManager getPhysicsManager() {
        return this.physicsManager;
    }

    @Nullable
    public AnimationController getAnimationEntries(String str) {
        return null;
    }

    public int getTextureIndex() {
        return 0;
    }

    public float getScale() {
        return 0.15f;
    }

    public void setupAnim(float seekTime, boolean z) {
    }

    public void afterSetupAnim(float seekTime, boolean z) {
    }

    public final TEntity getEntity() {
        return this.entity;
    }

    public boolean hasCustomTexture() {
        return false;
    }

    @Nullable
    public IBone getBone(int i) {
        return this.animationProcessor.getBone(i);
    }

    public boolean shouldRenderOverlay() {
        return true;
    }

    public int getRefreshRate() {
        TEntity tentity = (TEntity) Minecraft.getInstance().player;
        if (tentity != null && tentity != this.entity) {
            Vec3 vec3Position = tentity.position();
            if (vec3Position.x != 0.0d || vec3Position.y != 0.0d || vec3Position.z != 0.0d) {
                if (!this.isFirstFrameAfterReset) {
                    float fDistanceTo = tentity.distanceTo(this.entity);
                    if (fDistanceTo > 64.0f) {
                        return 15;
                    }
                    if (fDistanceTo > 32.0f) {
                        return 30;
                    }
                }
                return ClientTickEvent.getRefreshRate();
            }
        }
        return ClientTickEvent.getRefreshRate();
    }

    @Nullable
    public final AnimationEvent<?> processAnimation(float partialTick) {
        return processAnimationImpl(partialTick, ModelPreviewRenderer.isFirstPersonOnRenderThread());
    }

    @Nullable
    public AnimationEvent<?> processAnimationImpl(float partialTick, boolean z) {
        if (this.currentModel == null) {
            return null;
        }
        partialTick = sanitizePartialTick(partialTick);
        Entity entity = this.entity;
        LivingEntity livingEntity = entity instanceof LivingEntity ? (LivingEntity) entity : null;
        PlayerCapability playerCapability = this instanceof PlayerCapability cap ? cap : null;
        int tickCount = this instanceof IPreviewAnimatable ? ClientTickEvent.getTickCount() : entity.tickCount;
        float frameTime = partialTick;
        boolean shouldSit = entity.isPassenger() && entity.getVehicle() != null && EntityDataBridge.shouldRiderSit(entity.getVehicle());
        float limbSwingAmount = 0.0f;
        float limbSwing = 0.0f;
        if (!shouldSit && entity.isAlive() && livingEntity != null) {
            boolean renderStateMovementSuppressed = false;
            boolean remotePlayer = playerCapability != null && !playerCapability.isLocalPlayerModel();
            if (playerCapability != null && playerCapability.hasRenderState()) {
                limbSwingAmount = playerCapability.getRenderStateWalkAnimationSpeed();
                limbSwing = playerCapability.getRenderStateWalkAnimationPos();
                if (remotePlayer
                        && ControllerActionResolver.getRemotePlayerGroundSpeed(playerCapability, entity, limbSwingAmount) <= ControllerActionResolver.MIN_MOVEMENT_SPEED) {
                    limbSwingAmount = 0.0f;
                    limbSwing = 0.0f;
                    renderStateMovementSuppressed = true;
                }
            } else {
                limbSwingAmount = livingEntity.walkAnimation.speed(partialTick);
                limbSwing = livingEntity.walkAnimation.position(partialTick);
                if (remotePlayer
                        && ControllerActionResolver.getRemotePlayerGroundSpeed(playerCapability, entity, limbSwingAmount) <= ControllerActionResolver.MIN_MOVEMENT_SPEED) {
                    limbSwingAmount = 0.0f;
                    limbSwing = 0.0f;
                    renderStateMovementSuppressed = true;
                }
            }
            if (!renderStateMovementSuppressed && limbSwingAmount <= 1.0E-4f) {
                float movementSpeed = Mth.clamp(playerCapability != null && !playerCapability.isLocalPlayerModel()
                        ? ControllerActionResolver.getRemotePlayerGroundSpeed(playerCapability, entity, limbSwingAmount)
                        : MovementQuery.getGroundSpeed(entity, this.positionTracker, null), 0.0f, 1.0f);
                if (movementSpeed > 1.0E-4f) {
                    limbSwingAmount = movementSpeed;
                    limbSwing = this.seekTime * 0.6662f;
                }
            }
            if (livingEntity.isBaby()) {
                limbSwing *= 3.0f;
            }
        }
        EntityModelData modelData = new EntityModelData();
        modelData.isSitting = shouldSit;

        float lerpBodyRot = 0.0f;
        float lerpHeadRot = 0.0f;
        float netHeadYaw = 0.0f;
        if (playerCapability != null && playerCapability.hasRenderState()) {
            modelData.isChild = livingEntity != null && livingEntity.isBaby();
            lerpBodyRot = playerCapability.getRenderStateBodyRot();
            netHeadYaw = playerCapability.getRenderStateNetHeadYaw();
        } else if (livingEntity != null) {
            modelData.isChild = livingEntity.isBaby();
            lerpBodyRot = Mth.rotLerp(partialTick, livingEntity.yBodyRotO, livingEntity.yBodyRot);
            lerpHeadRot = Mth.rotLerp(partialTick, livingEntity.yHeadRotO, livingEntity.yHeadRot);
            netHeadYaw = lerpHeadRot - lerpBodyRot;
        }

        if (shouldSit && (entity.getVehicle() instanceof LivingEntity vehicle)) {
            lerpBodyRot = Mth.rotLerp(partialTick, vehicle.yBodyRotO, vehicle.yBodyRot);
            netHeadYaw = lerpHeadRot - lerpBodyRot;
            float clampedHeadYaw = Mth.clamp(Mth.wrapDegrees(lerpHeadRot - lerpBodyRot), -85.0f, 85.0f);

            lerpBodyRot = lerpHeadRot - clampedHeadYaw;
            if (clampedHeadYaw * clampedHeadYaw > 2500f) {
                lerpBodyRot += clampedHeadYaw * 0.2f;
            }

            netHeadYaw = lerpHeadRot - lerpBodyRot;
        }
        modelData.rawHeadPitch = playerCapability != null && playerCapability.hasRenderState()
                ? playerCapability.getRenderStateHeadPitch()
                : Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        modelData.headPitch = -modelData.rawHeadPitch;
        modelData.rawNetHeadYaw = netHeadYaw;
        modelData.netHeadYaw = -Mth.clamp(Mth.wrapDegrees(netHeadYaw), -85.0f, 85.0f);
        modelData.lerpBodyRot = lerpBodyRot;
        modelData.lerpedAge = tickCount + partialTick;
        AnimationEvent<AnimatableEntity<TEntity>> event = new AnimationEvent<>(this, limbSwing, limbSwingAmount, tickCount, partialTick, frameTime, Math.abs(limbSwingAmount) > 1.0E-4f, z, modelData);
        AnimationContext<?> context = new AnimationContext<>(entity, this, event, modelData);
        context.setLogger(getLogger());
        setCustomAnimations(context, event);
        return event;
    }

    public void setCustomAnimations(AnimationContext<?> ctx, @NotNull AnimationEvent<AnimatableEntity<TEntity>> event) {
        float currentTick = event.currentTick;
        boolean z = !shouldSkipAnimation(event);
        if (currentTick > this.lastTick) {
            this.hasUpdatedThisTick = false;
            this.isTickTriggered = false;
            this.lastTick = currentTick;
            this.rateLimiter.setRefreshRate(getRefreshRate());
            this.isFirstFrameAfterReset = this.needsReset;
            this.needsReset = false;
        } else {
            currentTick = this.lastTick;
        }
        if (this.manager.startTick == -1.0f) {
            this.manager.startTick = currentTick;
        } else {
            float f2 = currentTick - this.manager.startTick;
            float f3 = f2 - this.manager.limbSwing;
            if (f3 > 0.0f) {
                this.manager.limbSwing = f2;
                this.seekTime += f3;
            }
        }
        event.currentTick = this.seekTime;
        this.positionTracker.updateState(event.getTickCount(), this.seekTime, event.getFrameTime());
        if (!this.animationProcessor.isDisabled()) {
            this.isTickTriggered |= this.rateLimiter.request(this.seekTime / 20.0f);
            boolean z2 = (this.isTickTriggered && !this.hasUpdatedThisTick) || this.wasAnimationActiveLastTick || z;
            boolean z3 = (!z || (this.seekTime == 0.0f && !this.hasUpdatedThisTick)) && this.isTickTriggered && !this.hasUpdatedThisTick;
            resetHeadTracking(this.wasEvaluatedLastFrame);
            AnimationFrameProfiler.Scope profilerScope = null;
            if (z2) {
                if (z3) {
                    this.hasUpdatedThisTick = true;
                }
                int renderFrameId = AnimationFrameProfiler.getRenderFrameId();
                int boneCount = getEvaluationContext().getBoneCount();
                int controllerCount = this.manager.getAnimationControllers().size();
                boolean canReuseEvaluation = this.lastAnimationEvaluationFrameId == renderFrameId
                     && Float.compare(this.lastAnimationEvaluationSeekTime, this.seekTime) == 0
                     && this.lastAnimationEvaluationActive == z
                     && this.lastAnimationEvaluationModel == this.currentModel
                     && this.lastAnimationEvaluationBoneCount == boneCount
                     && this.lastAnimationEvaluationControllerCount == controllerCount
                     && this.lastAnimationEvaluationPreview == ModelPreviewRenderer.isPreview()
                     && this.lastAnimationEvaluationExtraPlayer == ModelPreviewRenderer.isExtraPlayer();
                if (canReuseEvaluation) {
                    AnimationFrameProfiler.logReusedEvaluation(this, event, this.seekTime);
                } else {
                    if (this.lastAnimationEvaluationFrameId == renderFrameId) {
                        AnimationFrameProfiler.logReuseMiss(this, event, getReuseMissReason(z, boneCount, controllerCount), this.seekTime, this.lastAnimationEvaluationSeekTime, z, this.lastAnimationEvaluationActive, boneCount, this.lastAnimationEvaluationBoneCount, controllerCount, this.lastAnimationEvaluationControllerCount);
                    }
                    profilerScope = AnimationFrameProfiler.beginEvaluation(this, event, currentTick, this.seekTime, z, z3, boneCount, controllerCount);
                    try {
                        getPhysicsManager().update(this.seekTime);
                        setupAnim(this.seekTime, z3);
                        getEvaluationContext().tickAnimation(event, ctx, z, shouldRenderOverlay());
                        afterSetupAnim(this.seekTime, z3);
                        this.wasAnimationActiveLastTick = z;
                        this.lastAnimationEvaluationFrameId = renderFrameId;
                        this.lastAnimationEvaluationSeekTime = this.seekTime;
                        this.lastAnimationEvaluationActive = z;
                        this.lastAnimationEvaluationModel = this.currentModel;
                        this.lastAnimationEvaluationBoneCount = boneCount;
                        this.lastAnimationEvaluationControllerCount = controllerCount;
                        this.lastAnimationEvaluationPreview = ModelPreviewRenderer.isPreview();
                        this.lastAnimationEvaluationExtraPlayer = ModelPreviewRenderer.isExtraPlayer();
                    } finally {
                        AnimationFrameProfiler.endEvaluation(profilerScope);
                    }
                }
            }
            applyHeadTracking(event, z2);
            this.wasEvaluatedLastFrame = z2;
        }
    }

    private static float sanitizePartialTick(float partialTick) {
        if (!Float.isFinite(partialTick)) {
            return 0.0f;
        }
        return Mth.clamp(partialTick, 0.0f, 1.0f);
    }

    private String getReuseMissReason(boolean animationActive, int boneCount, int controllerCount) {
        if (this.lastAnimationEvaluationModel != this.currentModel) {
            return "model_changed";
        }
        if (this.lastAnimationEvaluationBoneCount != boneCount || this.lastAnimationEvaluationControllerCount != controllerCount) {
            return "shape_changed";
        }
        if (Float.compare(this.lastAnimationEvaluationSeekTime, this.seekTime) != 0) {
            return "seekTime_changed";
        }
        if (this.lastAnimationEvaluationActive != animationActive) {
            return "active_changed";
        }
        return "unknown";
    }

    public void applyHeadTracking(AnimationEvent<? extends AnimatableEntity<TEntity>> event, boolean z) {
    }

    public void resetHeadTracking(boolean z) {
    }

    public AnimationProcessor<TEntity> getEvaluationContext() {
        return this.animationProcessor;
    }

    public void initAnimationControllers(@NotNull GeoModel model, Object2ReferenceMap<String, List<IValue>> object2ReferenceMap) {
        reset();
        this.currentModel = new AnimatedGeoModel(model);
        this.animationMap = object2ReferenceMap;
        registerAnimationControllers();
        this.animationProcessor.initBones(this.currentModel, object2ReferenceMap);
        setCurrentModel(this.currentModel);
    }

    public void clearAnimationControllers() {
        if (this.currentModel != null) {
            GeoModel model = this.currentModel.getGeoModel();
            Object2ReferenceMap<String, List<IValue>> object2ReferenceMap = this.animationMap;
            reset();
            initAnimationControllers(model, object2ReferenceMap);
        }
    }

    @Nullable
    public final AnimatedGeoModel getCurrentModel() {
        return this.currentModel;
    }

    public void setCurrentModel(AnimatedGeoModel model) {
    }

    public boolean shouldSkipAnimation(AnimationEvent<?> event) {
        return true;
    }

    public void resetAnimationState() {
        this.needsReset = true;
    }

    public void executeExpression(IValue value, boolean isClientPlayer, boolean executeBeforeAnimation, @Nullable Consumer<String> consumer) {
        this.animationProcessor.execute(value, isClientPlayer, executeBeforeAnimation, consumer);
    }

    public IForeignVariableStorage getPropertyGetter() {
        return this.animationProcessor.getPublicVariableStorage();
    }

    public void markModelInitialized() {
        this.modelInitialized = true;
    }

    public boolean isModelInitialized() {
        return this.modelInitialized;
    }

    @Nullable
    public ILogger getLogger() {
        return null;
    }

    public boolean isDebugMode() {
        return Minecraft.getInstance().level == this.entity.level() && !this.entity.isRemoved();
    }

    public void setAnimationState(String name, AnimationState state) {
        this.animationStates.put(name, state);
    }

    public AnimationState getAnimationState(String name) {
        return this.animationStates.getOrDefault(name, AnimationState.IDLE);
    }
}
