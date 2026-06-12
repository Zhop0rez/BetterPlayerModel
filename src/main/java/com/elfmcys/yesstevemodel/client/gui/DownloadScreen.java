package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import com.elfmcys.yesstevemodel.client.gui.resource.ResourceDownloadManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Objects;


public class DownloadScreen extends Screen {
    private static final int OUTER_MARGIN = 8;
    private static final int MIN_PANEL_WIDTH = 320;
    private static final int MAX_PANEL_WIDTH = 620;
    private static final int MIN_PANEL_HEIGHT = 220;
    private static final int MAX_PANEL_HEIGHT = 420;
    private static final int HEADER_HEIGHT = 48;
    private static final int COMPACT_HEADER_HEIGHT = 72;
    private static final int FOOTER_HEIGHT = 38;
    private static final int SECTION_LABEL_HEIGHT = 13;
    private static final int SECTION_GAP = 6;
    private static final int ROW_HEIGHT = 30;

    private final PlayerModelScreen parentScreen;
    private final ResourceStationScreen resourceStationScreen;
    private int guiLeft;
    private int guiTop;
    private int guiWidth;
    private int guiHeight;
    private int rows;
    private int unfinishedRows;
    private int page;

    public DownloadScreen(PlayerModelScreen modelScreen) {
        this(modelScreen, null);
    }

    public DownloadScreen(PlayerModelScreen modelScreen, ResourceStationScreen resourceStationScreen) {
        super(Component.translatable("gui.better_player_model.resource_station.download_page.title"));
        this.parentScreen = modelScreen;
        this.resourceStationScreen = resourceStationScreen;
    }

    @Override
    public void init() {
        clearWidgets();
        updateLayout();
        int topY = this.guiTop + 8;
        int left = this.guiLeft + 10;
        int right = this.guiLeft + this.guiWidth - 10;
        Component stationLabel = Component.translatable("gui.better_player_model.resource_station.title");
        Component clearLabel = Component.translatable("gui.better_player_model.resource_station.clear_finished");
        Component returnLabel = Component.translatable("gui.better_player_model.model.return");
        int stationW = buttonWidth(stationLabel, 58, 126);
        int returnW = buttonWidth(returnLabel, 58, 86);
        int clearW = Math.min(buttonWidth(clearLabel, 74, 126), Math.max(74, right - left - stationW - returnW - 12));
        if (compactHeader()) {
            clearW = Math.max(74, right - left);
        }
        addRenderableWidget(new FlatColorButton(left, topY, stationW, 18, stationLabel, b -> {
            Minecraft.getInstance().setScreen(this.resourceStationScreen == null ? new ResourceStationScreen(this.parentScreen) : this.resourceStationScreen);
        }));
        int clearX = compactHeader() ? left : right - returnW - 6 - clearW;
        int clearY = compactHeader() ? topY + 22 : topY;
        addRenderableWidget(new FlatColorButton(clearX, clearY, clearW, 18, clearLabel, b -> {
            ResourceDownloadManager.clearFinished();
            this.page = 0;
            init();
        }));
        addRenderableWidget(new FlatColorButton(right - returnW, topY, returnW, 18, returnLabel, b -> Minecraft.getInstance().setScreen(this.parentScreen)));
        int footerY = this.guiTop + this.guiHeight - 25;
        addRenderableWidget(new FlatColorButton(this.guiLeft + Math.max(10, this.guiWidth / 2 - 60), footerY, 52, 16, Component.translatable("gui.better_player_model.pre_page"), b -> {
            if (this.page > 0) {
                this.page--;
                init();
            }
        }));
        addRenderableWidget(new FlatColorButton(this.guiLeft + Math.min(this.guiWidth - 70, this.guiWidth / 2 + 16), footerY, 52, 16, Component.translatable("gui.better_player_model.next_page"), b -> {
            int maxPage = maxPage(ResourceDownloadManager.snapshot().finishedTasks().size());
            if (this.page < maxPage) {
                this.page++;
                init();
            }
        }));
    }

    @Override
    public void tick() {
        super.tick();
        ResourceDownloadManager.tick();
    }

