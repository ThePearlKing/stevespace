package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.space.ClientSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → client: the next dimension change is a vessel crossing into or out of space. */
public record SeamlessTransferS2C() {

    public static void encode(SeamlessTransferS2C msg, FriendlyByteBuf buf) {
    }

    public static SeamlessTransferS2C decode(FriendlyByteBuf buf) {
        return new SeamlessTransferS2C();
    }

    public static void handle(SeamlessTransferS2C msg, CustomPayloadEvent.Context ctx) {
        ClientSpace.expectSeamlessTransfer();
        ctx.setPacketHandled(true);
    }
}
