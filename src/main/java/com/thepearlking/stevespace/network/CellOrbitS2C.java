package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.space.ClientSpace;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server → a player walking around in space (aboard a parked ship, or out on a spacewalk): the
 * orbit of the cell they are in, and the point in the cell that follows it, so their sky is right.
 */
public record CellOrbitS2C(String body, double time, double[] state, double anchorX, double anchorY, double anchorZ) {

    public static void encode(CellOrbitS2C msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.body);
        buf.writeDouble(msg.time);
        for (double d : msg.state) {
            buf.writeDouble(d);
        }
        buf.writeDouble(msg.anchorX);
        buf.writeDouble(msg.anchorY);
        buf.writeDouble(msg.anchorZ);
    }

    public static CellOrbitS2C decode(FriendlyByteBuf buf) {
        String body = buf.readUtf();
        double time = buf.readDouble();
        double[] state = new double[6];
        for (int i = 0; i < 6; i++) {
            state[i] = buf.readDouble();
        }
        return new CellOrbitS2C(body, time, state, buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(CellOrbitS2C msg, CustomPayloadEvent.Context ctx) {
        ClientSpace.receiveCell(msg);
        ctx.setPacketHandled(true);
    }
}
