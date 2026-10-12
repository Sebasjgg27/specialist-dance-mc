package com.sebas.specialistdance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(
        method = "handleTakeItemEntity",
        at = @At("HEAD")
    )
    private void specialistDance$onItemPickup(
        ClientboundTakeItemEntityPacket packet,
        CallbackInfo ci
    ) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            return;
        }
        if (packet.getPlayerId() != client.player.getId()){
            return;
        }
        Entity entity = client.level.getEntity(packet.getItemId());

        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();

        System.out.println(
            "Specialist Dance observed pickup: "
                + stack.getItem()
        );

    }

}

