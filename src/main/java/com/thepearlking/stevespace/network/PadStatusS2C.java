package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.vessel.VesselReport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → client: how the vessel around the pilot's seat checks out, while still on the ground. */
public record PadStatusS2C(VesselReport report, boolean needsPropellant) {

    public static void encode(PadStatusS2C msg, FriendlyByteBuf buf) {
        msg.report.write(buf);
        buf.writeBoolean(msg.needsPropellant);
    }

    public static PadStatusS2C decode(FriendlyByteBuf buf) {
        return new PadStatusS2C(VesselReport.read(buf), buf.readBoolean());
    }

    public static void handle(PadStatusS2C msg, CustomPayloadEvent.Context ctx) {
        com.thepearlking.stevespace.client.FlightHud.setPadStatus(msg.report, msg.needsPropellant);
        ctx.setPacketHandled(true);
    }
}
