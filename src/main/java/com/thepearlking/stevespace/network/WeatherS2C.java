package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.world.WeatherSystems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;

/** Server → player: Earth's storms as they are now (game time {@code now}). */
public record WeatherS2C(long now, List<WeatherSystems.Storm> storms) {

    public static void encode(WeatherS2C msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.now);
        buf.writeVarInt(msg.storms.size());
        for (WeatherSystems.Storm s : msg.storms) {
            buf.writeVarInt(s.id());
            buf.writeDouble(s.x());
            buf.writeDouble(s.z());
            buf.writeFloat((float) s.radius());
            buf.writeFloat((float) s.rain());
            buf.writeBoolean(s.thunder());
            buf.writeFloat((float) s.vx());
            buf.writeFloat((float) s.vz());
            buf.writeLong(s.born());
            buf.writeLong(s.ends());
        }
    }

    public static WeatherS2C decode(FriendlyByteBuf buf) {
        long now = buf.readLong();
        int n = buf.readVarInt();
        List<WeatherSystems.Storm> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new WeatherSystems.Storm(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readLong(), buf.readLong()));
        }
        return new WeatherS2C(now, list);
    }

    public static void handle(WeatherS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.ClientWeather.receive(msg);
        ctx.setPacketHandled(true);
    }
}
