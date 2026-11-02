package com.sebas.specialistdance.config.menu;

import org.jspecify.annotations.NonNull;

import com.sebas.specialistdance.dance.DanceRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

final class DanceNameListEntry extends ObjectSelectionList.Entry<DanceNameListEntry> {

    private final @NonNull String name;
    private final @NonNull Font font;
    private final @NonNull DanceRegistry danceRegistry;
    private final int fallbackFrameDurationMs;
    private final boolean showDanceMetadata;

    DanceNameListEntry(
            @NonNull String name,
            @NonNull Font font,
            @NonNull DanceRegistry danceRegistry,
            int fallbackFrameDurationMs,
            boolean showDanceMetadata) {
        this.name = name;
        this.font = font;
        this.danceRegistry = danceRegistry;
        this.fallbackFrameDurationMs = fallbackFrameDurationMs;
        this.showDanceMetadata = showDanceMetadata;
    }

    String name() {
        return name;
    }

    @Override
    public @NonNull Component getNarration() {
        return Component.literal(name);
    }

    @Override
    public void renderContent(
            @NonNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            boolean isMouseOver,
            float partialTick) {
        int y = getContentYMiddle() - font.lineHeight / 2;
        int nameColor = isMouseOver ? 0xFFFFFFFF : 0xFFB0B0B0;

        if (showDanceMetadata) {
            DanceRegistry.Dance dance = danceRegistry.find(name);
            String meta = dance.frameCount() + "f  " + dance.frameDurationMs(fallbackFrameDurationMs) + "ms";

            graphics.drawString(font, name, getContentX(), y, nameColor);
            graphics.drawString(font, meta, getContentRight() - font.width(meta), y, 0xFF909090);
        } else {
            graphics.drawString(font, name, getContentX(), y, nameColor);
        }
    }
}
