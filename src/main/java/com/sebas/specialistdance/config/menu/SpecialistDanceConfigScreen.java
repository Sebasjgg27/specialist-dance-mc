package com.sebas.specialistdance.config.menu;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.sebas.specialistdance.SpecialistDanceClient;
import com.sebas.specialistdance.audio.MusicManager;
import com.sebas.specialistdance.config.SpecialistDanceConfig;
import com.sebas.specialistdance.dance.DanceRegistry;
import com.sebas.specialistdance.dance.DanceTextures;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

public class SpecialistDanceConfigScreen extends Screen {

    private static final int SIDEBAR_WIDTH = 100;
    private static final int PANE_GAP = 8;
    private static final int CONTENT_WIDTH = 300;
    private static final int ROW_HEIGHT = 18;
    private static final int GAP = 2;
    private static final int TOP_Y = 32;

    private static final List<String> CATEGORIES = List.of("Music", "Dance", "Animation", "Content");
    private static final Function<@NonNull String, Component> STRING_COMPONENT = Component::literal;

    private final SpecialistDanceConfig config;
    private final MusicManager music;
    private final @NonNull DanceRegistry danceRegistry;
    private final Screen parent;

    private boolean previewDance;
    private int category;
    private int sidebarX;
    private int sidebarTop;
    private int contentX;
    private int contentWidth;
    private int paneBottom;
    private int rowsBottom;
    private int listBottom;
    private boolean hasList() {
        return category == 0;
    }

    public SpecialistDanceConfigScreen(
            SpecialistDanceConfig config,
            MusicManager music,
            @NonNull DanceRegistry danceRegistry,
            Screen parent) {
        super(Component.literal("Specialist Dance"));
        this.config = config;
        this.music = music;
        this.danceRegistry = danceRegistry;
        this.parent = parent;
    }

