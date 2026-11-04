package com.sebas.specialistdance.config.menu;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.sebas.specialistdance.config.SpecialistDanceConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class OreTriggersScreen extends Screen {
    private static final int PANE_TOP = 34;
    private static final int ROW_HEIGHT = 20;
    private enum RuleType { BLOCK, PICKUP }

    private final SpecialistDanceConfig config;
    private final Screen parent;

    private int listX;
    private int listWidth;
    private int listBottom;
    private int actionTop;

    private String blockText = "";
    private String dropText = "";
    private @Nullable String notice;

    public OreTriggersScreen(SpecialistDanceConfig config, Screen parent) {
        super(Component.literal("Your rules"));
        this.config = config;
        this.parent = parent;
    }

    @Override
    protected void init() {
        listWidth = Math.min(360, this.width - 32);
        listX = this.width / 2 - listWidth / 2;

        int doneTop = this.height - 32;
        actionTop = doneTop - 22;
        int dropTop = actionTop - 20;
        int footerTop = dropTop - 20;
        int listHeight = Math.max(24, footerTop - 6 - 40);

        listBottom = 40 + listHeight;

        addRenderableWidget(new OreList(this.minecraft, listWidth, listHeight, listX, 40));

        int boxWidth = listWidth - 74;

        addRenderableWidget(idBox(footerTop, boxWidth, blockText, value -> blockText = value, "Block you mine"));
        addRenderableWidget(idBox(dropTop, boxWidth, dropText, value -> dropText = value, "Item you pick up"));

        addRenderableWidget(
            Button.builder(Component.literal("Mine"), button ->
                minecraft.setScreen(new ItemGridScreen(
                    this, Component.literal("Pick the mined block"), blockText, id -> blockText = id)))
                .bounds(listX + boxWidth + 4, footerTop, 68, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Drop"), button ->
                minecraft.setScreen(new ItemGridScreen(
                    this, Component.literal("Pick the picked-up item"), dropText, id -> dropText = id)))
                .bounds(listX + boxWidth + 4, dropTop, 68, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Add rule"), button -> {
                if (config.addBlockRule(blockText, dropText)) {
                    blockText = "";
                    dropText = "";
                    notice = null;
                    rebuildWidgets();
                } else {
                    notice = "both ids must look like minecraft:diamond_ore";
                }
            })
                .bounds(listX, actionTop, 76, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Add pickup"), button -> {
                if (config.addPickupRule(dropText)) {
                    dropText = "";
                    notice = null;
                    rebuildWidgets();
                } else {
                    notice = "item id must look like minecraft:golden_axe";
                }
            })
                .bounds(listX + 140, actionTop, 76, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Reset"), button -> {
                config.resetRules();
                blockText = "";
                dropText = "";
                notice = null;
                rebuildWidgets();
            })
                .bounds(listX + 220, actionTop, 56, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 32, 200, 20)
                .build()
        );
    }

    private EditBox idBox(int y, int width, String value, Consumer<String> onType, String hint) {
        EditBox box = new EditBox(this.font, listX, y, width, 18, Component.literal(hint));

        box.setMaxLength(128);
        box.setHint(Component.literal(hint));
        box.setValue(value);
        box.setResponder(onType);

        return box;
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        String hint = config.blockRules.isEmpty() && config.pickupRules.isEmpty()
            ? "No rules yet - add one below"
            : "Left-click toggles · right-click deletes · saved on close";

        graphics.drawCenteredString(this.font, hint, this.width / 2, 25, 0xFFA0A0A0);

        if (notice != null) {
            graphics.drawString(this.font, notice, listX + 142, actionTop + 6, 0xFFFF7070);
        }
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0x60000000);
        graphics.fill(listX - 6, PANE_TOP, listX + listWidth + 6, actionTop + 24, 0x80000000);
    }

    @Override
    public void onClose() {
        config.save();
        this.minecraft.setScreen(parent);
    }

    private class OreEntry extends ObjectSelectionList.Entry<OreEntry> {
        private final RuleType type;
        private final @Nullable String blockId;
        private final String dropId;
        private final Font font;

        OreEntry(RuleType type, @Nullable String blockId, String dropId, Font font) {
            this.type = type;
            this.blockId = blockId;
            this.dropId = dropId;
            this.font = font;
        }

        RuleType type() {
            return type;
        }

        String key() {
            return type == RuleType.BLOCK ? blockId : dropId;
        }

        boolean enabled() {
            if (type == RuleType.BLOCK) {
                return !config.disabledBlockRules.contains(blockId);
            }
            return !config.disabledPickupRules.contains(dropId);
        }

        void toggle() {
            if (type == RuleType.BLOCK) {
                if (enabled()) {
                    config.disabledBlockRules.add(blockId);
                } else {
                    config.disabledBlockRules.remove(blockId);
                }
            } else {
                if (enabled()) {
                    config.disabledPickupRules.add(dropId);
                } else {
                    config.disabledPickupRules.remove(dropId);
                }
            }
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(key());
        }

        @Override
        public void renderContent(
            @NonNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            boolean isMouseOver,
            float partialTick) {
            int textY = getContentYMiddle() - font.lineHeight / 2;
            int iconY = getContentYMiddle() - 8;
            int color = isMouseOver ? 0xFFFFFFFF : 0xFFB0B0B0;
            String check = enabled() ? "[x] " : "[ ] ";

            graphics.drawString(font, check, getContentX(), textY, color);

            int x = getContentX() + font.width(check);

            if (type == RuleType.BLOCK) {
                ItemStack mined = ItemGridScreen.stackOf(blockId);

                if (!mined.isEmpty()) {
                    graphics.renderItem(mined, x, iconY);
                    x += 18;
                }

                graphics.drawString(font, blockId, x, textY, color);

                int nameEnd = x + font.width(blockId);
                ItemStack drop = ItemGridScreen.stackOf(dropId);
                int dropTextX = getContentRight() - font.width(dropId);
                int dropIconX = dropTextX - 18;

                if (dropIconX <= nameEnd + 4) {
                    return;
                }

                if (!drop.isEmpty()) {
                    graphics.renderItem(drop, dropIconX, iconY);
                }

                graphics.drawString(font, dropId, dropTextX, textY, 0xFF909090);

                if (isMouseOver && !drop.isEmpty()) {
                    graphics.setTooltipForNextFrame(font, drop, mouseX, mouseY);
                }
            } else {
                graphics.drawString(font, "pickup ", x, textY, 0xFF909090);
                x += font.width("pickup ");

                ItemStack item = ItemGridScreen.stackOf(dropId);

                if (!item.isEmpty()) {
                    graphics.renderItem(item, x, iconY);
                    x += 18;
                }

                graphics.drawString(font, dropId, x, textY, color);

                if (isMouseOver && !item.isEmpty()) {
                    graphics.setTooltipForNextFrame(font, item, mouseX, mouseY);
                }
            }
        }
    }

    private class OreList extends ObjectSelectionList<OreEntry> {

        OreList(Minecraft minecraft, int width, int height, int x, int y) {
            super(minecraft, width, height, x, y);

            for (String blockId : List.copyOf(config.blockRules.keySet())) {
                addEntry(new OreEntry(RuleType.BLOCK, blockId, config.blockRules.get(blockId), font), ROW_HEIGHT);
            }

            for (String itemId : List.copyOf(config.pickupRules)) {
                addEntry(new OreEntry(RuleType.PICKUP, null, itemId, font), ROW_HEIGHT);
            }
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            OreEntry entry = getEntryAtPosition(event.x(), event.y());

            if (entry != null && event.button() == 0) {
                entry.toggle();
                return true;
            }

            if (entry != null && event.button() == 1) {
                boolean removed = entry.type() == RuleType.BLOCK
                    ? config.removeBlockRule(entry.blockId)
                    : config.removePickupRule(entry.dropId);

                if (removed) {
                    notice = null;
                    rebuildWidgets();
                }

                return true;
            }

            return super.mouseClicked(event, doubleClick);
        }
    }
}
