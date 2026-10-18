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
    public int positionX = -1;
    public int positionY = -1;
    public int cooldownMs = 0;

    public static SpecialistDanceConfig load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                SpecialistDanceConfig config = GSON.fromJson(reader, SpecialistDanceConfig.class);

                if (config != null) {
                    config.save();
                    return config;
                }
            } 

            catch (IOException e) {
                System.err.println(
                    "Specialist Dance: Could not read config, using defaults"
                );
                e.printStackTrace();
            }
        }

        SpecialistDanceConfig config = new SpecialistDanceConfig();
        config.save();
        return config;
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
