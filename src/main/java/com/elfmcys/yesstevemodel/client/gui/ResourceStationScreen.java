package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.gui.resource.ModelRepoClient;
import com.elfmcys.yesstevemodel.client.gui.resource.ModelRepoEntry;
import com.elfmcys.yesstevemodel.client.gui.resource.ResourceDownloadManager;
import com.elfmcys.yesstevemodel.client.gui.resource.ResourceStationConfig;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.elfmcys.yesstevemodel.client.upload.ModelUploadSession;
import com.elfmcys.yesstevemodel.mixin.client.ScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ResourceStationScreen extends Screen {
    private static final int OUTER_MARGIN = 8;
    private static final int MIN_PANEL_WIDTH = 320;
    private static final int MAX_PANEL_WIDTH = 620;
    private static final int MIN_PANEL_HEIGHT = 220;
    private static final int MAX_PANEL_HEIGHT = 420;
    private static final int HEADER_HEIGHT = 56;
    private static final int COMPACT_HEADER_HEIGHT = 100;
    private static final int FOOTER_HEIGHT = 58;
    private static final int ENTRY_HEIGHT = 32;
    private static final int ENTRY_BUTTON_AREA_WIDTH = 92;
    private static final ExecutorService RESOURCE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "YSM Resource Station");
        thread.setDaemon(true);
        return thread;
    });

    private final PlayerModelScreen parentScreen;
    private ResourceStationConfig.State config;
    private final List<ModelRepoEntry> entries = new ArrayList<>();
    private final Map<String, ResourceLocation> previewTextures = new HashMap<>();
    private final Set<String> loadingPreviews = new HashSet<>();
    private EditBox urlBox;
    private EditBox searchBox;
    private FlatColorButton nativeModeButton;
    private FlatColorButton mainlandModeButton;
    private final Object configSaveLock = new Object();
    private int guiLeft;
    private int guiTop;
    private int guiWidth;
    private int guiHeight;
    private int entriesPerPage = 1;
    private int page;
    private int sourceIndex;
    private boolean listLoading;
    private boolean active;
    private boolean queuedListRefresh;
    private int listRequestId;
    private volatile int configSaveRequestId;
    private volatile ListResult pendingListResult;
    private SortMode sortMode = SortMode.NAME;
    private Component status = Component.empty();
    private ChatFormatting statusColor = ChatFormatting.GRAY;

    public ResourceStationScreen(PlayerModelScreen parentScreen) {
        super(Component.translatable("gui.better_player_model.resource_station.title"));
        this.parentScreen = parentScreen;
        this.config = ResourceStationConfig.load();
        this.sourceIndex = Math.max(0, this.config.urls().indexOf(this.config.selectedUrl()));
    }

    @Override
    public void init() {
        this.active = true;
        clearWidgets();
        updateLayout();
        String urlValue = this.urlBox == null ? this.config.selectedUrl() : this.urlBox.getValue();
        String searchValue = this.searchBox == null ? "" : this.searchBox.getValue();
        boolean urlFocused = this.urlBox != null && this.urlBox.isFocused();
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();

        int labelWidth = labelWidth();
        int contentLeft = this.guiLeft + 10;
        int contentRight = this.guiLeft + this.guiWidth - 10;
        boolean compactHeader = compactHeader();
        Component refreshLabel = Component.translatable("gui.better_player_model.resource_station.refresh");
        Component saveLabel = Component.translatable("gui.better_player_model.resource_station.save");
        Component deleteLabel = Component.translatable("gui.better_player_model.resource_station.delete_source");
        int refreshW = buttonWidth(refreshLabel, 42, 64);
        int saveW = buttonWidth(saveLabel, 34, 54);
        int deleteW = buttonWidth(deleteLabel, 34, 60);
        int sourceNavW = 18;
        int topControlsWidth = refreshW + saveW + deleteW + sourceNavW * 2 + 16;
        int topButtonY = compactHeader ? this.guiTop + 29 : this.guiTop + 7;
        int topControlsX = compactHeader ? contentLeft : contentRight - topControlsWidth;
        int urlX = contentLeft + labelWidth;
        int urlW = compactHeader ? Math.max(80, contentRight - urlX) : Math.max(80, topControlsX - urlX - 6);
        this.urlBox = new EditBox(this.font, urlX, this.guiTop + 8, urlW, 16, Component.literal("Resource URL"));
        this.urlBox.setMaxLength(2048);
        this.urlBox.setValue(urlValue);
        this.urlBox.setTextColor(0xFFF3F3E0);
        this.urlBox.setFocused(urlFocused);
        addWidget(this.urlBox);

        Component sortLabel = Component.translatable("gui.better_player_model.resource_station.sort");
        Component queueAllLabel = Component.translatable("gui.better_player_model.resource_station.queue_all");
        int sortW = buttonWidth(sortLabel, 52, 64);
        int queueAllW = buttonWidth(queueAllLabel, 62, 86);
        int searchX = urlX;
        int searchY = compactHeader ? this.guiTop + 52 : this.guiTop + 30;
        int modeWidth = compactHeader ? Math.min(180, this.guiWidth - 20) : 156;
        int modeX = compactHeader ? contentLeft : contentRight - modeWidth;
        int searchControlsRight = compactHeader ? contentRight : modeX - 6;
        int maxSearchW = compactHeader ? 160 : 180;
        int searchW = Math.min(maxSearchW, Math.max(70, searchControlsRight - searchX - sortW - queueAllW - 14));
        this.searchBox = new EditBox(this.font, searchX, searchY, searchW, 16, Component.literal("Search"));
        this.searchBox.setMaxLength(2048);
        this.searchBox.setValue(searchValue);
        this.searchBox.setTextColor(0xFFF3F3E0);
        this.searchBox.setFocused(searchFocused);
        addWidget(this.searchBox);
        if (searchFocused) {
            setFocused(this.searchBox);
        } else if (urlFocused) {
            setFocused(this.urlBox);
        }

        int topX = topControlsX;
        addRenderableWidget(new FlatColorButton(topX, topButtonY, refreshW, 18, refreshLabel, b -> refresh()));
        topX += refreshW + 4;
        addRenderableWidget(new FlatColorButton(topX, topButtonY, saveW, 18, saveLabel, b -> saveUrl()));
        topX += saveW + 4;
        addRenderableWidget(new FlatColorButton(topX, topButtonY, deleteW, 18, deleteLabel, b -> deleteSource()));
        topX += deleteW + 4;
        addRenderableWidget(new FlatColorButton(topX, topButtonY, sourceNavW, 18, Component.literal("<"), b -> switchSource(-1)).setTooltipText("gui.better_player_model.resource_station.prev_source"));
        topX += sourceNavW + 4;
        addRenderableWidget(new FlatColorButton(topX, topButtonY, sourceNavW, 18, Component.literal(">"), b -> switchSource(1)).setTooltipText("gui.better_player_model.resource_station.next_source"));
        int sortX = searchX + searchW + 6;
        FlatColorButton sortButton = new FlatColorButton(sortX, searchY - 1, sortW, 18, sortLabel, b -> cycleSort());
        sortButton.setTooltipLines(List.of(Component.translatable("gui.better_player_model.resource_station.sort_mode", this.sortMode.label())));
        addRenderableWidget(sortButton);
        addRenderableWidget(new FlatColorButton(sortX + sortW + 4, searchY - 1, queueAllW, 18, queueAllLabel, b -> enqueueAllVisible()));

        int modeY = compactHeader ? this.guiTop + 75 : this.guiTop + 29;
        int nativeWidth = modeWidth / 2;
        int mainlandWidth = modeWidth - nativeWidth;
        this.nativeModeButton = new FlatColorButton(modeX, modeY, nativeWidth, 18, Component.translatable("gui.better_player_model.resource_station.mode.native"), b -> setMainlandMode(false));
        this.nativeModeButton.setSelected(!this.config.preferGithubAccelerator());
        this.nativeModeButton.setTooltipText("gui.better_player_model.resource_station.mode.native.tooltip");
        addRenderableWidget(this.nativeModeButton);
        this.mainlandModeButton = new FlatColorButton(modeX + nativeWidth, modeY, mainlandWidth, 18, Component.translatable("gui.better_player_model.resource_station.mode.mainland"), b -> setMainlandMode(true));
        this.mainlandModeButton.setSelected(this.config.preferGithubAccelerator());
        this.mainlandModeButton.setTooltipText("gui.better_player_model.resource_station.mode.mainland.tooltip");
        addRenderableWidget(this.mainlandModeButton);

        int footerY = footerButtonY();
        Component returnLabel = Component.translatable("gui.better_player_model.model.return");
        Component downloadPageLabel = Component.translatable("gui.better_player_model.resource_station.download_page");
        Component preLabel = Component.translatable("gui.better_player_model.pre_page");
        Component nextLabel = Component.translatable("gui.better_player_model.next_page");
        int returnW = buttonWidth(returnLabel, 58, 78);
        int downloadPageW = buttonWidth(downloadPageLabel, 58, 78);
        int preW = buttonWidth(preLabel, 52, 58);
        int nextW = buttonWidth(nextLabel, 52, 58);
        int centerX = this.guiLeft + this.guiWidth / 2;
        addRenderableWidget(new FlatColorButton(contentRight - returnW, footerY, returnW, 16, returnLabel, b -> Minecraft.getInstance().setScreen(this.parentScreen)));
        addRenderableWidget(new FlatColorButton(contentLeft, footerY, downloadPageW, 16, downloadPageLabel, b -> Minecraft.getInstance().setScreen(new DownloadScreen(this.parentScreen, this))));
        addRenderableWidget(new FlatColorButton(centerX - preW - 14, footerY, preW, 16, preLabel, b -> {
            if (this.page > 0) {
                this.page--;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(centerX + 14, footerY, nextW, 16, nextLabel, b -> {
            int maxPage = maxPage(filteredEntries().size());
            if (this.page < maxPage) {
                this.page++;
                init();
            }
        }));

        List<ModelRepoEntry> visible = filteredEntries();
        clampPage(visible.size());
        int start = this.page * this.entriesPerPage;
        for (int i = 0; i < this.entriesPerPage; i++) {
            int index = start + i;
            if (index >= visible.size()) {
                break;
            }
            ModelRepoEntry entry = visible.get(index);
            int y = entryY(i);
            int buttonX = entryButtonX();
            int retryW = retryButtonWidth();
            int downloadW = downloadButtonWidth();
            addRenderableWidget(new FlatColorButton(buttonX, y + 5, downloadW, 18, Component.translatable("gui.better_player_model.resource_station.download"), b -> enqueue(entry)));
            addRenderableWidget(new FlatColorButton(buttonX + downloadW + 4, y + 5, retryW, 18, Component.translatable("gui.better_player_model.resource_station.retry"), b -> enqueue(entry)));
            ensurePreview(entry);
        }
    }

    @Override
    public void removed() {
        this.active = false;
        this.listRequestId++;
        this.pendingListResult = null;
    }

    @Override
    public void tick() {
        super.tick();
        applyPendingListResult();
        ResourceDownloadManager.tick();
    }

    private void refresh() {
        saveUrl();
        if (this.listLoading) {
            this.queuedListRefresh = true;
            this.status = Component.translatable("gui.better_player_model.resource_station.loading");
            this.statusColor = ChatFormatting.YELLOW;
            return;
        }
        startListRefresh();
    }

    private void startListRefresh() {
        ResourceStationConfig.State requestConfig = this.config;
        String requestUrl = requestConfig.selectedUrl();
        int requestId = ++this.listRequestId;
        this.listLoading = true;
        this.queuedListRefresh = false;
        this.entries.clear();
        this.page = 0;
        this.status = Component.translatable("gui.better_player_model.resource_station.loading");
        this.statusColor = ChatFormatting.YELLOW;
        if (ResourceStationConfig.monitorLogEnabled()) {
            YesSteveModel.LOGGER.info("[YSM-RESOURCE] UI list request start id={} source={}", requestId, requestUrl);
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.list(requestUrl, requestConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RESOURCE_EXECUTOR).orTimeout(taskTimeoutMs(), TimeUnit.MILLISECONDS).whenComplete((result, error) -> {
            this.pendingListResult = new ListResult(requestId, requestUrl, result, error);
            if (ResourceStationConfig.monitorLogEnabled()) {
                YesSteveModel.LOGGER.info("[YSM-RESOURCE] UI list request pending id={} source={} result={} error={}",
                        requestId, requestUrl, result == null ? -1 : result.size(), error == null ? "none" : rootMessage(error));
            }
        });
    }

    private void applyPendingListResult() {
        ListResult pending = this.pendingListResult;
        if (pending == null) {
            return;
        }
        this.pendingListResult = null;
        if (!this.active || pending.requestId != this.listRequestId) {
            return;
        }
        this.listLoading = false;
        if (this.queuedListRefresh || !Objects.equals(this.config.selectedUrl(), pending.sourceUrl)) {
            if (ResourceStationConfig.monitorLogEnabled()) {
                YesSteveModel.LOGGER.info("[YSM-RESOURCE] UI list request superseded id={} requestSource={} currentSource={} queued={}",
                        pending.requestId, pending.sourceUrl, this.config.selectedUrl(), this.queuedListRefresh);
            }
            startListRefresh();
            return;
        }
        if (pending.error != null) {
            this.status = Component.translatable("gui.better_player_model.resource_station.error", rootMessage(pending.error));
            this.statusColor = ChatFormatting.RED;
            if (ResourceStationConfig.monitorLogEnabled()) {
                YesSteveModel.LOGGER.info("[YSM-RESOURCE] UI list request error id={} source={} error={}", pending.requestId, pending.sourceUrl, rootMessage(pending.error));
            }
        } else {
            this.entries.clear();
            this.entries.addAll(pending.entries);
            this.page = 0;
            this.status = Component.translatable("gui.better_player_model.resource_station.loaded", pending.entries.size());
            this.statusColor = ChatFormatting.GREEN;
            if (ResourceStationConfig.monitorLogEnabled()) {
                YesSteveModel.LOGGER.info("[YSM-RESOURCE] UI list request applied id={} source={} entries={}", pending.requestId, pending.sourceUrl, pending.entries.size());
            }
        }
        init();
    }

    private void enqueue(ModelRepoEntry entry) {
        if (ResourceDownloadManager.enqueue(entry, this.config)) {
            this.status = Component.translatable("gui.better_player_model.resource_station.queued", entry.name());
            this.statusColor = ChatFormatting.YELLOW;
            Minecraft.getInstance().setScreen(new DownloadScreen(this.parentScreen, this));
        }
    }

    private void enqueueAllVisible() {
        int added = ResourceDownloadManager.enqueueAll(filteredEntries(), this.config);
        if (added > 0) {
            this.status = Component.translatable("gui.better_player_model.resource_station.queued", added);
            this.statusColor = ChatFormatting.YELLOW;
            Minecraft.getInstance().setScreen(new DownloadScreen(this.parentScreen, this));
        }
    }

    private boolean isQueued(ModelRepoEntry entry) {
        return ResourceDownloadManager.isQueued(entry);
    }

    private void saveUrl() {
        String url = this.urlBox.getValue().trim();
        if (url.isBlank()) {
            return;
        }
        ArrayList<String> urls = new ArrayList<>(this.config.urls());
        if (!urls.contains(url)) {
            urls.add(0, url);
        }
        this.sourceIndex = urls.indexOf(url);
        this.config = new ResourceStationConfig.State(urls, url, this.config.timeoutMs(), this.config.maxDownloadBytes(), this.config.preferGithubAccelerator(), this.config.githubAccelerators());
        clearSourceEntries();
        saveConfigNow();
    }

    private void deleteSource() {
        ArrayList<String> urls = new ArrayList<>(this.config.urls());
        String current = this.urlBox.getValue().trim();
        urls.remove(current);
        if (urls.isEmpty()) {
            return;
        }
        this.sourceIndex = Math.min(this.sourceIndex, urls.size() - 1);
        String selected = urls.get(this.sourceIndex);
        this.config = new ResourceStationConfig.State(urls, selected, this.config.timeoutMs(), this.config.maxDownloadBytes(), this.config.preferGithubAccelerator(), this.config.githubAccelerators());
        clearSourceEntries();
        saveConfigNow();
        this.urlBox.setValue(selected);
        refresh();
    }

    private void switchSource(int delta) {
        List<String> urls = this.config.urls();
        if (urls.isEmpty()) {
            return;
        }
        this.sourceIndex = Math.floorMod(this.sourceIndex + delta, urls.size());
        String selected = urls.get(this.sourceIndex);
        this.config = new ResourceStationConfig.State(urls, selected, this.config.timeoutMs(), this.config.maxDownloadBytes(), this.config.preferGithubAccelerator(), this.config.githubAccelerators());
        clearSourceEntries();
        saveConfigNow();
        this.urlBox.setValue(selected);
        refresh();
    }

    private void clearSourceEntries() {
        this.entries.clear();
        this.previewTextures.clear();
        this.loadingPreviews.clear();
        this.page = 0;
        this.pendingListResult = null;
        init();
    }

    private void setMainlandMode(boolean enabled) {
        if (this.config.preferGithubAccelerator() == enabled) {
            return;
        }
        ResourceStationConfig.State newConfig = new ResourceStationConfig.State(this.config.urls(), this.config.selectedUrl(), this.config.timeoutMs(),
                this.config.maxDownloadBytes(), enabled, this.config.githubAccelerators());
        this.config = newConfig;
        saveConfigAsync(newConfig);
        updateModeButtonSelection();
        this.status = Component.translatable(enabled
                ? "gui.better_player_model.resource_station.mode.mainland.selected"
                : "gui.better_player_model.resource_station.mode.native.selected");
        this.statusColor = ChatFormatting.YELLOW;
    }

    private void updateModeButtonSelection() {
        if (this.nativeModeButton != null) {
            this.nativeModeButton.setSelected(!this.config.preferGithubAccelerator());
        }
        if (this.mainlandModeButton != null) {
            this.mainlandModeButton.setSelected(this.config.preferGithubAccelerator());
        }
    }

    private void saveConfigNow() {
        this.configSaveRequestId++;
        synchronized (this.configSaveLock) {
            ResourceStationConfig.save(this.config);
        }
    }

    private void saveConfigAsync(ResourceStationConfig.State state) {
        int requestId = ++this.configSaveRequestId;
        CompletableFuture.runAsync(() -> {
            synchronized (this.configSaveLock) {
                if (requestId == this.configSaveRequestId) {
                    ResourceStationConfig.save(state);
                }
            }
        }, RESOURCE_EXECUTOR);
    }

    private void cycleSort() {
        this.sortMode = switch (this.sortMode) {
            case NAME -> SortMode.SIZE;
            case SIZE -> SortMode.SOURCE;
            case SOURCE -> SortMode.NAME;
        };
        this.page = 0;
        init();
    }

    private List<ModelRepoEntry> filteredEntries() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<ModelRepoEntry> result = new ArrayList<>(this.entries);
        if (!query.isBlank()) {
            result.removeIf(entry -> !searchText(entry).contains(query));
        }
        Comparator<ModelRepoEntry> comparator = switch (this.sortMode) {
            case SIZE -> Comparator.comparingLong(entry -> entry.size() < 0 ? Long.MAX_VALUE : entry.size());
            case SOURCE -> Comparator.comparing(ModelRepoEntry::description, String.CASE_INSENSITIVE_ORDER).thenComparing(ModelRepoEntry::name, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing(ModelRepoEntry::name, String.CASE_INSENSITIVE_ORDER);
        };
        result.sort(comparator);
        return result;
    }

    private String searchText(ModelRepoEntry entry) {
        return (entry.name() + " " + entry.fileName() + " " + entry.description() + " " + entry.author() + " " + entry.tags()).toLowerCase(Locale.ROOT);
    }

    private void ensurePreview(ModelRepoEntry entry) {
        if (entry.previewUrl() == null || entry.previewUrl().isBlank() || this.previewTextures.containsKey(entry.previewUrl()) || !this.loadingPreviews.add(entry.previewUrl())) {
            return;
        }
        ResourceStationConfig.State requestConfig = this.config;
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.downloadPreview(entry, requestConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RESOURCE_EXECUTOR).orTimeout(Math.max(10_000L, requestConfig.timeoutMs() * 2L), TimeUnit.MILLISECONDS).whenComplete((data, error) -> ((Executor) Minecraft.getInstance()).execute(() -> {
            this.loadingPreviews.remove(entry.previewUrl());
            if (!this.active || error != null) {
                return;
            }
            ResourceLocation id = new ResourceLocation(YesSteveModel.MOD_ID, "resource_station/" + sha1(entry.previewUrl()));
            OuterFileTexture texture = new OuterFileTexture(data);
            texture.doLoad();
            Minecraft.getInstance().getTextureManager().register(id, texture);
            this.previewTextures.put(entry.previewUrl(), id);
        }));
    }

    @Override
    public void render(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        renderBackground(extractor);
        extractor.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + this.guiWidth, this.guiTop + this.guiHeight, -14540254, -14540254);
        int labelWidth = labelWidth() - 4;
        drawFirstLine(extractor, Component.translatable("gui.better_player_model.resource_station.url"), labelWidth, this.guiLeft + 10, this.guiTop + 12, 0xFFF3F3E0);
        drawFirstLine(extractor, Component.translatable("gui.better_player_model.resource_station.search"), labelWidth, this.guiLeft + 10, compactHeader() ? this.guiTop + 56 : this.guiTop + 34, 0xFFF3F3E0);
        this.urlBox.render(extractor, mouseX, mouseY, partialTick);
        this.searchBox.render(extractor, mouseX, mouseY, partialTick);
        List<ModelRepoEntry> visible = filteredEntries();
        clampPage(visible.size());
        int start = this.page * this.entriesPerPage;
        for (int i = 0; i < this.entriesPerPage; i++) {
            int index = start + i;
            if (index >= visible.size()) {
                break;
            }
            ModelRepoEntry entry = visible.get(index);
            int y = entryY(i);
            renderEntry(extractor, entry, y);
        }
        int maxPage = maxPage(visible.size());
        Component pageText = Component.literal((this.page + 1) + "/" + (maxPage + 1));
        int footerY = footerButtonY();
        extractor.drawString(this.font, pageText, this.guiLeft + (this.guiWidth - this.font.width(pageText)) / 2, footerY - 16, 0xFFF3F3E0);
        renderQueueStatus(extractor);
        if (!Objects.equals(this.status, Component.empty())) {
            int statusWidth = Math.max(90, this.guiWidth / 2 - 16);
            drawFirstLine(extractor, this.status.copy().withStyle(this.statusColor), statusWidth, this.guiLeft + 12, footerY - 16, 0xFFF3F3E0);
        }
        super.render(extractor, mouseX, mouseY, partialTick);
        ((ScreenAccessor) this).ysm$getRenderables().stream().filter(renderable -> renderable instanceof FlatColorButton).forEach(renderable -> ((FlatColorButton) renderable).renderTooltip(extractor, this, mouseX, mouseY));
    }

    private void renderEntry(GuiGraphics extractor, ModelRepoEntry entry, int y) {
        int entryLeft = this.guiLeft + 10;
        int entryRight = entryPanelRight();
        int textLeft = entryLeft + 38;
        int textWidth = Math.max(40, entryRight - textLeft - 4);
        extractor.fillGradient(entryLeft, y - 2, entryRight, y + 28, -12369342, -12369342);
        ResourceLocation preview = entry.previewUrl() == null ? null : this.previewTextures.get(entry.previewUrl());
        if (preview != null) {
            extractor.blit(preview, entryLeft + 4, y + 1, 0, 0, 28, 28, 28, 28);
        } else {
            extractor.fillGradient(entryLeft + 4, y + 1, entryLeft + 32, y + 29, -14540254, -14540254);
        }
        drawFirstLine(extractor, Component.literal(entry.name()), textWidth, textLeft, y + 1, 0xFFF3F3E0);
        String meta = entryMeta(entry);
        drawFirstLine(extractor, Component.literal(meta).withStyle(ChatFormatting.GRAY), textWidth, textLeft, y + 11, 0xFFAAAAAA);
        if (!entry.description().isBlank()) {
            drawFirstLine(extractor, Component.literal(entry.description()).withStyle(ChatFormatting.DARK_GRAY), textWidth, textLeft, y + 21, 0xFF888888);
        }
    }

    private void drawFirstLine(GuiGraphics extractor, Component component, int width, int x, int y, int color) {
        if (width <= 0) {
            return;
        }
        List<FormattedCharSequence> lines = this.font.split(component, width);
        if (!lines.isEmpty()) {
            extractor.drawString(this.font, lines.get(0), x, y, color);
        }
    }

    private String entryMeta(ModelRepoEntry entry) {
        List<String> parts = new ArrayList<>();
        if (entry.size() > 0) {
            parts.add(ModelUploadSession.formatBytes((int) Math.min(Integer.MAX_VALUE, entry.size())));
        }
        if (!entry.author().isBlank()) {
            parts.add(entry.author());
        }
        if (!entry.tags().isBlank()) {
            parts.add(entry.tags());
        }
        return String.join("  ", parts);
    }

    private void renderQueueStatus(GuiGraphics extractor) {
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        int queued = snapshot.queued();
        long failed = snapshot.failed();
        long done = snapshot.done();
        String text = Component.translatable("gui.better_player_model.resource_station.queue_status", queued, done, failed).getString();
        int queueY = footerButtonY() - 29;
        ResourceDownloadManager.TaskSnapshot currentTask = snapshot.currentTask();
        if (currentTask != null) {
            int x = this.guiLeft + 12;
            int w = Math.max(80, Math.min(245, this.guiWidth / 2 - 12));
            extractor.fillGradient(x, queueY, x + w, queueY + 6, -16777216, -16777216);
            extractor.fillGradient(x, queueY, x + (int) (w * currentTask.progress()), queueY + 6, -14774017, -14774017);
            text += "  " + currentTask.state() + " " + Math.round(currentTask.progress() * 100f) + "%";
            if (!Objects.equals(currentTask.message(), Component.empty())) {
                text += "  " + currentTask.message().getString();
            }
        }
        int queueX = this.guiLeft + Math.max(12, this.guiWidth / 2 - 40);
        int queueWidth = Math.max(90, this.guiWidth - (queueX - this.guiLeft) - 80);
        drawFirstLine(extractor, Component.literal(text).withStyle(ChatFormatting.GRAY), queueWidth, queueX, queueY, 0xFFAAAAAA);
    }

    private void updateLayout() {
        this.guiWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, this.width - OUTER_MARGIN * 2));
        this.guiHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(MIN_PANEL_HEIGHT, this.height - OUTER_MARGIN * 2));
        this.guiLeft = Math.max(0, (this.width - this.guiWidth) / 2);
        this.guiTop = Math.max(0, (this.height - this.guiHeight) / 2);
        int availableEntryHeight = Math.max(ENTRY_HEIGHT, this.guiHeight - headerHeight() - FOOTER_HEIGHT);
        this.entriesPerPage = Math.max(1, availableEntryHeight / ENTRY_HEIGHT);
        clampPage(this.entries.size());
    }

    private boolean compactHeader() {
        return this.guiWidth < 500;
    }

    private int headerHeight() {
        return compactHeader() ? COMPACT_HEADER_HEIGHT : HEADER_HEIGHT;
    }

    private void clampPage(int entryCount) {
        this.page = Math.min(this.page, maxPage(entryCount));
    }

    private int entryY(int row) {
        return this.guiTop + headerHeight() + row * ENTRY_HEIGHT;
    }

    private int footerButtonY() {
        return this.guiTop + this.guiHeight - 21;
    }

    private int entryButtonX() {
        return this.guiLeft + this.guiWidth - entryButtonAreaWidth() - 10;
    }

    private int entryPanelRight() {
        return entryButtonX() - 8;
    }

    private int maxPage(int entryCount) {
        return Math.max(0, (entryCount - 1) / Math.max(1, this.entriesPerPage));
    }

    private int labelWidth() {
        int urlWidth = this.font.width(Component.translatable("gui.better_player_model.resource_station.url"));
        int searchWidth = this.font.width(Component.translatable("gui.better_player_model.resource_station.search"));
        return Math.min(70, Math.max(42, Math.max(urlWidth, searchWidth) + 6));
    }

    private int buttonWidth(Component label, int minWidth, int maxWidth) {
        return Math.min(maxWidth, Math.max(minWidth, this.font.width(label) + 12));
    }

    private int downloadButtonWidth() {
        return buttonWidth(Component.translatable("gui.better_player_model.resource_station.download"), this.guiWidth >= 420 ? 44 : 42, 74);
    }

    private int retryButtonWidth() {
        return buttonWidth(Component.translatable("gui.better_player_model.resource_station.retry"), this.guiWidth >= 420 ? 44 : 32, 62);
    }

    private int entryButtonAreaWidth() {
        return Math.max(ENTRY_BUTTON_AREA_WIDTH, downloadButtonWidth() + retryButtonWidth() + 4);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        String search = this.searchBox.getValue();
        boolean handled = this.urlBox.charTyped(codePoint, modifiers) || this.searchBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
        if (!Objects.equals(search, this.searchBox.getValue())) {
            this.page = 0;
            init();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        String search = this.searchBox.getValue();
        boolean handled = this.urlBox.keyPressed(keyCode, scanCode, modifiers) || this.searchBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
        if (!Objects.equals(search, this.searchBox.getValue())) {
            this.page = 0;
            init();
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.urlBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.urlBox);
            return true;
        }
        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.searchBox);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private long taskTimeoutMs() {
        return Math.max(15_000L, this.config.timeoutMs() * 4L);
    }

    private static int progressTotal(int contentLength, long entrySize) {
        if (contentLength > 0) {
            return contentLength;
        }
        if (entrySize > 0 && entrySize <= Integer.MAX_VALUE) {
            return (int) entrySize;
        }
        return 0;
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private enum SortMode {
        NAME("name"),
        SIZE("size"),
        SOURCE("source");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        private String label() {
            return this.label;
        }
    }

    private record ListResult(int requestId, String sourceUrl, List<ModelRepoEntry> entries, Throwable error) {
    }
}
