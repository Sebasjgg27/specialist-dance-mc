package com.sebas.specialistdance.dance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.sebas.specialistdance.SpecialistDanceConfig;

public class DanceRegistry {

    private static final Set<String> SUPPORTED_AUDIO_EXTENSIONS = Set.of(
        "ogg", "wav", "aiff", "aif", "au", "mp3", "flac"
    );

    private static final Gson PACK_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long MAX_PACK_JSON_BYTES = 1_000_000L;

    public record Dance(String name, Path folder, int frameCount, DancePackMeta meta) {
        public boolean isBundled() {
            return folder == null;
        }

        public int frameDurationMs(int fallback) {
            return meta != null && meta.frameDurationMs() > 0
                ? meta.frameDurationMs()
                : fallback;
        }

        public String track() {
            return meta == null ? null : meta.track();
        }
    }

    private static final Dance BUNDLED = new Dance("specialist", null, 147, null);

    private final Map<String, Dance> userDances = new TreeMap<>();
    private final SpecialistDanceConfig config;
    private boolean firstScan = true;

    public DanceRegistry(SpecialistDanceConfig config) {
        this.config = config;
    }

    public List<String> scan() {
        Set<String> before = new HashSet<>(userDances.keySet());
        userDances.clear();

        Path root = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("specialist-dance")
            .resolve("dances");

        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            return new ArrayList<>();
        }

