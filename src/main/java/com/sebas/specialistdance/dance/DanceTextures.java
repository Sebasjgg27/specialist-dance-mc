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
        Identifier location = Identifier.fromNamespaceAndPath(
            NAMESPACE,
            "dances/%s/%s".formatted(dance.name(), fileName)
        );

        NativeImage image;

        try (InputStream in = Files.newInputStream(dance.folder().resolve(fileName))) {
            image = NativeImage.read(in);
        } catch (IOException e) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        client.getTextureManager().register(
            location,
            new DynamicTexture(() -> location.toString(), image)
        );
        registered.add(location);

        return new Frame(location, width, height);
    }
}
