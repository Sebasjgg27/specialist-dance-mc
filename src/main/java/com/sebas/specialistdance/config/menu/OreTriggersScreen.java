package com.sebas.specialistdance.config.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.sebas.specialistdance.config.SpecialistDanceConfig;
import com.sebas.specialistdance.config.SpecialistDanceConfig.Rule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

public class OreTriggersScreen extends Screen {
    private static final int ROW_HEIGHT = 20;

    private final SpecialistDanceConfig config;
    private final Screen parent;

    private final List<String> blockIds;
    private final List<String> itemIds;
    private final List<String> mobIds;

    private String trigger = "mined";
    private String sourceText = "";
    private String itemText = "";
    private @Nullable String sourceMatch;
    private @Nullable String itemMatch;

    private @Nullable EditBox sourceBox;
    private @Nullable EditBox itemBox;
    private @Nullable RuleList ruleList;

    private int listX;
    private int listWidth;
    private int actionTop;
    private @Nullable String notice;

    public OreTriggersScreen(SpecialistDanceConfig config, Screen parent) {
        super(Component.literal("Your rules"));
        this.config = config;
        this.parent = parent;
        this.blockIds = sortedIds(BuiltInRegistries.BLOCK);
        this.itemIds = sortedIds(BuiltInRegistries.ITEM);
        this.mobIds = sortedIds(BuiltInRegistries.ENTITY_TYPE);
    }

    private static <T> List<String> sortedIds(Registry<T> registry) {
        List<Identifier> ids = new ArrayList<>(registry.keySet());
        ids.sort(Comparator.comparing(Identifier::toString));
        return ids.stream().map(Identifier::toString).toList();
    }

    private boolean requiresSource() {
        return !"pickup".equals(trigger);
    }

    private static String labelOf(String trigger) {
        return switch (trigger) {
            case "mined" -> "mine";
            case "pickup" -> "pickup";
            case "mob" -> "kill";
            case "container" -> "open";
            default -> trigger;
        };
    }

