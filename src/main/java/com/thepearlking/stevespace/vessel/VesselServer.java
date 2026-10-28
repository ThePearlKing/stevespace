package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.block.PilotSeatBlock;
import com.thepearlking.stevespace.block.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of flying: pilot inputs, launching from a seat, and keeping passengers aboard.
 */
public final class VesselServer {

    private VesselServer() {
    }

    /** Pilot input arrived. Throttling up in a seat of a vessel on the ground launches it. */
    /**
     * Nobody riding a flying ship gets hurt by the ride itself: not squeezed into a block while
     * walking about the cabin, not by the landing, not by falling inside it.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && player.getVehicle() instanceof VesselEntity) {
            var source = event.getSource();
            if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.FALL)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.FLY_INTO_WALL)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)) {
                event.setCanceled(true);
            }
            player.fallDistance = 0;
        }
    }

    public static void onControls(ServerPlayer player, VesselControls controls) {
        // Flying by remote control: the controls go by radio (arriving after the light delay).
        if (com.thepearlking.stevespace.remote.RemoteControl.controls(player, controls)) {
            return;
        }
        applyControls(player, controls);
    }

    /** The controls reaching the craft (directly from its seat, or by radio when they arrive). */
    public static void applyControls(ServerPlayer player, VesselControls controls) {
        Entity vehicle = player.getVehicle();
        // In the pilot seat of a ship that is blocks in space: it flies that ship.
        VesselEntity ship = vehicle instanceof SeatEntity seat ? VesselEntity.materializedAt(player.serverLevel(), seat.blockPosition()) : null;
        if (ship != null) {
            if (ship.pilot() == player) {
                ship.setControls(controls);
            }
            return;
        }
        if (vehicle instanceof VesselEntity vessel) {
            if (vessel.pilot() == player) {
                vessel.setControls(controls);
            }
            return;
        }
        if (vehicle instanceof SeatEntity seat && controls.throttle() > 0) {
            tryLaunch(player, seat.blockPosition(), controls);
        }
    }

    /** The pilot chose a ship (and ports) to dock with: fly there and dock, launching first if still on the pad. */
    public static void onDock(ServerPlayer player, java.util.UUID id, int cell, BlockPos mine, BlockPos theirs) {
        Entity vehicle = player.getVehicle();
        if (VesselEntity.shipOf(player, 2) instanceof VesselEntity vessel) {
            if (vessel.pilot() == player) {
                vessel.setControls(VesselControls.IDLE);
                vessel.startDock(id, cell, mine, theirs);
            }
        } else if (vehicle instanceof SeatEntity seat) {
            VesselEntity vessel = tryLaunch(player, seat.blockPosition(), VesselControls.IDLE);
            if (vessel != null) {
                vessel.startDock(id, cell, mine, theirs);
            }
        }
    }

    /** The pilot chose an autopilot program: engage it, launching first if still on the pad. */
    public static void onAutopilot(ServerPlayer player, Autopilot.Program program) {
        onAutopilot(player, program, "", false);
    }

    /** The same, with a destination for {@link Autopilot.Program#TRAVEL}. */
    public static void onAutopilot(ServerPlayer player, Autopilot.Program program, String destination, boolean land) {
        onAutopilot(player, program, destination, land, Double.NaN, Double.NaN);
    }

    /** The same, landing on a picked spot (latitude, longitude in radians; NaN for anywhere). */
    public static void onAutopilot(ServerPlayer player, Autopilot.Program program, String destination, boolean land,
                                   double latitude, double longitude) {
        Entity vehicle = player.getVehicle();
        if (program == Autopilot.Program.UNDOCK) {
            if (vehicle instanceof VesselEntity vessel && !vessel.undock()) {
                player.displayClientMessage(Component.translatable("stevespace.vessel.nothing_to_undock"), true);
            }
            return;
        }
        if (VesselEntity.shipOf(player, 2) instanceof VesselEntity vessel) {
            if (vessel.pilot() == player) {
                vessel.setControls(VesselControls.IDLE);
                vessel.startAutopilot(program, destination, land, latitude, longitude);
            }
        } else if (vehicle instanceof SeatEntity seat && (program == Autopilot.Program.ORBIT || program == Autopilot.Program.TRAVEL)) {
            VesselEntity vessel = tryLaunch(player, seat.blockPosition(), VesselControls.IDLE);
            if (vessel != null) {
                vessel.startAutopilot(program, destination, land, latitude, longitude);
            }
        }
    }

