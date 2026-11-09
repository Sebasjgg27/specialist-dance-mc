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

    private static final List<String> CATEGORIES = List.of("Music", "Dance", "Animation", "Rules", "Content");
    private static final List<String> RULE_HELP = List.of(
        "mined - trigger when you mine this block and pick up its drop",
        "pickup - trigger whenever you collect this item, no source",
        "mob - trigger when you kill this mob and pick up its drop",
        "container - trigger when you take this item from this container"
    );
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
    private int headerTop;
    private int paneTop;
    private int previewTop;
    private int previewBottom;
    private int cursor;
    private int listBottom;
    private boolean hasList() {
        return category == 0 || category == 1;
    }
    private int nextRow() {
        int row = cursor;
        cursor += ROW_HEIGHT + GAP;
        return row;
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
        contentX = left + SIDEBAR_WIDTH + PANE_GAP;

        headerTop = 8;
        paneTop = headerTop + this.font.lineHeight + 6;
        sidebarTop = paneTop;
        paneBottom = this.height - 8 - ROW_HEIGHT;
        listBottom = paneBottom - (hasList() ? ROW_HEIGHT + GAP : 0) - 4;
        previewTop = paneTop;
        previewBottom = paneBottom - 4;
        cursor = paneTop;

        switch (category) {
            case 0 -> addMusicRows();
            case 1 -> addDanceRows();
            case 2 -> addAnimationRows();
            case 3 -> addOreRows();
            default -> addContentRows();
        }

        rowsBottom = cursor - GAP;

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(contentX, paneBottom, contentWidth, ROW_HEIGHT)
                .build()
        );
    }

    private void addMusicRows() {
        List<String> tracks = music.getAvailableTracks();

        if (!tracks.contains(config.musicTrack)) {
            config.musicTrack = tracks.get(0);
        }

        addRenderableWidget(
            Checkbox.builder(Component.literal("Music Enabled"), this.font)
                .pos(contentX, nextRow())
                .selected(config.musicEnabled)
                .onValueChange((checkbox, selected) -> {
                    config.musicEnabled = selected;
                    if (!selected) music.stop();
                })
                .build()
        );

        addRenderableWidget(new VolumeSlider(contentX, nextRow(), contentWidth, ROW_HEIGHT));

        int half = (contentWidth - 10) / 2;
        int previewRow = nextRow();

        addRenderableWidget(
            Button.builder(Component.literal("Play preview"), button -> {
                DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
                music.play(music.resolveTrackForDance(dance.name(), dance.track()));
            })
                .bounds(contentX, previewRow, half, ROW_HEIGHT)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Stop"), button -> music.stop())
                .bounds(contentX + half + 10, previewRow, half, ROW_HEIGHT)
                .build()
        );

        addRenderableWidget(
            new NameList(
                contentX, cursor, contentWidth, listBottom - cursor,
                tracks, config.musicTrack,
                name -> config.musicTrack = name,
                false
            )
        );

        addRenderableWidget(
            Button.builder(Component.literal("Open music folder"), button -> openFolder("music"))
                .bounds(contentX, listBottom + GAP, contentWidth, ROW_HEIGHT)
                .build()
        );

    }

    private void addDanceRows() {
        List<String> dances = danceRegistry.getAvailableDances();

        if (!dances.contains(config.activeDance)) {
            config.activeDance = dances.get(0);
        }

        addRenderableWidget(
            Button.builder(Component.literal("Preview dance on close"), button -> previewDance = true)
                .bounds(contentX, nextRow(), contentWidth, ROW_HEIGHT)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Position dance"), button -> 
                minecraft.setScreen(new DanceLayoutScreen(
                    config, danceRegistry, 
                    SpecialistDanceClient.getInstance().getDanceTextures(), this))
            )
                .bounds(contentX, nextRow(), contentWidth, ROW_HEIGHT)
                .build()
        );

        addRenderableWidget(
            new NameList(
                contentX, cursor, contentWidth, listBottom - cursor,
                dances, config.activeDance,
                name -> config.activeDance = name,
                true
            )
        );
    }

    private void addAnimationRows() {
        List<String> behaviors = SpecialistDanceConfig.ANIMATION_END_BEHAVIORS;

        if (!behaviors.contains(config.animationEndBehavior)) {
            config.animationEndBehavior = "stop";
        }

        addRenderableWidget(
            CycleButton.<String>builder(STRING_COMPONENT, requireString(config.animationEndBehavior))
                .withValues(behaviors)
                .create(
                    contentX, nextRow(), contentWidth, ROW_HEIGHT,
                    Component.literal("End behavior"),
                    (button, value) -> config.animationEndBehavior = value
                )
        );

        addRenderableWidget(new SpeedSlider(contentX, nextRow(), contentWidth, ROW_HEIGHT));
    }

    private void addOreRows() {
        addRenderableWidget(
            Button.builder(Component.literal("Your rules"), button ->
                minecraft.setScreen(new OreTriggersScreen(config, this)))
                .bounds(contentX, nextRow(), contentWidth, ROW_HEIGHT)
                .build()
        );
    }

    private void addContentRows() {
        int half = (contentWidth - 10) / 2;
        int foldersRow = nextRow();

        addRenderableWidget(
            Button.builder(Component.literal("Open music folder"), button -> openFolder("music"))
            .bounds(contentX, foldersRow, half, ROW_HEIGHT)
            .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Open dances folder"), button -> openFolder("dances"))
                .bounds(contentX + half + 10, foldersRow, half, ROW_HEIGHT)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Rescan"), button -> {
                List<String> added = danceRegistry.scan();
                music.scanUserTracks();
                SpecialistDanceClient.getInstance().getDanceTextures().release(minecraft);
                SpecialistDanceClient.getInstance().queueLayoutFor(added);
                rebuildWidgets();
            })
            .bounds(contentX, nextRow(), contentWidth, ROW_HEIGHT)
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
        graphics.fill(sidebarX - 6, paneTop - 20, contentX + contentWidth + 6, paneBottom + ROW_HEIGHT + 6, 0x80000000);
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, headerTop, 0xFFFFFFFF);

        int step = ROW_HEIGHT + GAP;
        int dividerX = contentX - PANE_GAP / 2;

        graphics.fill(dividerX, paneTop - 6, dividerX + 1, paneBottom, 0x40FFFFFF);

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

        graphics.drawString(this.font, CATEGORIES.get(category), contentX, paneTop - 6, 0xFFB0B0B0);

        if (category == 0) {
            DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
            String track = music.resolveTrackForDance(dance.name(), dance.track());

            graphics.drawString(
                this.font,
                "gif used: " + dance.name() + " · track: " + track,
                contentX,
                listBottom + GAP + ROW_HEIGHT + GAP,
                0xFFB0B0B0
            );
        }

        if (category == 2) {
            drawDancePreview(graphics);
        }

        if (category == 3) {
            int enabled = 0;

            for (SpecialistDanceConfig.Rule r : config.rules) {
                if (r.enabled) enabled++;
            }

            graphics.drawString(
                this.font,
                config.rules.size() + " rules (" + enabled + " enabled)",
                contentX, rowsBottom + GAP + 2, 0xFFB0B0B0
            );

            int y = rowsBottom + GAP + 14;

            for (String line : RULE_HELP) {
                graphics.drawString(this.font, line, contentX, y, 0xFFA0A0A0);
                y += this.font.lineHeight + 2;
            }
        }
    }

    private void drawDancePreview(GuiGraphics graphics) {
        DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
        int top = previewTop;
        int boxHeight = previewBottom - top;

        if (boxHeight < 24) {
            return;
        }

        graphics.renderOutline(contentX - 1, top - 1, contentWidth + 2, boxHeight + 2, 0x40FFFFFF);

        if (dance.frameCount() <= 0) {
            graphics.drawCenteredString(this.font, "No frames in " + dance.name(),
                contentX + contentWidth / 2, top + boxHeight / 2 - this.font.lineHeight / 2, 0xFFB0B0B0);
            return;
        }

        SpecialistDanceClient client = SpecialistDanceClient.getInstance();
        int duration = client.effectiveFrameDurationMs(dance);
        int index = (int) ((System.currentTimeMillis() / duration) % dance.frameCount());
        DanceTextures.Frame frame = client.getDanceTextures().get(minecraft, dance, index);

        if (frame == null) {
            graphics.drawCenteredString(this.font, "Frames not loaded yet",
                contentX + contentWidth / 2, top + boxHeight / 2 - this.font.lineHeight / 2, 0xFFB0B0B0);
            return;
        }

        double scale = Math.min((double) contentWidth / frame.width(), (double) boxHeight / frame.height());
        int width = Math.max(1, (int) Math.round(frame.width() * scale));
        int height = Math.max(1, (int) Math.round(frame.height() * scale));
        int x = contentX + (contentWidth - width) / 2;
        int y = top + (boxHeight - height) / 2;

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            Objects.requireNonNull(frame.texture()),
            x, y, 0.0f, 0.0f, width, height,
            frame.width(), frame.height(), frame.width(), frame.height()
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
