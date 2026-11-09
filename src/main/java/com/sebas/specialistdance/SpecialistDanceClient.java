package com.sebas.specialistdance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.sebas.specialistdance.audio.MusicManager;
import com.sebas.specialistdance.config.SpecialistDanceConfig;
import com.sebas.specialistdance.config.menu.DanceLayoutScreen;
import com.sebas.specialistdance.config.menu.SpecialistDanceConfigScreen;
import com.sebas.specialistdance.dance.DanceRegistry;
import com.sebas.specialistdance.dance.DanceTextures;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import org.lwjgl.glfw.GLFW;

public class SpecialistDanceClient implements ClientModInitializer {

    private SpecialistDanceConfig config;
    private MusicManager musicManager;
    private DanceRegistry danceRegistry;
    private final DanceTextures danceTextures = new DanceTextures();
    private KeyMapping configKey;
    private long lastTriggerMillis = -1;

    private record PendingOre(Item expectedItem, long createdAtMillis) {}

    private record RecentDeath(@NonNull String sourceId, @NonNull BlockPos pos, long diedAtMillis) {}

    private record DropLink(@Nullable BlockPos orePos, @Nullable BlockPos deathPos, long linkedAtMillis) {}

    private final Map<@NonNull BlockPos, PendingOre> pendingOres = new HashMap<>();
    private final Map<Integer, DropLink> dropLinks = new HashMap<>();
    private final Map<Integer, Long> pendingDrops = new HashMap<>();
    private final Map<@NonNull BlockPos, Long> triggeredOres = new HashMap<>();
    private final Map<@NonNull BlockPos, Long> triggeredDeaths = new HashMap<>();
    private final Map<Integer, @NonNull String> liveMobs = new HashMap<>();
    private final Map<Integer, RecentDeath> recentDeaths = new HashMap<>();

    private @Nullable String pendingContainerSourceId;
    private @Nullable String containerSourceId;
    private @Nullable Map<Item, Integer> containerCounts;

    private ClientLevel lastLevel;

    private static SpecialistDanceClient instance;

    private long animationStartMillis = -1;
    private boolean musicDrivesAnimationEnd;
    private DanceRegistry.Dance playingDance;
    private final Deque<String> pendingLayout = new ArrayDeque<>();

    public static SpecialistDanceClient getInstance() {
        return instance;
    }

    public SpecialistDanceConfig getConfig() {
        return config;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }

    public DanceRegistry getDanceRegistry() {
        return danceRegistry;
    }