    @Override
    protected void init() {
        contentWidth = Math.min(CONTENT_WIDTH, this.width - SIDEBAR_WIDTH - PANE_GAP - 16);

        int total = SIDEBAR_WIDTH + PANE_GAP + contentWidth;
        int left = this.width / 2 - total / 2;

        sidebarX = left;
        sidebarTop = TOP_Y;
        contentX = left + SIDEBAR_WIDTH + PANE_GAP;
        paneBottom = this.height - 8 - ROW_HEIGHT;

        int rows = rowsFor(category);
        int row = TOP_Y;
        rowsBottom = row + rows * ROW_HEIGHT + (rows - 1) * GAP;
        listBottom = paneBottom - (hasList() ? ROW_HEIGHT + GAP : 0) - 4;

        switch (category) {
            case 0 -> addMusicRows(row);
            case 1 -> addDanceRows(row);
            case 2 -> addAnimationRows(row);
            default -> addContentRows(row);
        }

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(contentX, paneBottom, contentWidth, ROW_HEIGHT)
                .build()
        );
    }

    private int rowsFor(int index) {
        return switch (index) {
            case 0 -> 4;
            case 1 -> 3;
            case 2 -> 2;
            default -> 2;
        };
    }

    private void addMusicRows(int row) {
        addRenderableWidget(
            Checkbox.builder(Component.literal("Music Enabled"), this.font)
                .pos(contentX, row)
                .selected(config.musicEnabled)
                .onValueChange((checkbox, selected) -> {
                    config.musicEnabled = selected;
                    if (!selected) {
                        music.stop();
                    }
                })
                .build()
        );

        addRenderableWidget(
            new NameList(
                contentX, rowsBottom + GAP, contentWidth, listBottom - rowsBottom - GAP,
                music.getAvailableTracks(), config.musicTrack,
                name -> config.musicTrack = name,
                false
            )
        );

        addRenderableWidget(
            Button.builder(Component.literal("Open music folder"), button -> openFolder("music"))
                .bounds(contentX, listBottom + GAP, contentWidth, ROW_HEIGHT)
                .build()
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(new VolumeSlider(contentX, row, contentWidth, ROW_HEIGHT));

        row += ROW_HEIGHT + GAP;

        List<String> tracks = music.getAvailableTracks();

        if (!tracks.contains(config.musicTrack)) {
            config.musicTrack = tracks.get(0);
        }

        addRenderableWidget(
            CycleButton.<String>builder(STRING_COMPONENT, requireString(config.musicTrack))
                .withValues(tracks)
                .create(
                    contentX, row, contentWidth, ROW_HEIGHT,
                    Component.literal("Music track"),
                    (button, value) -> config.musicTrack = value
                )
        );

        row += ROW_HEIGHT + GAP;

        int half = (contentWidth - 10) / 2;

        addRenderableWidget(
            Button.builder(Component.literal("Play preview"), button -> {
                DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
                music.play(music.resolveTrackForDance(dance.name(), dance.track()));
            })
                .bounds(contentX, row, half, ROW_HEIGHT)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Stop"), button -> music.stop())
                .bounds(contentX + half + 10, row, half, ROW_HEIGHT)
                .build()
        );
    }

    private void addDanceRows(int row) {
        List<String> dances = danceRegistry.getAvailableDances();

        if (!dances.contains(config.activeDance)) {
            config.activeDance = dances.get(0);
        }

        addRenderableWidget(
            new NameList(
                contentX, row, contentWidth, 3 * ROW_HEIGHT + 2 * GAP,
                dances, config.activeDance,
                name -> config.activeDance = name,
                true
            )
        );

        row += 3 * ROW_HEIGHT + 3 * GAP;

        addRenderableWidget(
            Button.builder(Component.literal("Preview dance on close"), button -> previewDance = true)
                .bounds(contentX, row, contentWidth, ROW_HEIGHT)
                .build()
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(
            Button.builder(Component.literal("Position dance"), button -> {
                minecraft.setScreen(new DanceLayoutScreen(config, danceRegistry, SpecialistDanceClient.getInstance().getDanceTextures(), this));
            })
                .bounds(contentX, row, contentWidth, ROW_HEIGHT)
                .build()
        );

        rowsBottom = row + ROW_HEIGHT;
    }

    private void addAnimationRows(int row) {
        List<String> behaviors = SpecialistDanceConfig.ANIMATION_END_BEHAVIORS;

        if (!behaviors.contains(config.animationEndBehavior)) {
            config.animationEndBehavior = "stop";
        }

        addRenderableWidget(
            CycleButton.<String>builder(STRING_COMPONENT, requireString(config.animationEndBehavior))
                .withValues(behaviors)
                .create(
                    contentX, row, contentWidth, ROW_HEIGHT,
                    Component.literal("End behavior"),
                    (button, value) -> config.animationEndBehavior = value
                )
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(new SpeedSlider(contentX, row, contentWidth, ROW_HEIGHT));
    }

    private void addContentRows(int row) {
        int half = (contentWidth - 10) / 2;

        addRenderableWidget(
            Button.builder(Component.literal("Open music folder"), button -> openFolder("music"))
            .bounds(contentX, row, half, ROW_HEIGHT)
            .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Open dances folder"), button -> openFolder("dances"))
                .bounds(contentX + half + 10, row, half, ROW_HEIGHT)
                .build()
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(
            Button.builder(Component.literal("Rescan"), button -> {
                List<String> added = danceRegistry.scan();
                music.scanUserTracks();
                SpecialistDanceClient.getInstance().getDanceTextures().release(minecraft);
                SpecialistDanceClient.getInstance().queueLayoutFor(added);
                rebuildWidgets();
            })
            .bounds(contentX, row, contentWidth, ROW_HEIGHT)
            .build()
        );
    }

    private void openFolder(String folder) {
        Path directory = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("specialist-dance")
            .resolve(folder);

        try {
            Files.createDirectories(directory);
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

            if (os.contains("mac")) {
                new ProcessBuilder("open", directory.toString()).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("explorer", directory.toString()).start();
            } else {
                new ProcessBuilder("xdg-open", directory.toString()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static @NonNull String requireString(@Nullable String value) {
        if (value == null) {
            throw new IllegalStateException("Expected a non-null config or list value");
        }

        return value;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        int step = ROW_HEIGHT + GAP;

        if (event.button() == 0
                && event.x() >= sidebarX
                && event.x() < sidebarX + SIDEBAR_WIDTH
                && event.y() >= sidebarTop
                && event.y() < sidebarTop + CATEGORIES.size() * step) {
            category = (int) ((event.y() - sidebarTop) / step);
            rebuildWidgets();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0x60000000);
        graphics.fill(sidebarX - 6, TOP_Y - 20, contentX + contentWidth + 6, paneBottom + ROW_HEIGHT + 6, 0x80000000);
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(
            this.font,
            this.title,
            this.width / 2,
            12,
            0xFFFFFFFF
        );

        int step = ROW_HEIGHT + GAP;
        int dividerX = contentX - PANE_GAP / 2;

        graphics.fill(dividerX, TOP_Y - 6, dividerX + 1, paneBottom, 0x40FFFFFF);

        for (int i = 0; i < CATEGORIES.size(); i++) {
            boolean active = i == category;

            graphics.drawString(
                this.font,
                (active ? "> " : "  ") + CATEGORIES.get(i),
                sidebarX + 4,
                sidebarTop + i * step + 5,
                active ? 0xFFFFFFFF : 0xFFA0A0A0
            );
        }

        graphics.drawString(this.font, CATEGORIES.get(category), contentX, TOP_Y - 6, 0xFFB0B0B0);

        if (category == 1) {
            drawDancePreview(graphics);
        }
    }

    private void drawDancePreview(GuiGraphics graphics) {
        DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
        int top = rowsBottom + GAP;
        int boxHeight = paneBottom - 6 - top;

        if (dance.frameCount() <= 0 || boxHeight < 24) {
            return;
        }

        SpecialistDanceClient client = SpecialistDanceClient.getInstance();
        int duration = client.effectiveFrameDurationMs(dance);
        int index = (int) ((System.currentTimeMillis() / duration) % dance.frameCount());
        DanceTextures.Frame frame = client.getDanceTextures().get(minecraft, dance, index);

        if (frame == null) {
            return;
        }

        double scale = Math.min((double) contentWidth / frame.width(), (double) boxHeight / frame.height());
        int width = Math.max(1, (int) Math.round(frame.width() * scale));
        int height = Math.max(1, (int) Math.round(frame.height() * scale));
        int x = contentX + (contentWidth - width) / 2;
        int y = top + (boxHeight - height) / 2;

        graphics.renderOutline(contentX - 1, top - 1, contentWidth + 2, boxHeight + 2, 0x40FFFFFF);

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            Objects.requireNonNull(frame.texture()),
            x,
            y,
            0.0f,
            0.0f,
            width,
            height,
            frame.width(),
            frame.height(),
            frame.width(),
            frame.height()
        );
    }

    public void refreshWidgets() {
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        music.stop();
        config.save();
        this.minecraft.setScreen(parent);

        if (previewDance) {
            SpecialistDanceClient.getInstance().triggerDance();
        }
    }

    private class NameList extends ObjectSelectionList<DanceNameListEntry> {

        private final Consumer<String> onPick;

        NameList(
                int x,
                int y,
                int width,
                int height,
                List<String> names,
                String selected,
                Consumer<String> onPick,
                boolean showDanceMetadata) {
            super(SpecialistDanceConfigScreen.this.minecraft, width, height, x, y);
            this.onPick = onPick;

            for (String name : names) {
                DanceNameListEntry entry = new DanceNameListEntry(
                    requireString(name),
                    font,
                    danceRegistry,
                    config.frameDurationMs,
                    showDanceMetadata
                );
                addEntry(entry);

                if (name.equals(selected)) {
                    setSelected(entry);
                }
            }
        }

        @Override
        public void setSelected(@Nullable DanceNameListEntry entry) {
            super.setSelected(entry);

            if (entry != null) {
                onPick.accept(entry.name());
            }
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) {
                return super.mouseClicked(event, doubleClick);
            }

            DanceNameListEntry entry = getEntryAtPosition(event.x(), event.y());

            if (entry != null) {
                setSelected(entry);
                return true;
            }

            return super.mouseClicked(event, doubleClick);
        }

    }

    private class VolumeSlider extends AbstractSliderButton {

        VolumeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Volume"), config.musicVolume);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Volume: " + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            config.musicVolume = (float) value;
            updateMessage();
            music.applyVolume();
        }
    }

    private class SpeedSlider extends AbstractSliderButton {

        private static final double SPEED_RANGE = SpecialistDanceConfig.MAX_ANIMATION_SPEED
            - SpecialistDanceConfig.MIN_ANIMATION_SPEED;

        SpeedSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Speed"),
                Math.clamp((config.animationSpeed - SpecialistDanceConfig.MIN_ANIMATION_SPEED) / SPEED_RANGE, 0.0, 1.0));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Animation speed: "
                + String.format(Locale.ROOT, "%.1f", config.animationSpeed) + "x"));
        }

        @Override
        protected void applyValue() {
            config.animationSpeed = SpecialistDanceConfig.MIN_ANIMATION_SPEED + value * SPEED_RANGE;
            updateMessage();
        }
    }
}
