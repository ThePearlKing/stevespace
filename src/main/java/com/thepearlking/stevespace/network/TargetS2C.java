package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.space.ClientTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → player: where their target vessel is (state relative to its body), or that it's gone. */
public record TargetS2C(boolean present, String name, String body, double time, double[] state) {

    public static void encode(TargetS2C msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.present);
        if (msg.present) {
            buf.writeUtf(msg.name);
            buf.writeUtf(msg.body);
            buf.writeDouble(msg.time);
            for (double d : msg.state) {
                buf.writeDouble(d);
            }
        }
    }

    public static TargetS2C decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return new TargetS2C(false, "", "", 0, new double[6]);
        }
        String name = buf.readUtf();
        String body = buf.readUtf();
        double time = buf.readDouble();
        double[] state = new double[6];
        for (int i = 0; i < 6; i++) {
            state[i] = buf.readDouble();
        }
        return new TargetS2C(true, name, body, time, state);
    }

    public static void handle(TargetS2C msg, CustomPayloadEvent.Context ctx) {
        ClientTarget.receive(msg);
        ctx.setPacketHandled(true);
    }
}
