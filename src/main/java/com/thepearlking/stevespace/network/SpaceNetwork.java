package com.thepearlking.stevespace.network;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** The mod's network channel. Each message is a record in this package with encode/decode/handle. */
public final class SpaceNetwork {

    public static final SimpleChannel CHANNEL = ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "main"))
            .networkProtocolVersion(2)
            .simpleChannel();

    private SpaceNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(UniverseSyncS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UniverseSyncS2C::encode).decoder(UniverseSyncS2C::decode)
                .consumerMainThread(UniverseSyncS2C::handle).add();
        CHANNEL.messageBuilder(EarthLayoutS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EarthLayoutS2C::encode).decoder(EarthLayoutS2C::decode)
                .consumerMainThread(EarthLayoutS2C::handle).add();
        CHANNEL.messageBuilder(VesselControlC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VesselControlC2S::encode).decoder(VesselControlC2S::decode)
                .consumerMainThread(VesselControlC2S::handle).add();
        CHANNEL.messageBuilder(SasC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SasC2S::encode).decoder(SasC2S::decode)
                .consumerMainThread(SasC2S::handle).add();
        CHANNEL.messageBuilder(ManeuverC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ManeuverC2S::encode).decoder(ManeuverC2S::decode)
                .consumerMainThread(ManeuverC2S::handle).add();
        CHANNEL.messageBuilder(CabinWalkC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CabinWalkC2S::encode).decoder(CabinWalkC2S::decode)
                .consumerMainThread(CabinWalkC2S::handle).add();
        CHANNEL.messageBuilder(LeaveSeatC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LeaveSeatC2S::encode).decoder(LeaveSeatC2S::decode)
                .consumerMainThread(LeaveSeatC2S::handle).add();
        CHANNEL.messageBuilder(FlightComputerS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlightComputerS2C::encode).decoder(FlightComputerS2C::decode)
                .consumerMainThread(FlightComputerS2C::handle).add();
        CHANNEL.messageBuilder(SeamlessTransferS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SeamlessTransferS2C::encode).decoder(SeamlessTransferS2C::decode)
                .consumerMainThread(SeamlessTransferS2C::handle).add();
        CHANNEL.messageBuilder(VesselOrbitS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(VesselOrbitS2C::encode).decoder(VesselOrbitS2C::decode)
                .consumerMainThread(VesselOrbitS2C::handle).add();
        CHANNEL.messageBuilder(WarpRequestC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WarpRequestC2S::encode).decoder(WarpRequestC2S::decode)
                .consumerMainThread(WarpRequestC2S::handle).add();
        CHANNEL.messageBuilder(MissionControlS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MissionControlS2C::encode).decoder(MissionControlS2C::decode)
                .consumerMainThread(MissionControlS2C::handle).add();
        CHANNEL.messageBuilder(SetTargetC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTargetC2S::encode).decoder(SetTargetC2S::decode)
                .consumerMainThread(SetTargetC2S::handle).add();
        CHANNEL.messageBuilder(TargetS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TargetS2C::encode).decoder(TargetS2C::decode)
                .consumerMainThread(TargetS2C::handle).add();
        CHANNEL.messageBuilder(OxygenS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OxygenS2C::encode).decoder(OxygenS2C::decode)
                .consumerMainThread(OxygenS2C::handle).add();
        CHANNEL.messageBuilder(CellOrbitS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CellOrbitS2C::encode).decoder(CellOrbitS2C::decode)
                .consumerMainThread(CellOrbitS2C::handle).add();
        CHANNEL.messageBuilder(PadStatusS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PadStatusS2C::encode).decoder(PadStatusS2C::decode)
                .consumerMainThread(PadStatusS2C::handle).add();
        CHANNEL.messageBuilder(SkyShipsS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkyShipsS2C::encode).decoder(SkyShipsS2C::decode)
                .consumerMainThread(SkyShipsS2C::handle).add();
        CHANNEL.messageBuilder(SkyShipsC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SkyShipsC2S::encode).decoder(SkyShipsC2S::decode)
                .consumerMainThread(SkyShipsC2S::handle).add();
        CHANNEL.messageBuilder(RadioS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RadioS2C::encode).decoder(RadioS2C::decode)
                .consumerMainThread(RadioS2C::handle).add();
        CHANNEL.messageBuilder(RadioC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RadioC2S::encode).decoder(RadioC2S::decode)
                .consumerMainThread(RadioC2S::handle).add();
        CHANNEL.messageBuilder(RadioTuneC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RadioTuneC2S::encode).decoder(RadioTuneC2S::decode)
                .consumerMainThread(RadioTuneC2S::handle).add();
        CHANNEL.messageBuilder(AutopilotC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AutopilotC2S::encode).decoder(AutopilotC2S::decode)
                .consumerMainThread(AutopilotC2S::handle).add();
        CHANNEL.messageBuilder(ShipRemoveC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShipRemoveC2S::encode).decoder(ShipRemoveC2S::decode)
                .consumerMainThread(ShipRemoveC2S::handle).add();
        CHANNEL.messageBuilder(ShipRemoveS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShipRemoveS2C::encode).decoder(ShipRemoveS2C::decode)
                .consumerMainThread(ShipRemoveS2C::handle).add();
        CHANNEL.messageBuilder(FleetC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FleetC2S::encode).decoder(FleetC2S::decode)
                .consumerMainThread(FleetC2S::handle).add();
        CHANNEL.messageBuilder(FleetS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FleetS2C::encode).decoder(FleetS2C::decode)
                .consumerMainThread(FleetS2C::handle).add();
        CHANNEL.messageBuilder(DockC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DockC2S::encode).decoder(DockC2S::decode)
                .consumerMainThread(DockC2S::handle).add();
        CHANNEL.messageBuilder(DockListS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DockListS2C::encode).decoder(DockListS2C::decode)
                .consumerMainThread(DockListS2C::handle).add();
        CHANNEL.messageBuilder(RemoteTunerS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoteTunerS2C::encode).decoder(RemoteTunerS2C::decode)
                .consumerMainThread(RemoteTunerS2C::handle).add();
        CHANNEL.messageBuilder(RemoteConsoleS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoteConsoleS2C::encode).decoder(RemoteConsoleS2C::decode)
                .consumerMainThread(RemoteConsoleS2C::handle).add();
        CHANNEL.messageBuilder(RemoteStateS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RemoteStateS2C::encode).decoder(RemoteStateS2C::decode)
                .consumerMainThread(RemoteStateS2C::handle).add();
        CHANNEL.messageBuilder(RemoteActionC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoteActionC2S::encode).decoder(RemoteActionC2S::decode)
                .consumerMainThread(RemoteActionC2S::handle).add();
        CHANNEL.messageBuilder(RemoteFunctionC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RemoteFunctionC2S::encode).decoder(RemoteFunctionC2S::decode)
                .consumerMainThread(RemoteFunctionC2S::handle).add();
        CHANNEL.messageBuilder(NukeS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NukeS2C::encode).decoder(NukeS2C::decode)
                .consumerMainThread(NukeS2C::handle).add();
        CHANNEL.messageBuilder(SkyShipBlocksC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SkyShipBlocksC2S::encode).decoder(SkyShipBlocksC2S::decode)
                .consumerMainThread(SkyShipBlocksC2S::handle).add();
        CHANNEL.messageBuilder(SkyShipBlocksS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SkyShipBlocksS2C::encode).decoder(SkyShipBlocksS2C::decode)
                .consumerMainThread(SkyShipBlocksS2C::handle).add();
        CHANNEL.messageBuilder(LaunchTrackS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LaunchTrackS2C::encode).decoder(LaunchTrackS2C::decode)
                .consumerMainThread(LaunchTrackS2C::handle).add();
        CHANNEL.messageBuilder(RenameShipC2S.class, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RenameShipC2S::encode).decoder(RenameShipC2S::decode)
                .consumerMainThread(RenameShipC2S::handle).add();
        CHANNEL.messageBuilder(LandmarksS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LandmarksS2C::encode).decoder(LandmarksS2C::decode)
                .consumerMainThread(LandmarksS2C::handle).add();
        CHANNEL.messageBuilder(AcheronS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AcheronS2C::encode).decoder(AcheronS2C::decode)
                .consumerMainThread(AcheronS2C::handle).add();
        CHANNEL.messageBuilder(WeatherS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WeatherS2C::encode).decoder(WeatherS2C::decode)
                .consumerMainThread(WeatherS2C::handle).add();
        CHANNEL.messageBuilder(NukeHistoryS2C.class, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NukeHistoryS2C::encode).decoder(NukeHistoryS2C::decode)
                .consumerMainThread(NukeHistoryS2C::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(message, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToAll(Object message) {
        CHANNEL.send(message, PacketDistributor.ALL.noArg());
    }

    public static void sendToServer(Object message) {
        CHANNEL.send(message, PacketDistributor.SERVER.noArg());
    }
}
