
package com.sebas.specialistdance;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SpecialistDanceClient implements ClientModInitializer {

    private int previousDiamondCount = -1;

    @Override
    public void onInitializeClient() {
        System.out.println("Specialist Dance is Ready!");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            checkForDiamonds(client);
        });
    }

    private void checkForDiamonds(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        int currentDiamondCount = 0;

        for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = client.player.getInventory().getItem(i);

            if (stack.is(Items.DIAMOND)) {
                currentDiamondCount += stack.getCount();
            }
        }

        if (previousDiamondCount == -1) {
            previousDiamondCount = currentDiamondCount;
            return;
        }

        if (currentDiamondCount > previousDiamondCount) {
            client.player.displayClientMessage(
                Component.literal("SPECIALIST DANCE!!! ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(
                        Component.literal(
                            "You now have " + currentDiamondCount + " diamonds!"
                        ).withStyle(ChatFormatting.WHITE)
                    ),
                false
            );
        }

        previousDiamondCount = currentDiamondCount;
    }
}