    @Override
    public void render(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        renderBackground(extractor);
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        extractor.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + this.guiWidth, this.guiTop + this.guiHeight, -14540254, -14540254);
        drawFirstLine(extractor, Component.translatable("gui.better_player_model.resource_station.download_page.title"), this.guiWidth - 24, this.guiLeft + 12, this.guiTop + (compactHeader() ? 53 : 31), 0xFFF3F3E0);
        renderUnfinishedTasks(extractor, snapshot);
        renderFinishedTasks(extractor, snapshot);
        renderFooter(extractor, snapshot);
        super.render(extractor, mouseX, mouseY, partialTick);
    }

    private void renderUnfinishedTasks(GuiGraphics extractor, ResourceDownloadManager.Snapshot snapshot) {
        List<ResourceDownloadManager.TaskSnapshot> tasks = snapshot.unfinishedTasks();
        int x = this.guiLeft + 10;
        int y = this.guiTop + headerHeight();
        int w = this.guiWidth - 20;
        drawFirstLine(extractor, Component.translatable("gui.better_player_model.resource_station.download_page.tasks").withStyle(ChatFormatting.YELLOW), w, x, y, 0xFFF3F3E0);
        int rowY = y + SECTION_LABEL_HEIGHT;
        if (tasks.isEmpty()) {
            extractor.fillGradient(x, rowY, x + w, rowY + ROW_HEIGHT - 3, -12369342, -12369342);
            drawFirstLine(extractor, Component.translatable("gui.better_player_model.resource_station.download_page.idle").withStyle(ChatFormatting.GRAY), w - 12, x + 6, rowY + 8, 0xFFAAAAAA);
            return;
        }
        int count = Math.min(this.unfinishedRows, tasks.size());
        for (int i = 0; i < count; i++) {
            renderTaskRow(extractor, tasks.get(i), x, rowY + i * ROW_HEIGHT, w);
        }
    }

    private void renderTaskRow(GuiGraphics extractor, ResourceDownloadManager.TaskSnapshot task, int x, int y, int w) {
        extractor.fillGradient(x, y, x + w, y + ROW_HEIGHT - 3, -12369342, -12369342);
        drawFirstLine(extractor, Component.literal(task.name()), w - 12, x + 6, y + 3, 0xFFF3F3E0);
        String meta = task.state() + "  " + Math.round(task.progress() * 100f) + "%";
        if (!Objects.equals(task.message(), Component.empty())) {
            meta += "  " + task.message().getString();
        }
        drawFirstLine(extractor, Component.literal(meta).withStyle(statusStyle(task.state())), w - 12, x + 6, y + 15, 0xFFAAAAAA);
        if (task.state() == ResourceDownloadManager.TaskState.DOWNLOADING || task.state() == ResourceDownloadManager.TaskState.IMPORTING
                || task.state() == ResourceDownloadManager.TaskState.UPLOADING) {
            renderProgressBar(extractor, task.progress(), x + 6, y + 25, w - 12);
        }
    }

    private void renderProgressBar(GuiGraphics extractor, float progress, int x, int y, int w) {
        extractor.fillGradient(x, y, x + w, y + 3, -16777216, -16777216);
        extractor.fillGradient(x, y, x + (int) (w * progress), y + 3, -14774017, -14774017);
    }

    private void renderFinishedTasks(GuiGraphics extractor, ResourceDownloadManager.Snapshot snapshot) {
        List<ResourceDownloadManager.TaskSnapshot> tasks = snapshot.finishedTasks();
        int x = this.guiLeft + 10;
        int w = this.guiWidth - 20;
        int startY = this.guiTop + headerHeight() + SECTION_LABEL_HEIGHT + this.unfinishedRows * ROW_HEIGHT + SECTION_GAP;
        drawFirstLine(extractor, Component.translatable("gui.better_player_model.resource_station.download_page.finished").withStyle(ChatFormatting.YELLOW), w, x, startY, 0xFFF3F3E0);
        int rowY = startY + SECTION_LABEL_HEIGHT;
        if (tasks.isEmpty()) {
            extractor.fillGradient(x, rowY, x + w, rowY + ROW_HEIGHT - 3, -13421773, -13421773);
            drawFirstLine(extractor, Component.translatable("gui.better_player_model.resource_station.download_page.no_finished").withStyle(ChatFormatting.GRAY), w - 12, x + 6, rowY + 8, 0xFFAAAAAA);
            return;
        }
        int start = this.page * this.rows;
        for (int i = 0; i < this.rows; i++) {
            int index = start + i;
            if (index >= tasks.size()) {
                break;
            }
            ResourceDownloadManager.TaskSnapshot task = tasks.get(index);
            int y = rowY + i * ROW_HEIGHT;
            extractor.fillGradient(x, y, x + w, y + ROW_HEIGHT - 3, -13421773, -13421773);
            drawFirstLine(extractor, Component.literal(task.name()), w - 12, x + 6, y + 3, 0xFFF3F3E0);
            String text = task.state() + "  " + Math.round(task.progress() * 100f) + "%";
            if (!Objects.equals(task.message(), Component.empty())) {
                text += "  " + task.message().getString();
            }
            drawFirstLine(extractor, Component.literal(text).withStyle(statusStyle(task.state())), w - 12, x + 6, y + 15, 0xFFAAAAAA);
        }
    }

    private void renderFooter(GuiGraphics extractor, ResourceDownloadManager.Snapshot snapshot) {
        int maxPage = maxPage(snapshot.finishedTasks().size());
        int footerY = this.guiTop + this.guiHeight - 21;
        extractor.drawString(this.font, Component.literal((this.page + 1) + "/" + (maxPage + 1)), this.guiLeft + this.guiWidth / 2 - 2, footerY + 4, 0xFFF3F3E0);
        String queue = Component.translatable("gui.better_player_model.resource_station.queue_status", snapshot.queued(), snapshot.done(), snapshot.failed()).getString();
        drawFirstLine(extractor, Component.literal(queue).withStyle(ChatFormatting.GRAY), Math.max(80, this.guiWidth / 2 - 12), this.guiLeft + 12, footerY - 12, 0xFFAAAAAA);
        if (!Objects.equals(snapshot.status(), Component.empty())) {
            drawFirstLine(extractor, snapshot.status().copy().withStyle(snapshot.statusColor()), Math.max(100, this.guiWidth / 2 - 16), this.guiLeft + Math.max(90, this.guiWidth / 2 + 24), footerY - 12, 0xFFF3F3E0);
        }
    }

    private ChatFormatting statusStyle(ResourceDownloadManager.TaskState state) {
        return switch (state) {
            case DONE -> ChatFormatting.GREEN;
            case FAILED -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
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

    private void updateLayout() {
        this.guiWidth = Math.min(MAX_PANEL_WIDTH, Math.max(1, this.width - OUTER_MARGIN * 2));
        this.guiHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(1, this.height - OUTER_MARGIN * 2));
        this.guiLeft = Math.max(0, (this.width - this.guiWidth) / 2);
        this.guiTop = Math.max(0, (this.height - this.guiHeight) / 2);
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        int rowSlots = Math.max(2, (this.guiHeight - headerHeight() - FOOTER_HEIGHT - SECTION_LABEL_HEIGHT * 2 - SECTION_GAP) / ROW_HEIGHT);
        int wantedUnfinishedRows = snapshot.unfinishedTasks().isEmpty() ? 1 : Math.min(3, snapshot.unfinishedTasks().size());
        this.unfinishedRows = Math.max(1, Math.min(wantedUnfinishedRows, rowSlots - 1));
        this.rows = Math.max(1, rowSlots - this.unfinishedRows);
        this.page = Math.min(this.page, maxPage(snapshot.finishedTasks().size()));
    }

    private boolean compactHeader() {
        return this.guiWidth < 380;
    }

    private int headerHeight() {
        return compactHeader() ? COMPACT_HEADER_HEIGHT : HEADER_HEIGHT;
    }

    private int maxPage(int taskCount) {
        return Math.max(0, (taskCount - 1) / Math.max(1, this.rows));
    }

    private int buttonWidth(Component label, int minWidth, int maxWidth) {
        return Math.min(maxWidth, Math.max(minWidth, this.font.width(label) + 12));
    }
}
