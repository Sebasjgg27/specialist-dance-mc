package com.sebas.specialistdance.config.menu;

import com.sebas.specialistdance.config.SpecialistDanceConfig;
import com.sebas.specialistdance.dance.DanceRegistry;
import com.sebas.specialistdance.dance.DanceTextures;
import com.sebas.specialistdance.dance.DanceTextures.Placement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

import org.lwjgl.glfw.GLFW;
import org.jspecify.annotations.NonNull;

public class DanceLayoutScreen extends Screen {

    private static final int BAR_HEIGHT = 30;

    private final SpecialistDanceConfig config;
    private final DanceRegistry danceRegistry;
    private final DanceTextures danceTextures;
    private final Screen parent;
    private boolean dragging;

    public DanceLayoutScreen(SpecialistDanceConfig config, DanceRegistry danceRegistry, DanceTextures danceTextures, Screen parent) {
        super(Component.literal("Position Dance"));
        this.config = config;
        this.danceRegistry = danceRegistry;
        this.danceTextures = danceTextures;
        this.parent = parent;
    }

    @Override 
    protected void init() {
        int buttonWidth = 60;
        int buttonHeight = 20;
        int y = this.height - BAR_HEIGHT + (BAR_HEIGHT - buttonHeight) / 2;
        int startX = 8;

        addRenderableWidget(
            Button.builder(Component.literal("Center"), button -> {
                config.positionX = -1;
                config.positionY = -1;
            })
                .bounds(startX, y, buttonWidth, buttonHeight)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Fit"), button -> fitStage())
                .bounds(startX + buttonWidth + 4, y, buttonWidth, buttonHeight)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("reset"), button -> {
                config.positionX = -1;
                config.positionY = -1;
                config.stageWidthPercent = 35;
                config.stageHeightPercent = 33;
            })
                .bounds(startX + (buttonWidth + 4) * 2, y, buttonWidth, buttonHeight)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width - buttonWidth - 8, y, buttonWidth, buttonHeight)
                .build()
        );
    }

    @Override 
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && currentPlacement().contains((int) event.x(), (int) event.y())) {
            dragging = true;
            return true;
        }

        return false;
    }

    @Override 
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override 
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) {
            return super.mouseDragged(event, dragX, dragY);
        }

        Placement placement = currentPlacement();

        if (config.positionX < 0) {
            config.positionX = placement.stageX();
        }

        if (config.positionY < 0) {
            config.positionY = placement.stageY();
        }

        config.positionX = Math.max(0, config.positionX + (int) dragX);
        config.positionY = Math.max(0, config.positionY + (int) dragY);

        return true;
    }

    @Override 
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = scrollY > 0 ? 2 : -2;

        config.stageWidthPercent = Math.clamp(config.stageWidthPercent + step, 5, 100);
        config.stageHeightPercent = Math.clamp(config.stageHeightPercent + step, 5, 100);

        return true;
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0x60000000);
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DanceTextures.Frame frame = currentFrame();
        DanceTextures.Placement placement = currentPlacement();

        if (frame != null) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                frame.texture(),
                placement.x(),
                placement.y(),
                0.0f,
                0.0f,
                placement.width(),
                placement.height(),
                frame.width(),
                frame.height(),
                frame.width(),
                frame.height()
            );

            int sc = 0xFF00FF00;
            graphics.fill(placement.stageX() - 1, placement.stageY() - 1, placement.stageX() + placement.stageWidth() + 1, placement.stageY(), sc);
            graphics.fill(placement.stageX() - 1, placement.stageY() + placement.stageHeight(), placement.stageX() + placement.stageWidth() + 1, placement.stageY() + placement.stageHeight() + 1, sc);
            graphics.fill(placement.stageX() - 1, placement.stageY(), placement.stageX(), placement.stageY() + placement.stageHeight(), sc);
            graphics.fill(placement.stageX() + placement.stageWidth(), placement.stageY(), placement.stageX() + placement.stageWidth() + 1, placement.stageY() + placement.stageHeight(), sc);
        } else {
            DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);

            if (dance.frameCount() <= 0) {
                graphics.drawCenteredString(this.font, "No frames in \"" + dance.name() + "\"", this.width / 2, this.height / 2, 0xFFFF8080);
            } else {
                graphics.drawCenteredString(this.font, "Frames not loaded yet \"" + dance.name() + "\"", this.width / 2, this.height / 2, 0xFFFF8080);
            }
        }

        graphics.fill(0, this.height - BAR_HEIGHT, this.width, this.height, 0x80000000);

        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, "Drag to move · wheel to resize · previews the middle frame", this.width / 2, 25, 0xFFAAAAAA);

        String pos = "Stage at " + (config.positionX < 0 ? "auto-center" : placement.stageX() + ", " + placement.stageY());
        String size = "Stage " + config.stageWidthPercent + "% x " + config.stageHeightPercent
            + " (" + placement.stageWidth() + " x " + placement.stageHeight() + " px)";

        graphics.drawString(this.font, pos, 8, 35, 0xFFFFFF55);
        graphics.drawString(this.font, size, 8, 45, 0xFFFFFF55);
    }

    @Override
    public void onClose() {
        config.save();

        if (parent instanceof SpecialistDanceConfigScreen configScreen) {
            configScreen.refreshWidgets();
        }

        this.minecraft.setScreen(parent);
    }

    private void fitStage() {
        DanceTextures.Frame frame = currentFrame();

        if (frame == null || this.width <= 0 || this.height <= 0) {
            return;
        }

        double scale = Math.min(
            this.width * 0.8 / frame.width(),
            this.height * 0.8 / frame.height()
        );

        int drawWidth = Math.max(1, (int) Math.round(frame.width() * scale));
        int drawHeight = Math.max(1, (int) Math.round(frame.height() * scale));

        config.stageWidthPercent = Math.clamp(
            (int) Math.round(drawWidth * 100.0 / this.width), 5, 100
        );
        config.stageHeightPercent = Math.clamp(
            (int) Math.round(drawHeight * 100.0 / this.height), 5, 100
        );
    }

    private DanceTextures.Frame currentFrame() {
        DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
        int index = dance.frameCount() > 1 ? dance.frameCount() / 2 : 0;
        return danceTextures.get(Minecraft.getInstance(), dance, index);
    }

    private DanceTextures.Placement currentPlacement() {
        DanceTextures.Frame frame = currentFrame();

        if (frame == null) {
            return new DanceTextures.Placement(0, 0, 0, 0, 0, 0, 0, 0);
        }

        return DanceTextures.place(
            frame,
            config.stageWidthPercent,
            config.stageHeightPercent,
            config.positionX,
            config.positionY,
            this.width,
            this.height
        );
    }
}
