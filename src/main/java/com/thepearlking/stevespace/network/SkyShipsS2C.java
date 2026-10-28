package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.radio.SkyShips;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.StateVector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Server → player: the ships out in space, where they are and how they're turned, and their silhouettes. */
public record SkyShipsS2C(double time, List<SkyShips.Ship> ships) {

    public static void encode(SkyShipsS2C msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.time);
        buf.writeVarInt(msg.ships.size());
        for (SkyShips.Ship s : msg.ships) {
            buf.writeUtf(s.id());
            buf.writeUtf(s.name());
            buf.writeBoolean(s.radio());
            buf.writeUtf(s.body());
            write(buf, s.state().position());
            write(buf, s.state().velocity());
            buf.writeDouble(s.epoch());
            buf.writeDouble(s.mu());
            buf.writeDouble(s.radius());
            buf.writeVarIntArray(s.pixels());
            buf.writeLong(s.version());
            for (float f : s.turn()) {
                buf.writeFloat(f);
            }
            for (double d : s.centre()) {
                buf.writeDouble(d);
            }
        }
    }

    public static SkyShipsS2C decode(FriendlyByteBuf buf) {
        double time = buf.readDouble();
        int n = buf.readVarInt();
        List<SkyShips.Ship> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            boolean radio = buf.readBoolean();
            String body = buf.readUtf();
            StateVector state = new StateVector(read(buf), read(buf));
            double epoch = buf.readDouble();
            double mu = buf.readDouble();
            double radius = buf.readDouble();
            int[] pixels = buf.readVarIntArray(SkyShips.SIZE * SkyShips.SIZE);
            long version = buf.readLong();
            float[] turn = {buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()};
            double[] centre = {buf.readDouble(), buf.readDouble(), buf.readDouble()};
            list.add(new SkyShips.Ship(id, name, radio, body, state, epoch, mu, radius, pixels, version, turn, centre));
        }
        return new SkyShipsS2C(time, list);
    }

    private static void write(FriendlyByteBuf buf, DVec3 v) {
        buf.writeDouble(v.x());
        buf.writeDouble(v.y());
        buf.writeDouble(v.z());
    }

    private static DVec3 read(FriendlyByteBuf buf) {
        return new DVec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(SkyShipsS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.telescope.TelescopeShips.receive(msg);
        ctx.setPacketHandled(true);
    }
}
