package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.Targets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.UUID;

/** Player → server: follow this vessel as my target (or none, with a nil id). */
public record SetTargetC2S(UUID target) {

    public static void encode(SetTargetC2S msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.target);
    }

    public static SetTargetC2S decode(FriendlyByteBuf buf) {
        return new SetTargetC2S(buf.readUUID());
    }

    public static void handle(SetTargetC2S msg, CustomPayloadEvent.Context ctx) {
        if (ctx.getSender() != null) {
            Targets.set(ctx.getSender(), msg.target.getLeastSignificantBits() == 0 && msg.target.getMostSignificantBits() == 0 ? null : msg.target);
        }
        ctx.setPacketHandled(true);
    }
}
