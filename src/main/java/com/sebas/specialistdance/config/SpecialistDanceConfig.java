package com.sebas.specialistdance.config;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

public class SpecialistDanceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH  = FabricLoader.getInstance().getConfigDir().resolve("specialist-dance.json");
    private static final int CURRENT_VERSION = 1;

    public static final List<String> ANIMATION_END_BEHAVIORS = List.of("stop", "hold", "loop");
    public static final double MIN_ANIMATION_SPEED = 0.1;
    public static final double MAX_ANIMATION_SPEED = 10.0;

    public int configVersion = CURRENT_VERSION;

    public boolean enabled = true;
    public boolean debug = false;

    public int oreTimeoutMs = 10_000;
    public double animationSpeed = 1.0;
    public int linkTimeoutMs = 30_000;
    public double searchRadiusSquared = 16.0;
    public int dropResolveTimeoutMs = 2_000;

    public int frameDurationMs = 100;
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
    public List<String> seenDances = new ArrayList<>();
    public boolean seenDancesSeeded;

    public static SpecialistDanceConfig load() {
        try {
            if (Files.exists(PATH)) {
                try {
                    String json = Files.readString(PATH);
                    JsonObject raw = JsonParser.parseString(json).getAsJsonObject();
                    SpecialistDanceConfig config = GSON.fromJson(raw, SpecialistDanceConfig.class);

                    if (config != null) {
                        // Gson runs the field initialisers before binding, so a file written
                        // before versioning exists still reads back as CURRENT_VERSION.
                        if (!raw.has("configVersion")) {
                            config.configVersion = 0;
                        }

                        config.sanitize();
                        config.migrate();

                        if (!config.seenDancesSeeded) {
                            seedSeenDances(config);
                            config.seenDancesSeeded = true;
                            config.save();
                        }

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
        config.seenDancesSeeded = true;
        config.save();
        return config;
    }

    private static void seedSeenDances(SpecialistDanceConfig config) {
        Path dancesDir = PATH.getParent()
            .resolve("specialist-dance")
            .resolve("dances");

        if (!Files.isDirectory(dancesDir)) {
            return;
        }

        try (Stream<Path> dirs = Files.list(dancesDir)) {
            dirs.filter(Files::isDirectory)
                .map(dir -> dir.getFileName().toString())
                .filter(name -> !name.startsWith(".") && !"specialist".equals(name))
                .forEach(name -> {
                    if (!config.seenDances.contains(name)) {
                        config.seenDances.add(name);
                    }
                });
        } catch (IOException ignored) {}
    }

    private void migrate() {
        if (configVersion < CURRENT_VERSION) {
            configVersion = CURRENT_VERSION;
        }
    }

    private void sanitize() {
        frameDurationMs = Math.clamp(frameDurationMs, 10, 10_000);
        drawSize = Math.clamp(drawSize, 16, 2000);
        stageWidth = Math.clamp(stageWidth, 16, 4000);
        stageHeight = Math.clamp(stageHeight, 16, 4000);
        stageWidthPercent = Math.clamp(stageWidthPercent, 1, 100);
        stageHeightPercent = Math.clamp(stageHeightPercent, 1, 100);
        positionX = positionX < 0 ? -1 : positionX;
        positionY = positionY < 0 ? -1 : positionY;
        cooldownMs = Math.clamp(cooldownMs, 0, 60000);
        oreTimeoutMs = Math.clamp(oreTimeoutMs, 100, 60000);
        linkTimeoutMs = Math.clamp(linkTimeoutMs, 100, 60000);
        dropResolveTimeoutMs = Math.clamp(dropResolveTimeoutMs, 100, 60000);
        searchRadiusSquared = Math.clamp(searchRadiusSquared, 0.0, 100.0);
        musicVolume = Math.clamp(musicVolume, 0.0f, 1.0f);
        animationSpeed = Math.clamp(animationSpeed, MIN_ANIMATION_SPEED, MAX_ANIMATION_SPEED);

        if (musicTrack == null) {
            musicTrack = "default";
        }

        if (activeDance == null || activeDance.isBlank()) {
            activeDance = "specialist";
        }

        if (seenDances == null) {
            seenDances = new ArrayList<>();
        }

        seenDances.removeIf(name -> name == null || name.isBlank());

        if (animationEndBehavior == null) {
            animationEndBehavior = "stop";
        }

        if ("loop-until-music-ends".equals(animationEndBehavior)) {
            animationEndBehavior = "loop";
        }

        if (!ANIMATION_END_BEHAVIORS.contains(animationEndBehavior)) {
            animationEndBehavior = "stop";
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
