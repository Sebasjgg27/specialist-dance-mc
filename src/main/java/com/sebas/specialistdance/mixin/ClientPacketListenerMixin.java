package com.sebas.specialistdance.mixin;

import com.sebas.specialistdance.SpecialistDanceClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(
        method = "handleTakeItemEntity",
        at = @At("RETURN")
    )
    private void specialistDance$onItemPickup(
        ClientboundTakeItemEntityPacket packet,
        CallbackInfo ci
    ) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            return;
        }

        if (packet.getPlayerId() != client.player.getId()) {
            return;
        }

        SpecialistDanceClient mod = SpecialistDanceClient.getInstance();

        if (mod != null) {
            mod.onItemPickup(packet.getItemId());
        }
    }
}