        importGifs(root);

        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                .filter(dir -> !dir.getFileName().toString().startsWith("."))
                .sorted()
                .forEach(dir -> {
                    String name = dir.getFileName().toString();

                    if (BUNDLED.name().equals(name)) {
                        log("dance pack " + name + " is reserved for the bundled dance");
                        return;
                    }

                    int frameCount = countFrames(dir, name);

                    if (frameCount < 0) {
                        log("ignoring invalid dance pack " + name);
                        return;
                    }

                    DancePackMeta meta = DancePackMeta.read(dir, config.debug).orElse(null);
                    userDances.put(name, new Dance(name, dir, frameCount, meta));
                });
        } catch (IOException e) {
            log("could not scan dances dir " + root);
        }

        if (config.seenDances != null
                && config.seenDances.removeIf(name -> !userDances.containsKey(name))) {
            config.save();
        }

        List<String> added = new ArrayList<>();

        for (String name : userDances.keySet()) {
            if (!before.contains(name)) {
                added.add(name);
            }
        }

        if (!added.isEmpty()) {
            log((firstScan ? "loaded dance packs: " : "found new dance packs: ") + added);
        }

        firstScan = false;
        return added;
    }

    public Set<String> knownNames() {
        return new TreeSet<>(userDances.keySet());
    }

    public boolean needsLayout(String name) {
        return userDances.containsKey(name)
            && (config.seenDances == null || !config.seenDances.contains(name));
    }

    public void markLayoutSeen(String name) {
        if (config.seenDances == null) {
            config.seenDances = new ArrayList<>();
        }

        if (config.seenDances.add(name)) {
            config.save();
        }
    }

    private void importGifs(Path root) {
        discardInterruptedImports(root);

        Map<String, String> imported = importedSources(root);

        try (Stream<Path> files = Files.list(root)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString()
                    .toLowerCase(Locale.ROOT).endsWith(".gif"))
                .sorted()
                .forEach(gif -> importGif(root, gif, imported));
        } catch (IOException e) {
            log("could not scan GIFs in " + root + ": " + e.getMessage());
        }
    }

    private void discardInterruptedImports(Path root) {
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith(".import-") || name.endsWith(".replaced");
                })
                .forEach(this::deleteRecursively);
        } catch (IOException e) {
            log("could not clean interrupted imports in " + root);
        }
    }

    private Map<String, String> importedSources(Path root) {
        Map<String, String> sources = new HashMap<>();

        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                .filter(dir -> !dir.getFileName().toString().startsWith("."))
                .forEach(dir -> DancePackMeta.read(dir, config.debug)
                    .map(DancePackMeta::source)
                    .ifPresent(source -> sources.putIfAbsent(source, dir.getFileName().toString())));
        } catch (IOException e) {
            log("could not index dance packs by source GIF");
        }

        return sources;
    }

    private boolean isImportedPack(Path dir) {
        return DancePackMeta.read(dir, config.debug)
            .map(DancePackMeta::source)
            .isPresent();
    }

    private void importGif(Path root, Path gif, Map<String, String> imported) {
        String fileName = gif.getFileName().toString();
        String stem = fileName.substring(0, fileName.length() - 4).trim();

        if (stem.isEmpty() || stem.startsWith(".")) {
            log("ignoring GIF with unusable name: " + fileName);
            return;
        }

        if (BUNDLED.name().equals(stem)) {
            log("GIF " + fileName + " ignored: " + stem + " is reserved for the bundled dance");
            return;
        }

        Path target = root.resolve(stem);
        String source = sha256(gif);

        if (source == null) {
            return;
        }

        String twin = imported.get(source);

        if (twin != null) {
            log("GIF " + fileName + " is the same animation as dance pack " + twin + ", ignoring");
            discardLooseCopies(root, gif, stem);
            return;
        }

        if (Files.exists(target) && !isImportedPack(target)) {
            log("GIF " + fileName + " ignored: dance pack " + stem + " has no import record");
            return;
        }

        Path temp = null;
        Path replaced = null;

        try {
            temp = Files.createTempDirectory(root, ".import-");
            int frames = GifImporter.convert(gif, temp, config.debug);

            if (frames <= 0) {
                deleteRecursively(temp);
                return;
            }

            copySidecars(root, stem, temp);
            stampSource(temp, source);

            if (Files.exists(target)) {
                replaced = root.resolve("." + stem + ".replaced");
                Files.move(target, replaced, StandardCopyOption.REPLACE_EXISTING);
            }

            Files.move(temp, target);

            if (replaced != null) {
                deleteRecursively(replaced);
            }

            imported.put(source, stem);

            log((replaced == null ? "imported " : "reimported ")
                + fileName + " as dance pack " + stem + " (" + frames + " frames)");
        } catch (IOException e) {
            log("could not import " + fileName + ": " + e.getMessage());

            if (replaced != null && !Files.exists(target)) {
                try {
                    Files.move(replaced, target);
                } catch (IOException restore) {
                    log("could not restore dance pack " + stem + ": " + restore.getMessage());
                }
            }

            if (temp != null) {
                deleteRecursively(temp);
            }
        }
    }

    private void copySidecars(Path root, String stem, Path target) throws IOException {
        for (String extension : SUPPORTED_AUDIO_EXTENSIONS) {
            Path audio = root.resolve(stem + "." + extension);

            if (Files.isRegularFile(audio)) {
                Files.copy(
                    audio,
                    target.resolve(audio.getFileName().toString()),
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        }

        Path json = root.resolve(stem + ".json");

        if (Files.isRegularFile(json)) {
            JsonObject sidecar = readJson(json);

            if (sidecar != null) {
                JsonObject pack = readPackJson(target);

                for (Map.Entry<String, JsonElement> member : sidecar.entrySet()) {
                    pack.add(member.getKey(), member.getValue());
                }

                writePackJson(target, pack);
            }
        }
    }

    private void stampSource(Path packDir, String source) {
        JsonObject json = readPackJson(packDir);
        json.addProperty("source", source);
        writePackJson(packDir, json);
    }

    private JsonObject readPackJson(Path packDir) {
        Path file = packDir.resolve("pack.json");

        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }

        JsonObject json = readJson(file);
        return json == null ? new JsonObject() : json;
    }

    private JsonObject readJson(Path file) {
        try {
            if (Files.size(file) > MAX_PACK_JSON_BYTES) {
                log("ignoring oversized " + file.getFileName());
                return null;
            }

            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            log("could not read " + file.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    private void writePackJson(Path packDir, JsonObject json) {
        try {
            Files.writeString(packDir.resolve("pack.json"), PACK_GSON.toJson(json));
        } catch (IOException e) {
            log("could not write pack.json: " + e.getMessage());
        }
    }

    private void discardLooseCopies(Path root, Path gif, String stem) {
        deleteQuietly(gif);

        try (Stream<Path> files = Files.list(root)) {
            files.filter(Files::isRegularFile)
                .filter(path -> stem.equalsIgnoreCase(
                    path.getFileName().toString().replaceFirst("\\.[^.]+$", "")))
                .filter(path -> {
                    String extension = extensionOf(path);
                    return SUPPORTED_AUDIO_EXTENSIONS.contains(extension) || "json".equals(extension);
                })
                .forEach(this::deleteQuietly);
        } catch (IOException e) {
            log("could not find leftover files for " + stem);
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log("could not delete " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');

        return dot < 0
            ? ""
            : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(Path file) {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            log("no SHA-256 in this runtime, cannot detect duplicate GIFs");
            return null;
        }

        byte[] buffer = new byte[64 * 1024];

        try (InputStream in = Files.newInputStream(file)) {
            for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            log("could not read " + file.getFileName() + ": " + e.getMessage());
            return null;
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private int countFrames(Path dir, String name) {
        TreeSet<Integer> indices = new TreeSet<>();

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
            .map(p -> p.getFileName().toString())
            .forEach(fileName -> {
                if (!fileName.startsWith("frame_") || !fileName.endsWith(".png")) {
                    return;
                }

                String digits = fileName.substring(6, fileName.length() - 4);

                if (digits.length() < 3) {
                    return;
                }

                try {
                    indices.add(Integer.parseInt(digits));
                } catch (NumberFormatException ignored) {}
            });
        } catch (IOException e) {
            log("dance pack " + name + " could not be read: " + e.getMessage());
            return -1;
        }

        if (indices.isEmpty()) {
            log("dance pack " + name + " has no frame_NNN.png files");
            return -1;
        }

        if (indices.last() != indices.size() - 1) {
            log("dance pack " + name + " has " + indices.size() + " numbered frames from frame_"
                + String.format("%03d", indices.first()) + " to frame_"
                + String.format("%03d", indices.last()) + ", expected a gapless run starting at frame_000");
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
