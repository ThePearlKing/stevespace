package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.client.gui.FlightComputerScreen;
import com.thepearlking.stevespace.vessel.VesselReport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server → client: open the flight computer showing this vessel report. */
public record FlightComputerS2C(VesselReport report) {

    public static void encode(FlightComputerS2C msg, FriendlyByteBuf buf) {
        msg.report.write(buf);
    }

    public static FlightComputerS2C decode(FriendlyByteBuf buf) {
        return new FlightComputerS2C(VesselReport.read(buf));
    }

    public static void handle(FlightComputerS2C msg, CustomPayloadEvent.Context ctx) {
        FlightComputerScreen.open(msg.report);
        ctx.setPacketHandled(true);
    }
}
