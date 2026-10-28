package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * A check of a vessel standing in the world: its numbers, and anything stopping it from flying.
 * Shown by the flight computer and used to decide whether a launch may go ahead.
 *
 * @param problems translation keys of launch-blocking problems (empty = ready)
 */
public record VesselReport(BlockPos origin, int blocks, double dryMass, double propellant, double capacity,
                           double thrust, int engines, int seats, double localGravity, double deltaV,
                           List<String> problems) {

    public double totalMass() {
        return dryMass + propellant;
    }

    /** Thrust-to-weight ratio at full throttle on this body (above 1 lifts off). */
    public double thrustToWeight() {
        return totalMass() > 0 && localGravity > 0 ? thrust / (totalMass() * localGravity) : 0;
    }

    public boolean ready() {
        return problems.isEmpty();
    }

    /** Scans the vessel connected to {@code origin} and checks it. */
    public static VesselReport analyze(ServerLevel level, BlockPos origin) {
        VesselAssembler.Result scan = VesselAssembler.scan(level, origin, SpaceConfig.MAX_VESSEL_BLOCKS.get());
        CelestialBody body = ServerUniverse.bodyOf(level);
        double gravity = (body != null ? body.surfaceGravity() : 9.81) * SpaceConfig.GRAVITY_SCALE.get();
        if (!scan.ok()) {
            return new VesselReport(origin, 0, 0, 0, 0, 0, 0, 0, gravity, 0, List.of(scan.error()));
        }
        VesselStructure structure = scan.structure();
        VesselStats stats = new VesselStats(structure);
        double propellant = 0;
        for (CompoundTag tag : structure.blockEntities().values()) {
            propellant += tag.getDouble("propellant");
        }
        double thrust = stats.thrustVector().length() * SpaceConfig.THRUST_SCALE.get();
        double deltaV = stats.deltaV(propellant, SpaceConfig.FUEL_EFFICIENCY.get());
        List<String> problems = new ArrayList<>();
        if (stats.seats().stream().noneMatch(VesselStats.Seat::pilot)) {
            problems.add("stevespace.vessel.problem.no_seat");
        }
        if (stats.engines().isEmpty()) {
            problems.add("stevespace.vessel.problem.no_engines");
        }
        VesselReport report = new VesselReport(origin, stats.blockCount(), stats.dryMass(), propellant,
                stats.propellantCapacity(), thrust, stats.engines().size(), stats.seats().size(), gravity, deltaV, problems);
        boolean inSpace = com.thepearlking.stevespace.world.SpaceDimension.isSpace(level);
        if (!inSpace && !stats.engines().isEmpty() && report.thrustToWeight() < 1) {
            problems.add("stevespace.vessel.problem.too_heavy");
        }
        // Something that isn't part of the ship resting on top of it would hold it down.
        for (BlockPos rel : inSpace ? java.util.Set.<BlockPos>of() : structure.blocks().keySet()) {
            BlockPos above = rel.above();
            if (structure.blocks().containsKey(above)) {
                continue;
            }
            BlockPos world = origin.offset(above);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(world);
            if (!state.isAir() && !state.getCollisionShape(level, world).isEmpty()) {
                problems.add("stevespace.vessel.problem.blocked|" + state.getBlock().getDescriptionId());
                break;
            }
        }
        return report;
    }

    /** A problem as text for the pilot: a translation key, optionally "key|block" naming a block. */
    public static net.minecraft.network.chat.Component describe(String problem) {
        int bar = problem.indexOf('|');
        if (bar < 0) {
            return net.minecraft.network.chat.Component.translatable(problem);
        }
        return net.minecraft.network.chat.Component.translatable(problem.substring(0, bar),
                net.minecraft.network.chat.Component.translatable(problem.substring(bar + 1)));
    }

    /** Whether launching needs propellant: not when the pilot has creative infinite fuel. */
    public boolean needsPropellant(boolean creativePilot) {
        return !(creativePilot && SpaceConfig.CREATIVE_INFINITE_FUEL.get());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(origin);
        buf.writeVarInt(blocks);
        buf.writeDouble(dryMass);
        buf.writeDouble(propellant);
        buf.writeDouble(capacity);
        buf.writeDouble(thrust);
        buf.writeVarInt(engines);
        buf.writeVarInt(seats);
        buf.writeDouble(localGravity);
        buf.writeDouble(deltaV);
        buf.writeVarInt(problems.size());
        problems.forEach(buf::writeUtf);
    }

    public static VesselReport read(FriendlyByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        int blocks = buf.readVarInt();
        double dry = buf.readDouble();
        double propellant = buf.readDouble();
        double capacity = buf.readDouble();
        double thrust = buf.readDouble();
        int engines = buf.readVarInt();
        int seats = buf.readVarInt();
        double gravity = buf.readDouble();
        double deltaV = buf.readDouble();
        int count = buf.readVarInt();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            problems.add(buf.readUtf());
        }
        return new VesselReport(origin, blocks, dry, propellant, capacity, thrust, engines, seats, gravity, deltaV, problems);
    }
}
