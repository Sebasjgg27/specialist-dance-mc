package com.sebas.specialistdance.config.menu;

import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

import com.sebas.specialistdance.SpecialistDanceClient;
import com.sebas.specialistdance.SpecialistDanceConfig;
import com.sebas.specialistdance.audio.MusicManager;
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
    private static final int ROWS = 11;
    private static final int TOP_Y = 30;
    private static final int NOTE_HEIGHT = 12;

    private final SpecialistDanceConfig config;
    private final MusicManager music;
    private final DanceRegistry danceRegistry;
    private final Screen parent;

    private boolean previewDance;
    private int noteY;

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

        int doneY = this.height - ROW_HEIGHT - 20;
        int row = Math.max(TOP_Y, (doneY - contentHeight - NOTE_HEIGHT) / 2);

        doneY = Math.max(doneY, row + contentHeight + NOTE_HEIGHT + 6);
        noteY = row + contentHeight + 3;

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
            Button.builder(Component.literal("Play preview"), button -> music.play())
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

        List<String> behaviors = List.of("stop", "hold", "loop");

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
                music.scanUserTracks();
                danceRegistry.scan();
                rebuildWidgets();
            })
            .bounds(x, row, BOX_WIDTH, ROW_HEIGHT)
            .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 100, doneY, 200, ROW_HEIGHT)
                .build()
        );
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

        graphics.drawCenteredString(
            this.font,
            Component.literal("Use clean GIFs - some may not look good"),
            this.width / 2,
            noteY,
            0xFFAAAAAA
        );
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
}