    private static VesselEntity tryLaunch(ServerPlayer player, BlockPos seatPos, VesselControls controls) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockState(seatPos).getBlock() instanceof PilotSeatBlock)) {
            return null;
        }
        VesselReport report = VesselReport.analyze(level, seatPos);
        if (!report.ready()) {
            player.displayClientMessage(VesselReport.describe(report.problems().get(0)), true);
            return null;
        }
        if (report.propellant() <= 0 && report.needsPropellant(player.getAbilities().instabuild)) {
            player.displayClientMessage(Component.translatable("stevespace.vessel.problem.no_propellant"), true);
            return null;
        }
        VesselAssembler.Result scan = VesselAssembler.scan(level, seatPos, com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get());
        if (!scan.ok()) {
            player.displayClientMessage(Component.translatable(scan.error()), true);
            return null;
        }
        // Other players sitting in this vessel's seats come along.
        List<ServerPlayer> crew = new ArrayList<>();
        VesselStats stats = new VesselStats(scan.structure());
        for (VesselStats.Seat seat : stats.seats()) {
            BlockPos world = seatPos.offset(seat.pos());
            for (SeatEntity entity : level.getEntitiesOfClass(SeatEntity.class, new net.minecraft.world.phys.AABB(world))) {
                for (Entity rider : entity.getPassengers()) {
                    if (rider instanceof ServerPlayer other && other != player) {
                        crew.add(other);
                    }
                }
            }
        }
        VesselEntity vessel;
        if (com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)) {
            // A ship parked in orbit flies off on the orbit its cell has been following.
            var parked = com.thepearlking.stevespace.world.ParkedShips.get(level.getServer()).at(seatPos.getX());
            if (parked == null) {
                return null;
            }
            vessel = VesselEntity.unpark(level, seatPos, scan.structure(), parked, player);
        } else {
            vessel = VesselEntity.launch(level, seatPos, scan.structure(), player);
        }
        vessel.setControls(controls);
        if (!report.needsPropellant(player.getAbilities().instabuild)) {
            vessel.fillTanks();
        }
        for (ServerPlayer member : crew) {
            VesselEntity.leaveGroundSeat(member);
            member.startRiding(vessel, true);
        }
        // Anyone on their feet inside the ship comes along standing (more people than seats).
        if (!vessel.materialized()) {
            for (ServerPlayer other : level.players()) {
                if (other != player && !crew.contains(other) && other.getVehicle() == null && vessel.standsIn(other, seatPos)) {
                    vessel.takeAboardStanding(other, seatPos);
                }
            }
        }
        return vessel;
    }

    /** Pilots sitting in a vessel still on the ground see whether it can fly, and why not. */
    private static void sendPadStatus(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getVehicle() instanceof SeatEntity seat
                    && player.serverLevel().getBlockState(seat.blockPosition()).getBlock() instanceof PilotSeatBlock
                    && VesselEntity.materializedAt(player.serverLevel(), seat.blockPosition()) == null) {
                VesselReport report = VesselReport.analyze(player.serverLevel(), seat.blockPosition());
                com.thepearlking.stevespace.network.SpaceNetwork.sendTo(player, new com.thepearlking.stevespace.network.PadStatusS2C(
                        report, report.needsPropellant(player.getAbilities().instabuild)));
            }
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        VesselEntity.onLeaveLevel(event);
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent.Post event) {
        VesselEntity.tickOutsideTickingChunks(event.getServer());
        if (event.getServer().getTickCount() % 10 == 0) {
            sendPadStatus(event.getServer());
        }
        Rendezvous.tick(event.getServer());
        SpaceCrew.tick(event.getServer());
        Targets.tick(event.getServer());
    }

    /**
     * In pilot seats and flying vessels, sneak is the throttle key, so it must not also get you
     * out: passengers leave only with the leave-seat key (or when the vessel lands or breaks).
     */
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide) {
            return;
        }
        if (event.getEntityBeingMounted() instanceof VesselEntity vessel && !vessel.allowsDismount()) {
            event.setCanceled(true);
        } else if (event.getEntityBeingMounted() instanceof SeatEntity seat && !seat.allowsDismount()
                && event.getLevel().getBlockState(seat.blockPosition()).getBlock() instanceof PilotSeatBlock) {
            event.setCanceled(true);
        }
    }
}
