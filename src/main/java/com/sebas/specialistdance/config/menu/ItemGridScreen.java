package com.sebas.specialistdance.config.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemGridScreen extends Screen {
    private static final int SLOT = 18;
    private static final int MARGIN = 9;
    private static final int GRID_TOP = 46;
    private static final int FOOTER = 30;

    private final Screen parent;
    private final Consumer<@NonNull String> onPick;
    private final List<@NonNull Identifier> allIds = new ArrayList<>();
    private final List<@NonNull Identifier> shown = new ArrayList<>();

    private @NonNull String query;
    private int columns = 1;
    private int rows = 1;
    private int page;

    public ItemGridScreen(
            Screen parent,
            Component title,
            @NonNull String initialQuery,
            Consumer<@NonNull String> onPick) {
        super(title);
        this.parent = parent;
        this.onPick = onPick;
        this.query = initialQuery;

        allIds.addAll(BuiltInRegistries.ITEM.keySet());
        allIds.sort(Comparator.comparing(Identifier::toString));
    }

    @Override
    protected void init() {
        columns = Math.max(1, (this.width - MARGIN * 2) / SLOT);
        rows = Math.max(1, (this.height - GRID_TOP - FOOTER) / SLOT);

        EditBox search = new EditBox(
            this.font,
            MARGIN,
            24,
            this.width - MARGIN * 2,
            18,
            Component.literal("Filter")
        );

        search.setHint(Component.literal("filter by id, e.g. diamond"));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refresh();
        });

        addRenderableWidget(search);

        addRenderableWidget(
            Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(this.width - MARGIN - 68, this.height - 24, 68, 20)
                .build()
        );

        setInitialFocus(search);
        refresh();
    }

    private void refresh() {
        String needle = query.trim().toLowerCase(Locale.ROOT);

        shown.clear();

        for (Identifier id : allIds) {
            if (needle.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.add(id);
            }
        }

        page = 0;
    }

    private int pageCount() {
        int perPage = columns * rows;

        return Math.max(1, (shown.size() + perPage - 1) / perPage);
    }

    static @NonNull ItemStack stackOf(@NonNull String rawId) {
        Identifier id = Identifier.tryParse(rawId);

        if (id == null) {
            return ItemStack.EMPTY;
        }

        Item item = BuiltInRegistries.ITEM.getValue(id);

        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public void render(@NonNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 9, 0xFFFFFFFF);

        int perPage = columns * rows;
        int index = page * perPage;
        @NonNull ItemStack hovered = ItemStack.EMPTY;

        for (int slot = 0; slot < perPage && index < shown.size(); slot++, index++) {
            int x = MARGIN + (slot % columns) * SLOT;
            int y = GRID_TOP + (slot / columns) * SLOT;
            boolean isHovered = mouseX >= x && mouseX < x + SLOT
                && mouseY >= y && mouseY < y + SLOT;

            graphics.fill(x, y, x + SLOT, y + SLOT, isHovered ? 0x80FFFFFF : 0x40000000);

            ItemStack stack = stackOf(shown.get(index).toString());

            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);

                if (isHovered) {
                    hovered = stack;
                }
            }
        }

        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
        }

        graphics.drawString(
            this.font,
            shown.isEmpty()
                ? "no item matches \"" + query + "\""
                : (page + 1) + " / " + pageCount() + " · " + shown.size() + " items · scroll to page",
            MARGIN,
            this.height - 44,
            0xFFA0A0A0
        );
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        int x = (int) event.x();
        int y = (int) event.y();

        if (y >= GRID_TOP && y < GRID_TOP + rows * SLOT
                && x >= MARGIN && x < MARGIN + columns * SLOT) {
            int column = (x - MARGIN) / SLOT;
            int row = (y - GRID_TOP) / SLOT;
            int index = page * columns * rows + row * columns + column;

            if (index < shown.size()) {
                onPick.accept(shown.get(index).toString());
                this.minecraft.setScreen(parent);
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        page = Math.clamp(page + (scrollY > 0 ? -1 : 1), 0, pageCount() - 1);
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