    @Override
    protected void init() {
        listWidth = Math.min(360, this.width - 32);
        listX = this.width / 2 - listWidth / 2;

        int doneTop = this.height - 32;
        actionTop = doneTop - 22;
        int itemTop = actionTop - 20;
        int sourceTop = itemTop - 20;
        int triggerTop = sourceTop - 22;
        int listHeight = Math.max(24, triggerTop - 6 - 40);

        addRenderableWidget(new RuleList(this.minecraft, listWidth, listHeight, listX, 40));

        addRenderableWidget(
            CycleButton.<String>builder(Component::literal, trigger)
                .withValues(SpecialistDanceConfig.RULE_TRIGGERS)
                .create(
                    listX, triggerTop, listWidth, 18,
                    Component.literal("Trigger"),
                    (button, value) -> {
                        trigger = value;
                        if (!requiresSource()) sourceText = "";
                        rebuildWidgets();
                    }
                )
        );

        int boxWidth = listWidth - 74;
        sourceBox = null;

        if (requiresSource()) {
            EditBox sBox = new EditBox(this.font, listX, sourceTop, boxWidth, 18, Component.literal("Source"));
            sBox.setMaxLength(128);
            sBox.setHint(Component.literal(sourceHint()));
            sBox.setValue(sourceText);
            sBox.setResponder(value -> {
                sourceText = value;
                sourceMatch = ghost(sBox, sourceRegistry(), value);
            });
            sourceBox = sBox;
            addRenderableWidget(sBox);
        }

        EditBox iBox = new EditBox(this.font, listX, itemTop, boxWidth, 18, Component.literal("Item"));
        iBox.setMaxLength(128);
        iBox.setHint(Component.literal("Item you get"));
        iBox.setValue(itemText);
        iBox.setResponder(value -> {
            itemText = value;
            itemMatch = ghost(iBox, itemIds, value);
        });
        itemBox = iBox;
        addRenderableWidget(iBox);

        addRenderableWidget(
            Button.builder(Component.literal("Pick item"), button ->
                minecraft.setScreen(new ItemGridScreen(
                    this, Component.literal("Pick the item"), itemText, id -> itemText = id)))
                .bounds(listX + boxWidth + 4, itemTop, 68, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Add rule"), button -> {
                if (config.addRule(trigger, requiresSource() ? sourceText : "", itemText)) {
                    sourceText = "";
                    itemText = "";
                    notice = null;
                    rebuildWidgets();
                } else {
                    notice = "id must look like minecraft:diamond_ore";
                }
            })
                .bounds(listX, actionTop, 76, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Reset"), button -> {
                config.resetRules();
                sourceText = "";
                itemText = "";
                notice = null;
                rebuildWidgets();
            })
                .bounds(listX + 140, actionTop, 56, 18)
                .build()
        );

        addRenderableWidget(
            Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 100, doneTop, 200, 20)
                .build()
        );
    }

    private String sourceHint() {
        return switch (trigger) {
            case "mob" -> "Mob you kill";
            case "container" -> "Container you open";
            default -> "Block you mine";
        };
    }

    private List<String> sourceRegistry() {
        return "mob".equals(trigger) ? mobIds : blockIds;
    }

    private static @Nullable String ghost(EditBox box, List<String> ids, String typed) {
        String needle = typed.toLowerCase(Locale.ROOT);

        if (needle.isEmpty()) {
            box.setSuggestion(null);
            return null;
        }

        for (String id : ids) {
            if (id.toLowerCase(Locale.ROOT).startsWith(needle)) {
                box.setSuggestion(id.substring(typed.length()));
                return id;
            }
        }

        box.setSuggestion(null);
        return null;
    }

    private boolean acceptGhost(EditBox box, @Nullable String match) {
        if (match != null && box != null && box.isFocused()) {
            box.setSuggestion(null);
            box.setValue(match);
            if (box == sourceBox) sourceText = match; else itemText = match;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            if (acceptGhost(sourceBox, sourceMatch) || acceptGhost(itemBox, itemMatch)) {
                return true;
            }
        }

        if (ruleList != null && ruleList.handleKey(event.key())) {
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        String hint = config.rules.isEmpty()
            ? "No rules yet - add one below"
            : "Click toggles · right-click deletes · Tab accepts ghost";

        graphics.drawCenteredString(this.font, hint, this.width / 2, 25, 0xFFA0A0A0);

        if (notice != null) {
            graphics.drawString(this.font, notice, listX + 142, actionTop + 6, 0xFFFF7070);
        }
    }

    @Override
    public void renderBackground(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0x60000000);
    }

    @Override
    public void onClose() {
        config.save();
        this.minecraft.setScreen(parent);
    }

    private class RuleEntry extends ObjectSelectionList.Entry<RuleEntry> {
        final Rule rule;

        RuleEntry(Rule rule) {
            this.rule = rule;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(rule.label());
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
            String check = rule.enabled ? "[x] " : "[ ] ";

            graphics.drawString(font, check, getContentX(), textY, color);

            int x = getContentX() + font.width(check);

            graphics.drawString(font, labelOf(rule.trigger), x, textY, 0xFF909090);
            x += font.width(labelOf(rule.trigger)) + 4;

            if (rule.requiresSource()) {
                graphics.drawString(font, rule.source, x, textY, color);
                x += font.width(rule.source) + 4;
                graphics.drawString(font, "->", x, textY, 0xFF909090);
                x += font.width("->") + 4;
            }

            ItemStack drop = ItemGridScreen.stackOf(rule.item);

            if (!drop.isEmpty()) {
                graphics.renderItem(drop, x, iconY);
                x += 18;
            }

            graphics.drawString(font, rule.item, x, textY, color);

            if (isMouseOver && !drop.isEmpty()) {
                graphics.setTooltipForNextFrame(font, drop, mouseX, mouseY);
            }
        }
    }

    private class RuleList extends ObjectSelectionList<RuleEntry> {
        RuleList(Minecraft minecraft, int width, int height, int x, int y) {
            super(minecraft, width, height, x, y);
            ruleList = this;
            for (Rule rule : List.copyOf(config.rules)) {
                addEntry(new RuleEntry(rule), ROW_HEIGHT);
            }
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return true;
        }

        boolean handleKey(int key) {
            RuleEntry sel = getSelected();

            if (sel == null) {
                return false;
            }

            return switch (key) {
                case GLFW.GLFW_KEY_SPACE -> {
                    config.toggleRule(sel.rule);
                    rebuildWidgets();
                    yield true;
                }
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> {
                    config.removeRule(sel.rule);
                    notice = null;
                    rebuildWidgets();
                    yield true;
                }
                default -> false;
            };
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            RuleEntry entry = getEntryAtPosition(event.x(), event.y());

            if (entry == null) {
                return super.mouseClicked(event, doubleClick);
            }

            setSelected(entry);

            if (event.button() == 0) {
                config.toggleRule(entry.rule);
                return true;
            }

            if (event.button() == 1) {
                if (config.removeRule(entry.rule)) {
                    notice = null;
                    rebuildWidgets();
                }
                return true;
            }

            return super.mouseClicked(event, doubleClick);
        }
    }
}
