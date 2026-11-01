package com.sebas.specialistdance.dance;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import com.google.gson.Gson;

public final class DancePackMeta {

    private static final Gson GSON = new Gson();
    private static final Pattern TRACK_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern SOURCE_HASH = Pattern.compile("[0-9a-f]{64}");
    private static final int MIN_FRAME_DURATION_MS = 10;
    private static final int MAX_FRAME_DURATION_MS = 10_000;
    private static final long MAX_BYTES = 1_000_000L;

    private int frameDurationMs = -1;
    private String track;
    private String source;

    private DancePackMeta() {}

    public static Optional<DancePackMeta> read(Path packDir, boolean debug) {
        Path file = packDir.resolve("pack.json");

        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            if (Files.size(file) > MAX_BYTES) {
                log(debug, "pack.json for " + packDir.getFileName() + " is too large");
                return Optional.empty();
            }
        } catch (IOException e) {
            log(debug, "could not read pack.json for " + packDir.getFileName() + ": " + e.getMessage());
            return Optional.empty();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            DancePackMeta meta = GSON.fromJson(reader, DancePackMeta.class);

            if (meta == null) {
                log(debug, "pack.json for " + packDir.getFileName() + " is empty");
                return Optional.empty();
            }

            meta.sanitize();
            return Optional.of(meta);
        } catch (IOException | RuntimeException e) {
            log(debug, "could not read pack.json for " + packDir.getFileName() + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public int frameDurationMs() {
        return frameDurationMs;
    }

    public String track() {
        return track;
    }

    public String source() {
        return source;
    }

    private void sanitize() {
        if (frameDurationMs <= 0) {
            frameDurationMs = -1;
        } else {
            frameDurationMs = Math.clamp(frameDurationMs, MIN_FRAME_DURATION_MS, MAX_FRAME_DURATION_MS);
        }

        if (track != null) {
            track = track.trim();

            if (track.isEmpty() || track.contains("..") || !TRACK_NAME.matcher(track).matches()) {
                track = null;
            }
        }

        if (source != null && !SOURCE_HASH.matcher(source).matches()) {
            source = null;
        }
    }

    private static void log(boolean debug, String message) {
        if (debug) {
            System.out.println("Specialist Dance: " + message);
        }
    }
}
