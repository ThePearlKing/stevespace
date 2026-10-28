package com.thepearlking.stevespace.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server → every player: a nuclear detonation. Where (world, position, the ground below it), how
 * big (kilotons), what kind (an {@code NuclearEffects.Regime} ordinal), the world's body, its
 * place in the solar system, its height (the real km it stands for), and the universe time.
 */
public record NukeS2C(String dimension, double x, double y, double z, double groundY, double kt, int regime, String body,
                      double[] absolute, double heightKm, double time) {

    public static void encode(NukeS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.dimension);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeDouble(msg.groundY);
        buf.writeDouble(msg.kt);
        buf.writeVarInt(msg.regime);
        buf.writeUtf(msg.body);
        for (int i = 0; i < 3; i++) {
            buf.writeDouble(msg.absolute[i]);
        }
        buf.writeDouble(msg.heightKm);
        buf.writeDouble(msg.time);
    }

    public static NukeS2C decode(FriendlyByteBuf buf) {
        return new NukeS2C(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readUtf(), new double[]{buf.readDouble(), buf.readDouble(), buf.readDouble()}, buf.readDouble(),
                buf.readDouble());
    }

    public static void handle(NukeS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.nuke.NukeClient.detonation(msg);
        ctx.setPacketHandled(true);
    }
}
