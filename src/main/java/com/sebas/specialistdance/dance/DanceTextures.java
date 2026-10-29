package com.sebas.specialistdance.dance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class DanceTextures {

    public record Frame(Identifier texture, int width, int height) {}

    public record Placement(int x, int y, int width, int height, int stageX, int stageY, int stageWidth, int stageHeight) {
        public boolean contains(int mouseX, int mouseY) {
            return mouseX >= stageX && mouseX < stageX + stageWidth && mouseY >= stageY && mouseY < stageY + stageHeight;
        }
    }

    public static Placement place(Frame frame, int stageWidthPercent, int stageHeightPercent, int positionX, int positionY, int guiWidth, int guiHeight) {
        if (frame.width() <= 0 || frame.height() <= 0 || guiWidth <= 0 || guiHeight <= 0) {
            return new Placement(0, 0, 0, 0, 0, 0, 0, 0);
        }

        int sw = Math.max(8, Math.min(guiWidth, (int) Math.round(guiWidth * Math.clamp(stageWidthPercent, 1, 100) / 100.0)));
        int sh = Math.max(8, Math.min(guiHeight, (int) Math.round(guiHeight * Math.clamp(stageHeightPercent, 1, 100) / 100.0)));

        double scale = Math.min((double) sw / frame.width(), (double) sh / frame.height());

        int width = Math.max(1, (int) Math.round(frame.width() * scale));
        int height = Math.max(1, (int) Math.round(frame.height() * scale));

        int stageX = positionX >= 0 ? Math.min(positionX, guiWidth - sw) : (guiWidth - sw) / 2;
        int stageY = positionY >= 0 ? Math.min(positionY, guiHeight - sh) : (guiHeight - sh) / 2;

        int x = stageX + (sw - width) / 2;
        int y = stageY + (sh - height) / 2;

        return new Placement(x, y, width, height, stageX, stageY, sw, sh);
    }

    private static final String NAMESPACE = "specialist-dance";

    private final Map<String, Frame> frames = new HashMap<>();
    private final Set<Identifier> registered = new HashSet<>();

    private String loadedPack;

    public Frame get(Minecraft client, DanceRegistry.Dance dance, int index) {
        if (!Objects.equals(loadedPack, dance.name())) {
            release(client);
            loadedPack = dance.name();
        }

        String key = dance.name() + '/' + index;
        Frame cached = frames.get(key);

        if (cached != null) {
            return cached;
        }

        Frame frame = load(client, dance, index);

        if (frame != null) {
            frames.put(key, frame);
        }

        return frame;
    }

    public void release(Minecraft client) {
        for (Identifier location : registered) {
            client.getTextureManager().release(location);
        }

        registered.clear();
        frames.clear();
        loadedPack = null;
    }

    private Frame load(Minecraft client, DanceRegistry.Dance dance, int index) {
        String fileName = "frame_%03d.png".formatted(index);

        return dance.isBundled()
            ? loadBundled(client, dance, fileName)
            : loadUserPack(client, dance, fileName);
    }

    private Frame loadBundled(Minecraft client, DanceRegistry.Dance dance, String fileName) {
        Identifier location = Identifier.fromNamespaceAndPath(
            NAMESPACE,
            "textures/gui/frames/" + fileName
        );

        try (NativeImage image = NativeImage.read(client.getResourceManager().open(location))) {
            return new Frame(location, image.getWidth(), image.getHeight());
        } catch (IOException e) {
            return null;
        }
    }

    private Frame loadUserPack(Minecraft client, DanceRegistry.Dance dance, String fileName) {
        // Minecraft paths reject anything outside [a-z0-9._-]; the folder name on disk may use any case.
        String safeName = dance.name().toLowerCase().replaceAll("[^a-z0-9._-]", "_");

        Identifier location = Identifier.fromNamespaceAndPath(
            NAMESPACE,
            "dances/%s/%s".formatted(safeName, fileName)
        );

        NativeImage image;
        int width;
        int height;

        try (InputStream in = Files.newInputStream(dance.folder().resolve(fileName))) {
            image = NativeImage.read(in);
            width = image.getWidth();
            height = image.getHeight();

            if (width <= 0 || height <= 0) {
                image.close();
                log("frame has no pixels: " + dance.name() + "/" + fileName);
                return null;
            }

            client.getTextureManager().register(
                location,
                new DynamicTexture(() -> location.toString(), image)
            );
        } catch (IOException | RuntimeException e) {
            log("could not load frame" + fileName + " from " + dance.name() + ": " + e.getMessage());
            return null;
        }

        registered.add(location);

        return new Frame(location, width, height);
    }

    private void log(String message) {
        System.out.println("Specialist Dance: " + message);
    }
}