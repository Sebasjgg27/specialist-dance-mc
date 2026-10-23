package com.sebas.specialistdance.config.menu;

import java.util.List;

import com.sebas.specialistdance.SpecialistDanceConfig;
import com.sebas.specialistdance.audio.MusicManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SpecialistDanceConfigScreen extends Screen {

    private static final int BOX_WIDTH = 300;

    private final SpecialistDanceConfig config;
    private final MusicManager music;
    private final Screen parent;

    public SpecialistDanceConfigScreen(SpecialistDanceConfig config, MusicManager music, Screen parent) {
        super(Component.literal("Specialist Dance"));
        this.config = config;
        this.music = music;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BOX_WIDTH / 2;
        int y = this.height / 4;

        addRenderableWidget(
            Checkbox.builder(Component.literal("Music Enabled"), this.font)
                .pos(x, y)
                .selected(config.musicEnabled)
                .onValueChange((checkbox, selected) -> {
                    config.musicEnabled = selected;
                    if (!selected) {
                        music.stop();
                    }
                })
                .build()
        );

        addRenderableWidget(new VolumeSlider(x, y + 30, BOX_WIDTH, 20));

        List<String> tracks = music.getAvailableTracks();

        if (!tracks.contains(config.musicTrack)) {
            config.musicTrack = tracks.get(0);
        }

        addRenderableWidget(
            CycleButton.<String>builder(name -> Component.literal("Track: " + name), config.musicTrack)
                .withValues(tracks)
                .create(
                    x, y + 60, BOX_WIDTH, 20,
                    Component.literal("Music track"),
                    (button, value) -> config.musicTrack = value
                )
        );

        int half = (BOX_WIDTH - 10) / 2;

        addRenderableWidget(
            Button.builder(Component.literal("Play preview"), button -> music.play())
                .bounds(x, y + 95, half, 20)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Stop"), button -> music.stop())
                .bounds(x + half + 10, y + 95, half, 20)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 40, 200, 20)
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
            this.height / 4 - 24,
            0xFFFFFF
        );
    }

    @Override
    public void onClose() {
        music.stop();
        config.save();
        this.minecraft.setScreen(parent);
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
