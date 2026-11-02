package com.sebas.specialistdance.config.menu;

import java.util.List;
import java.util.Locale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

import com.sebas.specialistdance.SpecialistDanceClient;
import com.sebas.specialistdance.audio.MusicManager;
import com.sebas.specialistdance.config.SpecialistDanceConfig;
import com.sebas.specialistdance.dance.DanceRegistry;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SpecialistDanceConfigScreen extends Screen {

    private static final int BOX_WIDTH = 300;
    private static final int ROW_HEIGHT = 18;
    private static final int GAP = 2;
    private static final int ROWS = 12;
    private static final int TOP_Y = 30;

    private final SpecialistDanceConfig config;
    private final MusicManager music;
    private final DanceRegistry danceRegistry;
    private final Screen parent;

    private boolean previewDance;
    private int scroll;
    private int maxScroll;

    public SpecialistDanceConfigScreen(
            SpecialistDanceConfig config,
            MusicManager music,
            DanceRegistry danceRegistry,
            Screen parent) {
        super(Component.literal("Specialist Dance"));
        this.config = config;
        this.music = music;
        this.danceRegistry = danceRegistry;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentHeight = ROWS * ROW_HEIGHT + (ROWS - 1) * GAP;
        int x = this.width / 2 - BOX_WIDTH / 2;

        int viewportBottom = this.height - 8;
        int viewport = viewportBottom - TOP_Y;

        maxScroll = Math.max(0, contentHeight - viewport);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int row = maxScroll == 0 ? Math.max(TOP_Y, (viewportBottom - contentHeight) / 2) : TOP_Y - scroll;

        addRenderableWidget(
            Checkbox.builder(Component.literal("Music Enabled"), this.font)
                .pos(x, row)
                .selected(config.musicEnabled)
                .onValueChange((checkbox, selected) -> {
                    config.musicEnabled = selected;
                    if (!selected) {
                        music.stop();
                    }
                })
                .build()
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(new VolumeSlider(x, row, BOX_WIDTH, ROW_HEIGHT));

        row += ROW_HEIGHT + GAP;

        List<String> tracks = music.getAvailableTracks();

        if (!tracks.contains(config.musicTrack)) {
            config.musicTrack = tracks.get(0);
        }

        addRenderableWidget(
            CycleButton.<String>builder(Component::literal, config.musicTrack)
                .withValues(tracks)
                .create(
                    x, row, BOX_WIDTH, ROW_HEIGHT,
                    Component.literal("Music track"),
                    (button, value) -> config.musicTrack = value
                )
        );

        row += ROW_HEIGHT + GAP;

        int half = (BOX_WIDTH - 10) / 2;

        addRenderableWidget(
            Button.builder(Component.literal("Play preview"), button -> {
                DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
                music.play(music.resolveTrackForDance(dance.name(), dance.track()));
            })
                .bounds(x, row, half, ROW_HEIGHT)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Stop"), button -> music.stop())
                .bounds(x + half + 10, row, half, ROW_HEIGHT)
                .build()
        );

        row += ROW_HEIGHT + GAP;

        List<String> dances = danceRegistry.getAvailableDances();

        if (!dances.contains(config.activeDance)) {
            config.activeDance = dances.get(0);
        }

        addRenderableWidget(
            CycleButton.<String>builder(Component::literal, config.activeDance)
                .withValues(dances)
                .create(
                    x, row, BOX_WIDTH, ROW_HEIGHT,
                    Component.literal("Dance pack"),
                    (button, value) -> config.activeDance = value
                )
        );

        row += ROW_HEIGHT + GAP;

        List<String> behaviors = SpecialistDanceConfig.ANIMATION_END_BEHAVIORS;

        if (!behaviors.contains(config.animationEndBehavior)) {
            config.animationEndBehavior = "stop";
        }

        addRenderableWidget(
            CycleButton.<String>builder(Component::literal, config.animationEndBehavior)
                .withValues(behaviors)
                .create(
                    x, row, BOX_WIDTH, ROW_HEIGHT,
                    Component.literal("End behavior"),
                    (button, value) -> config.animationEndBehavior = value
                )
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(new SpeedSlider(x, row, BOX_WIDTH, ROW_HEIGHT));

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(
            Button.builder(Component.literal("Preview dance on close"), button -> previewDance = true)
                .bounds(x, row, BOX_WIDTH, ROW_HEIGHT)
                .build()
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(
            Button.builder(Component.literal("Position dance"), button -> {
                minecraft.setScreen(new DanceLayoutScreen(config, danceRegistry, SpecialistDanceClient.getInstance().getDanceTextures(), this));
            })
                .bounds(x, row, BOX_WIDTH, ROW_HEIGHT)
                .build()
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(
          Button.builder(Component.literal("Open music folder"), button -> {
            Path musicDir = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("specialist-dance")
                .resolve("music");
            try {
                Files.createDirectories(musicDir);
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) {
                    new ProcessBuilder("open", musicDir.toString()).start();
                } else if (os.contains("win")) {
                    new ProcessBuilder("explorer", musicDir.toString()).start();
                } else {
                    new ProcessBuilder("xdg-open", musicDir.toString()).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
          })  
            .bounds(x, row, half, ROW_HEIGHT)
            .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Open dances folder"), button -> {
                Path dancesDir = FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("specialist-dance")
                    .resolve("dances");
                try {
                    Files.createDirectories(dancesDir);
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("mac")) {
                        new ProcessBuilder("open", dancesDir.toString()).start();
                    } else if (os.contains("win")) {
                        new ProcessBuilder("explorer", dancesDir.toString()).start();
                    } else {
                        new ProcessBuilder("xdg-open", dancesDir.toString()).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            })
                .bounds(x + half + 10, row, half, ROW_HEIGHT)
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
            .bounds(x, row, BOX_WIDTH, ROW_HEIGHT)
            .build()
        );

        row += ROW_HEIGHT + GAP;

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(x, row, BOX_WIDTH, ROW_HEIGHT)
                .build()
        );
    }

    @Override 
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll == 0) {
            return false;
        }

        int step = ROW_HEIGHT + GAP;
        int clamped = Math.max(0, Math.min(scroll + (scrollY > 0 ? -step : step), maxScroll));

        if (clamped == scroll) {
            return true;
        }

        scroll = clamped;
        rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(
            this.font,
            this.title,
            this.width / 2,
            12,
            0xFFFFFFFF
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
