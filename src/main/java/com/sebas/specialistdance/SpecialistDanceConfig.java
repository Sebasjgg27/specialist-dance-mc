package com.sebas.specialistdance;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public class SpecialistDanceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH  = FabricLoader.getInstance().getConfigDir().resolve("specialist-dance.json");

    public boolean enabled = true;
    public boolean debug = false;

    public int oreTimeoutMs = 10_000;
    public int linkTimeoutMs = 30_000;
    public double searchRadiusSquared = 16.0;
    public int dropResolveTimeoutMs = 2_000;

    public int frameDurationMs = 50;
    public int drawSize = 160;
    public int stageWidth = 200;
    public int stageHeight = 150;
    public int stageWidthPercent = 35;
    public int stageHeightPercent = 33;
    public int positionX = -1;
    public int positionY = -1;
    public int cooldownMs = 0;

    public boolean musicEnabled = true;
    public float musicVolume = 1.0f;
    public String musicTrack = "default";

    public String animationEndBehavior = "stop";
    public String activeDance = "specialist";

    public static SpecialistDanceConfig load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader reader = Files.newBufferedReader(PATH)) {
                    SpecialistDanceConfig config = GSON.fromJson(reader, SpecialistDanceConfig.class);

                    if (config != null) {
                        config.sanitize();
                        return config;
                    }
                } catch (IOException e) {
                    System.err.println("Specialist Dance: Could not read config, using defaults");
                    e.printStackTrace();
                }
            }
        } catch (RuntimeException e) {
            Path broken = PATH.resolveSibling("specialist-dance.json.broken");

            System.err.println("Specialist Dance: config is malformed, using defaults");
            e.printStackTrace();

            try {
                Files.move(PATH, broken, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.err.println("Specialist Dance: saved the bad config to " + broken);
            } catch (IOException ignored) {
                // Continue with defaults if the malformed config cannot be moved.
            }
        }

        SpecialistDanceConfig config = new SpecialistDanceConfig();
        config.save();
        return config;
    }

    private void sanitize() {
        frameDurationMs = Math.max(10, frameDurationMs);
        drawSize = Math.max(16, drawSize);
        stageWidth = Math.max(16, stageWidth);
        stageHeight = Math.max(16, stageHeight);
        stageWidthPercent = Math.clamp(stageWidthPercent, 1, 100);
        stageHeightPercent = Math.clamp(stageHeightPercent, 1, 100);
        positionX = positionX < 0 ? -1 : positionX;
        positionY = positionY < 0 ? -1 : positionY;
        cooldownMs = Math.max(0, cooldownMs);
        oreTimeoutMs = Math.max(100, oreTimeoutMs);
        linkTimeoutMs = Math.max(100, linkTimeoutMs);
        dropResolveTimeoutMs = Math.max(100, dropResolveTimeoutMs);
        searchRadiusSquared = Math.max(0.0, searchRadiusSquared);
        musicVolume = Math.clamp(musicVolume, 0.0f, 1.0f);

        if (musicTrack == null) {
            musicTrack = "default";
        }

        if (activeDance == null || activeDance.isBlank()) {
            activeDance = "specialist";
        }

        if (animationEndBehavior == null) {
            animationEndBehavior = "stop";
        }

        if ("loop-until-music-ends".equals(animationEndBehavior)) {
            animationEndBehavior = "loop";
        }
    }
    
    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, writer);
            }
        }

        catch (IOException e) {
            System.err.println(
                "Specialist Dance: Could not write config"
            );
            e.printStackTrace();
        }

    }

}
