package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.block.PilotSeatBlock;
import com.thepearlking.stevespace.block.RocketEngineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Everything flight needs to know about a vessel's blocks: mass, centre of mass, engines,
 * seats, and the outer blocks used for collision. Positions are in the vessel's own block
 * coordinates (relative to its origin block); a block's centre is at {@code pos + 0.5}.
 */
public final class VesselStats {

    /** An engine: where its nozzle exit is, which way it pushes, and how hard. */
    public record Engine(Vector3d nozzleExit, Vector3d thrustDirection, Direction nozzleFacing,
                         double thrust, double exhaustVelocity) {
    }

    /** A seat: its block and which way the person sitting in it faces. */
    public record Seat(BlockPos pos, Direction facing, boolean pilot) {
    }

    /** A docking port: its block and which way its collar points. */
    public record Port(BlockPos pos, Direction facing) {
    }

    /** An RCS thruster: its block and which way it sticks out from the hull. */
    public record Rcs(BlockPos pos, Direction facing) {
    }

    private final double dryMass;
    private final double propellantCapacity;
    private final Vector3d centerOfMass;
    private final List<Engine> engines;
    private final List<Seat> seats;
    private final List<Port> ports;
    private final List<Rcs> rcs;
    private final List<Vector3d> hullPoints;
    private final double radius;
    private final double crossSection;
    private final int blockCount;

    public VesselStats(VesselStructure structure) {
        double mass = 0;
        double capacity = 0;
        Vector3d weighted = new Vector3d();
        List<Engine> engineList = new ArrayList<>();
        List<Seat> seatList = new ArrayList<>();
        List<Port> portList = new ArrayList<>();
        List<Rcs> rcsList = new ArrayList<>();
        List<Vector3d> hull = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : structure.blocks().entrySet()) {
            BlockPos p = entry.getKey();
            BlockState state = entry.getValue();
            double m = VesselParts.massOf(state);
            double cap = VesselParts.propellantCapacity(state);
            mass += m;
            capacity += cap;
            // Centre of mass with full tanks (where the vessel balances on the pad).
            weighted.add((p.getX() + 0.5) * (m + cap), (p.getY() + 0.5) * (m + cap), (p.getZ() + 0.5) * (m + cap));

            VesselParts.EngineSpec spec = VesselParts.engine(state);
            if (spec != null && state.hasProperty(RocketEngineBlock.FACING)) {
                Direction nozzle = state.getValue(RocketEngineBlock.FACING);
                Vector3d exit = new Vector3d(p.getX() + 0.5 + nozzle.getStepX() * 0.5,
                        p.getY() + 0.5 + nozzle.getStepY() * 0.5, p.getZ() + 0.5 + nozzle.getStepZ() * 0.5);
                Vector3d push = new Vector3d(-nozzle.getStepX(), -nozzle.getStepY(), -nozzle.getStepZ());
                engineList.add(new Engine(exit, push, nozzle, spec.thrust(), spec.exhaustVelocity()));
            }
            if (state.getBlock() instanceof com.thepearlking.stevespace.block.SeatBlock) {
                seatList.add(new Seat(p, state.getValue(PilotSeatBlock.FACING), state.getBlock() instanceof PilotSeatBlock));
            }
            if (state.getBlock() instanceof com.thepearlking.stevespace.block.DockingPortBlock) {
                portList.add(new Port(p, state.getValue(com.thepearlking.stevespace.block.DockingPortBlock.FACING)));
            }
            if (state.getBlock() instanceof com.thepearlking.stevespace.block.RcsThrusterBlock) {
                rcsList.add(new Rcs(p, state.getValue(com.thepearlking.stevespace.block.RcsThrusterBlock.FACING)));
            }
            if (isExposed(structure, p)) {
                hull.add(new Vector3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
            }
        }
        double total = Math.max(1e-6, mass + capacity);
        this.centerOfMass = weighted.div(total);
        this.dryMass = mass;
        this.propellantCapacity = capacity;
        this.engines = Collections.unmodifiableList(engineList);
        // The seat the vessel was launched from (its origin) is the pilot's: always first.
        // The seat it was launched from first, then other pilot seats, then the passengers'.
        seatList.sort(java.util.Comparator.comparingInt(seat -> seat.pos().equals(BlockPos.ZERO) ? 0 : seat.pilot() ? 1 : 2));
        this.rcs = Collections.unmodifiableList(rcsList);
        this.seats = Collections.unmodifiableList(seatList);
        this.ports = Collections.unmodifiableList(portList);
        this.hullPoints = Collections.unmodifiableList(hull);
        double r = 0;
        for (BlockPos p : structure.blocks().keySet()) {
            r = Math.max(r, new Vector3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5).distance(centerOfMass) + 0.87);
        }
        this.radius = r;
        this.blockCount = structure.size();
        this.crossSection = Math.pow(blockCount, 2.0 / 3.0);
    }

    private static boolean isExposed(VesselStructure structure, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (structure.get(p.relative(d)) == null) {
                return true;
            }
        }
        return false;
    }

    public double dryMass() {
        return dryMass;
    }

    public double propellantCapacity() {
        return propellantCapacity;
    }

    public Vector3d centerOfMass() {
        return new Vector3d(centerOfMass);
    }

    public List<Engine> engines() {
        return engines;
    }

    public List<Seat> seats() {
        return seats;
    }

    public List<Port> ports() {
        return ports;
    }

    public List<Rcs> rcs() {
        return rcs;
    }

    /** Push of all the RCS thrusters together, in any one direction (kN). */
    public double rcsThrust() {
        return rcs.size() * VesselParts.RCS_THRUST;
    }

    public List<Vector3d> hullPoints() {
        return hullPoints;
    }

    /** Distance from the centre of mass to the farthest block corner. */
    public double radius() {
        return radius;
    }

    /** Rough frontal area for drag, in blocks². */
    public double crossSection() {
        return crossSection;
    }

    public int blockCount() {
        return blockCount;
    }

    /** Sum of all engines' thrust vectors (vessel axes), in kN at full throttle. */
    public Vector3d thrustVector() {
        Vector3d sum = new Vector3d();
        for (Engine e : engines) {
            sum.add(new Vector3d(e.thrustDirection()).mul(e.thrust()));
        }
        return sum;
    }

    /** Thrust-weighted average exhaust velocity of the engines (blocks/s). */
    public double exhaustVelocity() {
        double thrust = 0;
        double flow = 0;
        for (Engine e : engines) {
            thrust += e.thrust();
            flow += e.thrust() / e.exhaustVelocity();
        }
        return flow > 0 ? thrust / flow : 0;
    }

    /** Change in velocity the vessel can make with {@code propellant} tonnes on board (b/s). */
    public double deltaV(double propellant, double efficiency) {
        double ve = exhaustVelocity() * efficiency;
        if (ve <= 0 || dryMass <= 0) {
            return 0;
        }
        return ve * Math.log((dryMass + propellant) / dryMass);
    }
}