    public DanceTextures getDanceTextures() {
        return danceTextures;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = SpecialistDanceConfig.load();
        danceRegistry = new DanceRegistry(config);
        queueLayoutFor(danceRegistry.scan());

        System.out.println("Specialist Dance is Ready!");

        if(!config.enabled) {
            System.out.println("Specialist Dance: disabled via config");
            return;
        }

        musicManager = new MusicManager(config);
        musicManager.init();

        configKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.specialist-dance.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.MISC
        ));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            musicManager.shutdown();
            danceTextures.release(client);
        });

        ClientPlayerBlockBreakEvents.AFTER.register(
            (world, player, pos, state) -> {
                if (player != Minecraft.getInstance().player) {
                    return;
                }

                Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

                if (blockId == null) {
                    return;
                }

                String dropId = config.minedDropFor(blockId.toString());

                if (dropId == null) {
                    return;
                }

                Item expectedItem = itemById(dropId);

                if (expectedItem == null) {
                    return;
                }

                BlockPos orePos = pos.immutable();

                triggeredOres.remove(orePos);
                pendingOres.put(
                    orePos,
                    new PendingOre(expectedItem, System.currentTimeMillis())
                );

                log(
                    "Specialist Dance: registered mined "
                    + blockId
                    + " -> " + dropId
                    + " at " + orePos
                );
            }
        );

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity itemEntity) {
                pendingDrops.put(
                    itemEntity.getId(),
                    System.currentTimeMillis()
                );
            } else if (entity instanceof Mob mob) {
                Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());

                if (typeId != null) {
                    liveMobs.put(mob.getId(), typeId.toString());
                }
            }
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            liveMobs.remove(entity.getId());
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player != Minecraft.getInstance().player
                    || hand != InteractionHand.MAIN_HAND
                    || !config.hasTrigger("container")) {
                return InteractionResult.PASS;
            }

            BlockPos containerPos = hit.getBlockPos();

            if (!(level.getBlockEntity(containerPos) instanceof RandomizableContainerBlockEntity)) {
                return InteractionResult.PASS;
            }

            Identifier blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(containerPos).getBlock());

            if (blockId != null) {
                pendingContainerSourceId = blockId.toString();
                log("Specialist Dance: opened container " + pendingContainerSourceId);
            }

            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(
                "specialist-dance",
                "specialist_dance_overlay"
            ),
            (graphics, deltaTracker) -> {
                if (animationStartMillis < 0 || playingDance == null) {
                    return;
                }

                long elapsed = System.currentTimeMillis() - animationStartMillis;

                DanceRegistry.Dance dance = playingDance;

                if (dance.frameCount() <= 0) {
                    endAnimation("invalid pack " + dance.name());
                    return;
                }

                int frameDurationMs = effectiveFrameDurationMs(dance);
                int frameIndex = (int) (elapsed / frameDurationMs);

                String behavior = musicDrivesAnimationEnd ? config.animationEndBehavior : "stop";

                if ("loop".equals(behavior)) {
                    if (!musicManager.isPlaying()) {
                        endAnimation("music ended while looping");
                        return;
                    }
                    frameIndex = frameIndex % dance.frameCount();
                } else if ("hold".equals(behavior)) {
                    if (!musicManager.isPlaying()) {
                        endAnimation("music ended while holding");
                        return;
                    }
                    if (frameIndex >= dance.frameCount()) {
                        frameIndex = dance.frameCount() - 1;
                    }
                } else {
                    if (frameIndex >= dance.frameCount()) {
                        endAnimation("one pass complete");
                        return;
                    }
                }

                DanceTextures.Frame frame = danceTextures.get(
                    Minecraft.getInstance(),
                    dance,
                    frameIndex
                );

                if (frame == null) {
                    log("Specialist Dance: missing frame " + frameIndex + " of dance " + dance.name());
                    endAnimation("missing frame " + frameIndex);
                    return;
                }

                DanceTextures.Placement placement = DanceTextures.place(
                    frame,
                    config.stageWidthPercent,
                    config.stageHeightPercent,
                    config.positionX,
                    config.positionY,
                    graphics.guiWidth(),
                    graphics.guiHeight()
                );

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
            }
        );
    }

    private void log(String message) {
        if (config.debug) {
            System.out.println(message);
        }
    }

    public int effectiveFrameDurationMs(DanceRegistry.Dance dance) {
        double speedMultiplier = dance.speedMultiplier(config.animationSpeed);
        return Math.max(1, Math.round((float) (dance.frameDurationMs(config.frameDurationMs) / speedMultiplier)));
    }

    private void endAnimation(String reason) {
        if (animationStartMillis < 0) {
            return;
        }

        log("Specialist Dance: animation ended (" + reason + ")"
            + " dance=" + (playingDance == null ? "?" : playingDance.name())
            + " behavior=" + config.animationEndBehavior
            + " musicEnabled=" + config.musicEnabled
            + " drivesEnd=" + musicDrivesAnimationEnd);
        animationStartMillis = -1;
        playingDance = null;
        musicManager.stop();
    }

    private void tick(Minecraft client) {
        if (client.level != lastLevel) {
            pendingOres.clear();
            dropLinks.clear();
            pendingDrops.clear();
            triggeredOres.clear();
            liveMobs.clear();
            recentDeaths.clear();
            triggeredDeaths.clear();
            pendingContainerSourceId = null;
            containerSourceId = null;
            containerCounts = null;
            lastLevel = client.level;
            endAnimation("world change");
            return;
        }

        if (client.player != null && client.player.isDeadOrDying()) {
            endAnimation("player death");
        }

        while (configKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(
                    new SpecialistDanceConfigScreen(config, musicManager, Objects.requireNonNull(danceRegistry), client.screen)
                );
            }
        }

        openPendingLayout(client);

        long now = System.currentTimeMillis();

        pendingOres.entrySet().removeIf(
            entry -> now - entry.getValue().createdAtMillis() > config.oreTimeoutMs
        );

        dropLinks.entrySet().removeIf(
            entry -> now - entry.getValue().linkedAtMillis() > config.linkTimeoutMs
        );
        triggeredOres.entrySet().removeIf(
            entry -> now - entry.getValue() > config.linkTimeoutMs
        );

        trackMobDeaths(client, now);
        tickContainer(client);

        resolvePendingDrops(client, now);
    }

    private void trackMobDeaths(Minecraft client, long now) {
        if (!config.hasTrigger("mob")) {
            return;
        }

        ClientLevel level = client.level;

        if (level == null) {
            return;
        }

        Iterator<Map.Entry<Integer, String>> it = liveMobs.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, String> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());

            if (entity instanceof Mob mob && mob.isDeadOrDying()) {
                recentDeaths.put(entry.getKey(), new RecentDeath(entry.getValue(), mob.blockPosition(), now));
                it.remove();
            } else if (entity == null) {
                it.remove();
            }
        }

        recentDeaths.entrySet().removeIf(
            entry -> now - entry.getValue().diedAtMillis() > config.linkTimeoutMs
        );

        triggeredDeaths.entrySet().removeIf(
            entry -> now - entry.getValue() > config.linkTimeoutMs
        );
    }

    private void resolvePendingDrops(Minecraft client, long now) {
        ClientLevel level = client.level;

        if (level == null) {
            return;
        }

        Iterator<Map.Entry<Integer, Long>> iterator =
            pendingDrops.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Long> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());

            if (!(entity instanceof ItemEntity itemEntity)) {
                iterator.remove();
                continue;
            }

            ItemStack stack = itemEntity.getItem();

            if (stack.isEmpty()) {
                if (now - entry.getValue() > config.dropResolveTimeoutMs) {
                    iterator.remove();
                }
                continue;
            }

            iterator.remove();

            Item droppedItem = stack.getItem();
            long linkedAt = System.currentTimeMillis();

            BlockPos sourceOre = findSourceOre(
                itemEntity.blockPosition(),
                droppedItem
            );

            if (sourceOre != null) {
                dropLinks.put(itemEntity.getId(), new DropLink(sourceOre, null, linkedAt));
                triggeredOres.computeIfPresent(sourceOre, (orePos, previous) -> linkedAt);

                log(
                    "Specialist Dance: linked drop "
                    + droppedItem
                    + " (entity " + itemEntity.getId() + ")"
                    + " to ore at " + sourceOre
                );
                continue;
            }

            BlockPos sourceDeath = findSourceDeath(
                itemEntity.blockPosition(),
                droppedItem
            );

            if (sourceDeath != null) {
                dropLinks.put(itemEntity.getId(), new DropLink(null, sourceDeath, linkedAt));

                log(
                    "Specialist Dance: linked drop "
                    + droppedItem
                    + " (entity " + itemEntity.getId() + ")"
                    + " to death at " + sourceDeath
                );
                continue;
            }

            String itemId = itemIdOf(droppedItem);

            if (config.matches("pickup", null, itemId)) {
                dropLinks.put(itemEntity.getId(), new DropLink(null, null, linkedAt));

                log(
                    "Specialist Dance: linked drop "
                    + droppedItem
                    + " (entity " + itemEntity.getId() + ")"
                    + " as pickup-only trigger"
                );
            }
        }
    }

    private BlockPos findSourceOre(@NonNull BlockPos itemPos, Item droppedItem) {
        long now = System.currentTimeMillis();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, PendingOre> entry : pendingOres.entrySet()) {
            PendingOre pending = entry.getValue();

            if (now - pending.createdAtMillis() > config.oreTimeoutMs) {
                continue;
            }

            if (pending.expectedItem() != droppedItem) {
                continue;
            }

            @NonNull Vec3i orePos = Objects.requireNonNull(entry.getKey());
            @NonNull Vec3i itemPosition = itemPos;
            double distance = orePos.distSqr(itemPosition);

            if (distance <= config.searchRadiusSquared && distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }

        return best;
    }

    private @Nullable BlockPos findSourceDeath(@NonNull BlockPos itemPos, Item droppedItem) {
        if (!config.hasTrigger("mob")) {
            return null;
        }

        long now = System.currentTimeMillis();
        String itemId = itemIdOf(droppedItem);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (RecentDeath death : recentDeaths.values()) {
            if (now - death.diedAtMillis() > config.linkTimeoutMs) {
                continue;
            }

            if (!config.matches("mob", death.sourceId(), itemId)) {
                continue;
            }

            double distance = death.pos().distSqr(itemPos);

            if (distance <= config.searchRadiusSquared && distance < bestDistance) {
                bestDistance = distance;
                best = death.pos();
            }
        }

        return best;
    }

    public void onItemPickup(int entityId) {
        DropLink link = dropLinks.get(entityId);

        if (link == null) {
            return;
        }

        if (link.orePos() != null) {
            if (triggeredOres.putIfAbsent(link.orePos(), System.currentTimeMillis()) == null) {
                log("Specialist Dance: activated for mined ore at " + link.orePos());
                triggerDance();
            }
            return;
        }

        if (link.deathPos() != null) {
            if (triggeredDeaths.putIfAbsent(link.deathPos(), System.currentTimeMillis()) == null) {
                log("Specialist Dance: activated for mob death at " + link.deathPos());
                triggerDance();
            }
            return;
        }

        log("Specialist Dance: activated for pickup-only trigger");
        triggerDance();
    }

    public void queueLayoutFor(List<String> names) {
        if (names == null) {
            return;
        }

        for (String name : names) {
            if (danceRegistry.needsLayout(name) && !pendingLayout.contains(name)) {
                pendingLayout.add(name);
            }
        }

        if (!pendingLayout.isEmpty()) {
            log("queued layout editor for " + pendingLayout);
        }
    }

    private void openPendingLayout(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }

        Screen current = client.screen;

        if (current != null && !(current instanceof SpecialistDanceConfigScreen)) {
            return;
        }

        while (!pendingLayout.isEmpty()) {
            String name = pendingLayout.poll();

            if (name == null || !danceRegistry.needsLayout(name)) {
                continue;
            }

            config.activeDance = name;
            danceRegistry.markLayoutSeen(name);
            log("opening layout editor for new dance pack " + name);
            client.setScreen(new DanceLayoutScreen(
                config,
                danceRegistry,
                danceTextures,
                current
            ));
            return;
        }
    }

    private void tickContainer(Minecraft client) {
        if (pendingContainerSourceId == null && containerSourceId == null) {
            return;
        }

        Player player = client.player;

        if (player == null) {
            return;
        }

        if (pendingContainerSourceId != null) {
            containerSourceId = pendingContainerSourceId;
            pendingContainerSourceId = null;
            containerCounts = inventoryCounts(player.getInventory());
            return;
        }

        if (!(client.screen instanceof AbstractContainerScreen<?>)) {
            containerSourceId = null;
            containerCounts = null;
            return;
        }

        Map<Item, Integer> current = inventoryCounts(player.getInventory());
        String sourceId = containerSourceId;

        if (containerCounts != null && sourceId != null) {
            for (Map.Entry<Item, Integer> e : current.entrySet()) {
                int delta = e.getValue() - containerCounts.getOrDefault(e.getKey(), 0);

                if (delta > 0 && config.matches("container", sourceId, itemIdOf(e.getKey()))) {
                    log("Specialist Dance: activated for container " + sourceId);
                    triggerDance();
                    break;
                }
            }
        }

        containerCounts = current;
    }

    private static Map<Item, Integer> inventoryCounts(net.minecraft.world.entity.player.Inventory inv) {
        Map<Item, Integer> counts = new HashMap<>();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);

            if (stack.isEmpty()) {
                continue;
            }

            counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }

        return counts;
    }

    private static @Nullable Item itemById(@NonNull String id) {
        Identifier parsed = Identifier.tryParse(id);

        if (parsed == null) {
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getValue(parsed);

        return item == Items.AIR ? null : item;
    }

    private static @NonNull String itemIdOf(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "minecraft:air" : id.toString();
    }

    public void triggerDance() {
        long now = System.currentTimeMillis();

        if (config.cooldownMs > 0
                && lastTriggerMillis >= 0
                && now - lastTriggerMillis < config.cooldownMs) {
            return;
        }

        lastTriggerMillis = now;
        animationStartMillis = now;

        DanceRegistry.Dance dance = danceRegistry.find(config.activeDance);
        playingDance = dance;
        String track = config.musicEnabled
            ? musicManager.resolveTrackForDance(dance.name(), dance.track())
            : null;

        if (config.musicEnabled) {
            musicManager.play(track);
        }

        musicDrivesAnimationEnd = config.musicEnabled && musicManager.isPlaying() && !"stop".equals(config.animationEndBehavior);

        log("Specialist Dance: trigger dance=" + dance.name()
            + " frames=" + dance.frameCount()
            + " behavior=" + config.animationEndBehavior
            + " track=" + track
            + " musicEnabled=" + config.musicEnabled
            + " frameDurationMs=" + effectiveFrameDurationMs(dance)
            + " speed=" + dance.speedMultiplier(config.animationSpeed)
            + " drivesEnd=" + musicDrivesAnimationEnd);
    }
}
