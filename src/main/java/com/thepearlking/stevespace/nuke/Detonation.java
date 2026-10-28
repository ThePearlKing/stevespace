package com.thepearlking.stevespace.nuke;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.network.NukeS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.remote.RemoteControl;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.vessel.VesselAssembler;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A nuclear device going off. Whatever else happens, the spacecraft it was part of is gone. The
 * rest depends on where: worked out from the world it's in and how high up in that world's air,
 * then handed to the blast (only with destruction on), the aftermath (fallout, a radiation belt),
 * the EMP, and every player's screen (flash, fireball, cloud, aurora, sound).
 */
public final class Detonation {

    private Detonation() {
    }

    /** Set off as a block standing in the world (on the ground, or in a ship's blocks in space). */
    public static void fromWorld(ServerLevel level, BlockPos pos, BlockState state) {
        // Only ever once: if it's already gone (set off a moment ago), nothing more happens.
        if (!SpaceConfig.NUKES.get() || !(level.getBlockState(pos).getBlock() instanceof NuclearDeviceBlock)) {
            return;
        }
        double kt = NuclearDeviceBlock.kilotons(state);
        Vec3 at = Vec3.atCenterOf(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        // The craft it was built into: its blocks (and, flying as blocks in space, the craft itself).
        VesselEntity ship = VesselEntity.materializedAt(level, pos);
        Setting where = setting(level, at, ship);
        List<Entity> crew = new ArrayList<>();
        if (ship != null) {
            crew.addAll(ship.playersAboard(1.5));
            removeShipBlocks(level, ship);
            ship.discard();
        } else {
            removeCraftAround(level, pos);
            forgetParked(level, pos);
        }
        go(level, at, kt, where, crew);
    }

    /** Set off in a craft flying as one piece (its blocks are inside the craft, not in the world). */
    public static void inFlight(VesselEntity vessel, BlockPos relative, BlockState state) {
        if (!SpaceConfig.NUKES.get() || vessel.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) vessel.level();
        if (vessel.materialized()) {
            fromWorld(level, vessel.blockOrigin().offset(relative), state); // its blocks are in the world
            return;
        }
        double kt = NuclearDeviceBlock.kilotons(state);
        Vec3 at = vessel.position();
        // Where it is, worked out while the craft still exists (its orbit goes with it).
        Setting where = setting(level, at, vessel);
        List<Entity> crew = new ArrayList<>(vessel.getPassengers());
        RemoteControl.craftLost(vessel); // remote pilots home first
        crew.removeIf(e -> e.getVehicle() != vessel);
        vessel.ejectAllForDestruction();
        vessel.discard();
        go(level, at, kt, where, crew);
    }

    /** Everything after the craft is gone. */
    private static void go(ServerLevel level, Vec3 at, double kt, Setting where, List<Entity> crew) {
        var server = level.getServer();
        double fireballKm = NuclearEffects.fireballKm(kt);
        NuclearEffects.Regime regime = NuclearEffects.regime(where.heightKm(), where.air(), fireballKm);
        boolean destruction = SpaceConfig.NUKE_DESTRUCTION.get();
        StevesSpace.LOGGER.info("Nuclear detonation: {} kt, {} at {} (height {} km{}), destruction {}", kt, regime,
                level.dimension().location(), String.format("%.1f", where.heightKm()), where.air() ? ", in air" : "", destruction);
        // The crew of the craft: with destruction off they're spared, set adrift where it was.
        for (Entity e : crew) {
            if (destruction) {
                e.hurt(level.damageSources().explosion(null, null), Float.MAX_VALUE);
            } else if (e instanceof ServerPlayer p && where.air()) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60));
            }
        }
        double t = ServerUniverse.time(server);
        if (where.body() != null && where.body().id().equals("acheron")) {
            com.thepearlking.stevespace.world.AcheronEvent.struck(server);
        }
        NuclearAftermath.get(server).record(level, at, kt, regime, where, t);
        if (destruction && (regime == NuclearEffects.Regime.SURFACE || regime == NuclearEffects.Regime.AIR
                || regime == NuclearEffects.Regime.VACUUM) && !SpaceDimension.isSpace(level)) {
            BlastJob.start(level, at, kt, regime, where.groundY());
        }
        if (regime == NuclearEffects.Regime.SPACE && where.body() != null && SpaceConfig.NUKE_EMP.get()) {
            Emp.strike(server, where.body(), where.absolute(), t);
        }
        NukeS2C message = new NukeS2C(level.dimension().location().toString(), at.x, at.y, at.z, where.groundY(), kt, regime.ordinal(),
                where.body() == null ? "" : where.body().id(), where.absolute() == null ? new double[3]
                : new double[]{where.absolute().x(), where.absolute().y(), where.absolute().z()}, where.heightKm(), t);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            SpaceNetwork.sendTo(p, message);
        }
    }

    /**
     * Where it went off: the world's body, how high (in the real kilometres that height stands for:
     * the top of a world's air counts as 100 km), whether there's air there, the ground level
     * below (surface worlds), and its place in the solar system.
     */
    public record Setting(CelestialBody body, double heightKm, boolean air, double groundY, DVec3 absolute) {
    }

    static Setting setting(ServerLevel level, Vec3 at, VesselEntity craft) {
        double t = ServerUniverse.time(level.getServer());
        if (SpaceDimension.isSpace(level)) {
            // In orbit (or a ship's cell): above the air, by definition. Map mode is over a world's ground.
            CelestialBody body = craft != null ? craft.flightBody() : null;
            if (body == null) {
                // A parked ship (blocks with no flying craft): the world it's parked round.
                var parked = com.thepearlking.stevespace.world.ParkedShips.get(level.getServer()).at(at.x);
                body = parked == null ? null : ServerUniverse.system().body(parked.body());
            }
            // A craft out there flying (not parked): where it really is, not where its cell is.
            DVec3 abs = craft != null && body != null && !craft.materialized()
                    ? RemoteControl.vesselPosition(ServerUniverse.system(), craft, body, t)
                    : RemoteControl.absolute(level, at, t);
            if (craft != null && craft.inMapMode() && body != null) {
                double ground = 63;
                double alt = craft.mapY() - ground;
                return airSetting(body, alt, ground, abs);
            }
            // Above the air: how high over the world's surface, in its real kilometres.
            double height = body != null && abs != null
                    ? abs.sub(ServerUniverse.system().absolutePosition(body, t)).length() - body.radius() : 1000;
            return new Setting(body, Math.max(100, height), false, Double.NaN, abs);
        }
        CelestialBody body = ServerUniverse.bodyOf(level);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
        DVec3 abs = RemoteControl.absolute(level, at, t);
        return airSetting(body, Math.max(0, at.y - ground), ground, abs);
    }

    private static Setting airSetting(CelestialBody body, double altitudeBlocks, double ground, DVec3 abs) {
        if (body == null || !body.hasAtmosphere()) {
            return new Setting(body, altitudeBlocks, false, ground, abs);
        }
        double top = Math.max(1, body.atmosphere().height());
        double km = altitudeBlocks / top * 100;
        return new Setting(body, km, altitudeBlocks < top, ground, abs);
    }

    // ---------------------------------------------------------------------------------------
    // The craft it was part of
    // ---------------------------------------------------------------------------------------

    private static void removeShipBlocks(ServerLevel level, VesselEntity ship) {
        for (Map.Entry<BlockPos, BlockState> e : ship.structure().blocks().entrySet()) {
            BlockPos p = ship.blockOrigin().offset(e.getKey());
            level.removeBlockEntity(p);
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    private static boolean isCraftPart(BlockState s) {
        return s.getBlock() instanceof com.thepearlking.stevespace.block.SeatBlock
                || s.getBlock() instanceof com.thepearlking.stevespace.block.RocketEngineBlock;
    }

    /** A device standing in a craft on the ground (on its pad, landed): that craft's blocks go with it. */
    /** A ship parked in orbit with nothing left of it is no longer a ship out there. */
    private static void forgetParked(ServerLevel level, BlockPos pos) {
        if (!com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)) {
            return;
        }
        var parkedShips = com.thepearlking.stevespace.world.ParkedShips.get(level.getServer());
        var parked = parkedShips.at(pos.getX());
        if (parked == null) {
            return;
        }
        for (BlockPos p : BlockPos.betweenClosed(parked.min(), parked.max())) {
            if (!level.getBlockState(p).isAir()) {
                return; // something's still there (the crew's own bits, a second hull)
            }
        }
        parkedShips.remove(parked.cell());
        StevesSpace.LOGGER.info("Parked ship in cell {} destroyed", parked.cell());
    }

    private static void removeCraftAround(ServerLevel level, BlockPos pos) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos start = pos.relative(d);
            if (!VesselAssembler.isVesselBlock(level, start, level.getBlockState(start))) {
                continue;
            }
            var scan = VesselAssembler.scan(level, start, SpaceConfig.MAX_VESSEL_BLOCKS.get(), true);
            // Only a spacecraft (something with controls or an engine), never a house it sat in.
            if (!scan.ok() || scan.structure().blocks().values().stream().noneMatch(Detonation::isCraftPart)) {
                continue;
            }
            for (BlockPos rel : scan.structure().blocks().keySet()) {
                BlockPos p = start.offset(rel);
                level.removeBlockEntity(p);
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }
}
