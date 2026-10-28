package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.planet.PlanetMaps;
import com.thepearlking.stevespace.world.planet.earth.EarthSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server → client on login: the seed and settings of Earth's planet-scale layout, so the client
 * can draw the same continents from altitude and from orbit that the server generates.
 */
public record EarthLayoutS2C(long seed, double continentSize, double landFraction, double iceCapLatitude, boolean real) {

    public static EarthLayoutS2C of(long seed, EarthSettings settings) {
        return new EarthLayoutS2C(seed, settings.continentSize(), settings.landFraction(), settings.iceCapLatitude(), settings.real());
    }

    public static void encode(EarthLayoutS2C msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.seed);
        buf.writeDouble(msg.continentSize);
        buf.writeDouble(msg.landFraction);
        buf.writeDouble(msg.iceCapLatitude);
        buf.writeBoolean(msg.real);
    }

    public static EarthLayoutS2C decode(FriendlyByteBuf buf) {
        return new EarthLayoutS2C(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
    }

    public static void handle(EarthLayoutS2C msg, CustomPayloadEvent.Context ctx) {
        PlanetMaps.setEarthLayout(msg.seed, new EarthSettings(msg.continentSize, msg.landFraction, msg.iceCapLatitude, msg.real));
        ctx.setPacketHandled(true);
    }
}
