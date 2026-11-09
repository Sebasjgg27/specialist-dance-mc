package com.sebas.specialistdance.config;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;

public class SpecialistDanceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH  = FabricLoader.getInstance().getConfigDir().resolve("specialist-dance.json");
    private static final int CURRENT_VERSION = 4;

    private static final Pattern ORE_NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern ORE_PATH = Pattern.compile("[a-z0-9/.-]+");
    private transient boolean dirty;

    public static final List<String> ANIMATION_END_BEHAVIORS = List.of("stop", "hold", "loop");
    public static final List<String> RULE_TRIGGERS = List.of("mined", "pickup", "mob", "container");
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

    public List<Rule> rules = defaultRules();

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

                        // Migrate v3 (blockRules/pickupRules/disabled*) → v4 (List<Rule>)
                        if (!raw.has("rules") && (raw.has("blockRules") || raw.has("pickupRules"))) {
                            config.rules = new ArrayList<>();
                            Map<String, String> legacyBlocks = raw.has("blockRules")
                                ? GSON.fromJson(raw.getAsJsonObject("blockRules"),
                                    new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType())
                                : Map.of();
                            List<String> legacyPickup = raw.has("pickupRules")
                                ? GSON.fromJson(raw.getAsJsonArray("pickupRules"),
                                    new com.google.gson.reflect.TypeToken<List<String>>() {}.getType())
                                : List.of();
                            List<String> legacyDisabledBlocks = raw.has("disabledBlockRules")
                                ? GSON.fromJson(raw.getAsJsonArray("disabledBlockRules"),
                                    new com.google.gson.reflect.TypeToken<List<String>>() {}.getType())
                                : List.of();
                            List<String> legacyDisabledPickup = raw.has("disabledPickupRules")
                                ? GSON.fromJson(raw.getAsJsonArray("disabledPickupRules"),
                                    new com.google.gson.reflect.TypeToken<List<String>>() {}.getType())
                                : List.of();

                            for (Map.Entry<String, String> e : legacyBlocks.entrySet()) {
                                config.rules.add(new Rule("mined", e.getKey(), e.getValue(),
                                    !legacyDisabledBlocks.contains(e.getKey())));
                            }
                            for (String itemId : legacyPickup) {
                                config.rules.add(new Rule("pickup", "", itemId,
                                    !legacyDisabledPickup.contains(itemId)));
                            }
                            config.dirty = true;
                        }

                        int versionBeforeMigrate = config.configVersion;
                        config.sanitize();
                        config.migrate();

                        boolean needsSave = config.dirty
                            || config.configVersion != versionBeforeMigrate;

                        if (!config.seenDancesSeeded) {
                            seedSeenDances(config);
                            config.seenDancesSeeded = true;
                            needsSave = true;
                        }

                        if (needsSave) {
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

    private static List<Rule> defaultRules() {
    List<Rule> list = new ArrayList<>();
    list.add(new Rule("mined", "minecraft:diamond_ore",        "minecraft:diamond",       true));
    list.add(new Rule("mined", "minecraft:deepslate_diamond_ore","minecraft:diamond",      true));
    list.add(new Rule("mined", "minecraft:emerald_ore",        "minecraft:emerald",       true));
    list.add(new Rule("mined", "minecraft:deepslate_emerald_ore","minecraft:emerald",      true));
    list.add(new Rule("mined", "minecraft:gold_ore",           "minecraft:raw_gold",      true));
    list.add(new Rule("mined", "minecraft:deepslate_gold_ore", "minecraft:raw_gold",      true));
    list.add(new Rule("mined", "minecraft:nether_gold_ore",    "minecraft:gold_nugget",   true));
    list.add(new Rule("mined", "minecraft:iron_ore",           "minecraft:raw_iron",      true));
    list.add(new Rule("mined", "minecraft:deepslate_iron_ore", "minecraft:raw_iron",      true));
    list.add(new Rule("mined", "minecraft:ancient_debris",     "minecraft:ancient_debris",true));
    return list;
    }

    static @Nullable String normalizeOreId(@Nullable String raw) {
        if (raw == null) {
            return null;
        }

        String id = raw.trim().toLowerCase(Locale.ROOT);

        if (id.isEmpty()) {
            return null;
        }

        int separator = id.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : id.substring(0, separator);
        String path = separator < 0 ? id : id.substring(separator + 1);

        if (!ORE_NAMESPACE.matcher(namespace).matches() || !ORE_PATH.matcher(path).matches()) {
            return null;
        }

        return namespace + ":" + path;
    }

    private void migrate() {
        if (configVersion < CURRENT_VERSION) {
            configVersion = CURRENT_VERSION;
        }
    }

    public void resetRules() {
        rules = defaultRules();
        dirty = true;
    }

    public boolean addRule(@Nullable String rawTrigger, @Nullable String rawSource, @Nullable String rawItem) {

        if (rawTrigger == null || !RULE_TRIGGERS.contains(rawTrigger)) return false;
        String itemId = normalizeOreId(rawItem);
        if (itemId == null) return false;

        String sourceId = "pickup".equals(rawTrigger) ? "" : normalizeOreId(rawSource);
        if ("pickup".equals(rawTrigger) == false && sourceId == null) return false;

        Rule candidate = new Rule(rawTrigger, sourceId, itemId, true);
        if (containsRule(rules, candidate)) return false;

        rules.add(candidate);
        dirty = true;
        return true;
    }

    public boolean toggleRule(Rule rule) {
        rule.enabled = !rule.enabled;
        dirty = true;
        return true;
    }

    public boolean removeRule(Rule rule) {
        if (!rules.remove(rule)) return false;
        dirty = true;
        return true;
    }

    public static boolean containsRule(List<Rule> list, Rule candidate) {
        for (Rule r : list) {
            if (r.trigger.equals(candidate.trigger)
                    && r.source.equals(candidate.source)
                    && r.item.equals(candidate.item)) {
                return true;
            }
        }
        return false;
    }

    public @Nullable String minedDropFor(@Nullable String blockId) {
        if (blockId == null) return null;
        for (Rule r : rules) {
            if (r.enabled && "mined".equals(r.trigger) && blockId.equals(r.source)) {
                return r.item;
            }
        }
        return null;
    }

    public boolean matches(String trigger, @Nullable String sourceId, String itemId) {
        for (Rule r : rules) {
            if (r.enabled && r.matches(trigger, sourceId, itemId)) return true;
        }
        return false;
    }

    public boolean hasTrigger(String trigger) {
        for (Rule r : rules) {
            if (r.enabled && r.trigger.equals(trigger)) return true;
        }
        return false;
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

        if (rules == null) {
            rules = defaultRules();
            dirty = true;
        } else {
            List<Rule> normalized = new ArrayList<>();

            for (Rule r : rules) {
                if (r == null || r.trigger == null || !RULE_TRIGGERS.contains(r.trigger)) {
                    dirty = true;
                    continue;
                }

                String item = normalizeOreId(r.item);
                if (item == null) {
                    dirty = true;
                    continue;
                }

                String source = "pickup".equals(r.trigger) ? "" : normalizeOreId(r.source);
                if (source == null) {
                    dirty = true;
                    continue;
                }

                if (!item.equals(r.item) || !source.equals(r.source == null ? "" : r.source)) {
                    dirty = true;
                }

                Rule cleaned = new Rule(r.trigger, source, item, r.enabled);
                if (containsRule(normalized, cleaned)) {
                    dirty = true;
                    continue;
                }

                normalized.add(cleaned);
            }

            rules = normalized;
        }

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
    
    public static final class Rule {
        public String trigger;
        public String source;
        public String item;
        public boolean enabled = true;

        public Rule() {}

        public Rule(String trigger, String source, String item, boolean enabled) {
            this.trigger = trigger;
            this.source = source == null ? "" : source;
            this.item = item;
            this.enabled = enabled;
        }

        public boolean requiresSource() {
            return !"pickup".equals(trigger);
        }

        public boolean matches(String trigger, @Nullable String source, String item) {
            return this.trigger.equals(trigger) 
            && this.item.equals(item)
            && (source == null || this.source.equals(source));
        }

        public String label() {
            return source.isEmpty() ? item : source + " -> " + item;
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
