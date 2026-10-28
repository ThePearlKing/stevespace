package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.gui.MissionControlScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Server → player at a Mission Control console: every vessel on the server and where it is. */
public record MissionControlS2C(double time, List<Entry> vessels) {

    /** One vessel. {@code mode}: 0 landed, 1 in the air, 2 in orbit. State is relative to the body. */
    public record Entry(java.util.UUID id, String name, String body, int mode, double altitude, double speed, double[] state) {
    }

    public static void encode(MissionControlS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.time);
        buf.writeVarInt(msg.vessels.size());
        for (Entry e : msg.vessels) {
            buf.writeUUID(e.id);
            buf.writeUtf(e.name);
            buf.writeUtf(e.body);
            buf.writeByte(e.mode);
            buf.writeDouble(e.altitude);
            buf.writeDouble(e.speed);
            for (double d : e.state) {
                buf.writeDouble(d);
            }
        }
    }

    public static MissionControlS2C decode(FriendlyByteBuf buf) {
        double time = buf.readDouble();
        int count = buf.readVarInt();
        List<Entry> vessels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            java.util.UUID id = buf.readUUID();
            String name = buf.readUtf();
            String body = buf.readUtf();
            int mode = buf.readByte();
            double altitude = buf.readDouble();
            double speed = buf.readDouble();
            double[] state = new double[6];
            for (int k = 0; k < 6; k++) {
                state[k] = buf.readDouble();
            }
            vessels.add(new Entry(id, name, body, mode, altitude, speed, state));
        }
        return new MissionControlS2C(time, vessels);
    }

    public static void handle(MissionControlS2C msg, CustomPayloadEvent.Context ctx) {
        MissionControlScreen.open(msg);
        ctx.setPacketHandled(true);
    }
}
