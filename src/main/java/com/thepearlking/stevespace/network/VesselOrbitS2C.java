package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.space.ClientSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server → crew: where the vessel they are in really is (double precision), so the sky, the planet
 * below and the instruments are drawn from the right place. In orbit: position and velocity
 * relative to the body, plus the cell's axes. In map mode: map x, y, z and velocity east, up, south.
 */
public record VesselOrbitS2C(int entityId, String body, double time, double[] state, double[] axes, boolean coasting,
                             boolean mapMode) {

    public static void encode(VesselOrbitS2C msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.body);
        buf.writeDouble(msg.time);
        for (double d : msg.state) {
            buf.writeDouble(d);
        }
        for (double d : msg.axes) {
            buf.writeDouble(d);
        }
        buf.writeBoolean(msg.coasting);
        buf.writeBoolean(msg.mapMode);
    }

    public static VesselOrbitS2C decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String body = buf.readUtf();
        double time = buf.readDouble();
        double[] state = new double[6];
        for (int i = 0; i < 6; i++) {
            state[i] = buf.readDouble();
        }
        double[] axes = new double[9];
        for (int i = 0; i < 9; i++) {
            axes[i] = buf.readDouble();
        }
        boolean coasting = buf.readBoolean();
        return new VesselOrbitS2C(id, body, time, state, axes, coasting, buf.readBoolean());
    }

    public static void handle(VesselOrbitS2C msg, CustomPayloadEvent.Context ctx) {
        ClientSpace.receive(msg);
        ctx.setPacketHandled(true);
    }
}
