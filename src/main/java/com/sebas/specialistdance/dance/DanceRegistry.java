package com.sebas.specialistdance.dance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;

import com.sebas.specialistdance.SpecialistDanceConfig;

public class DanceRegistry {

    public record Dance(String name, Path folder, int frameCount) {
        public boolean isBundled() {
            return folder == null;
        }
    }

    private static final Dance BUNDLED = new Dance("specialist", null, 147);

    private final Map<String, Dance> userDances = new TreeMap<>();
    private final SpecialistDanceConfig config;

    public DanceRegistry(SpecialistDanceConfig config) {
        this.config = config;
    }

    public void scan() {
        userDances.clear();

        Path root = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("specialist-dance")
            .resolve("dances");

        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            return;
        }
        
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).sorted().forEach(dir -> {
                String name = dir.getFileName().toString();
                int frameCount = countFrames(dir);

                if (frameCount < 0) {
                    log("ignoring invalid dance pack " + name);
                    return;
                }

                Dance dance = new Dance(name, dir, frameCount);

                if (userDances.putIfAbsent(name, dance) != null) {
                    log("duplicate dance name ignored: " + name);
                }
            });
        } catch (IOException e) {
            log("could not scan dances dir " + root);
        }
    }
    
    private int countFrames(Path dir) {
        TreeSet<Integer> indices = new TreeSet<>();

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
            .map(p -> p.getFileName().toString())
            .forEach(fileName -> {
                if (!fileName.startsWith("frame_") || !fileName.endsWith(".png")) {
                    return;
                }

                String digits = fileName.substring(6, fileName.lastIndexOf('.'));

                try {
                    indices.add(Integer.parseInt(digits));
                } catch (NumberFormatException ignored) {}
            });
        } catch (IOException e) {
            return -1;
        }

        if (indices.isEmpty() || indices.last() != indices.size() - 1) {
            return -1;
        }

        return indices.size();
    }

    public List<String> getAvailableDances() {
        List<String> names = new ArrayList<>();
        names.add(BUNDLED.name());
        names.addAll(userDances.keySet());
        return names;
    }

    public Dance find(String name) {
        if (BUNDLED.name().equals(name)) {
            return BUNDLED;
        }

        Dance dance = userDances.get(name);
        return dance == null ? BUNDLED : dance;
    }

    private void log(String message) {
        if (config.debug) {
            System.out.println("Specialist Dance: " + message);
        }
    }
}