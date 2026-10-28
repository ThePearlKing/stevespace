package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.world.SpaceCells;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Vessels meeting in orbit. Each vessel in space normally has a cell of its own; when two come
 * close they share one, placed by their real relative orbits, so their crews can see each other,
 * fly alongside and dock. When they drift apart again, the visitor gets a cell of its own back.
 */
public final class Rendezvous {

    /** Closer than this (blocks), a vessel joins the other's cell. */
    static final double JOIN = 180;
    /** Farther than this, it leaves again. */
    static final double LEAVE = 260;
    /** Docking ports must meet within this distance (blocks) and this closing speed (m/s). */
    static final double DOCK_DISTANCE = 0.8;
    static final double DOCK_SPEED = 1.0;

    private Rendezvous() {
    }

    /** Server tick, after every vessel has moved. */
    public static void tick(MinecraftServer server) {
        ServerLevel space = server.getLevel(SpaceDimension.KEY);
        if (space == null) {
            return;
        }
        List<VesselEntity> orbiting = new ArrayList<>();
        for (VesselEntity v : VesselEntity.serverVessels()) {
            if (!v.isRemoved() && v.orbital() != null && v.level() == space) {
                orbiting.add(v);
            }
        }
        if (server.getTickCount() % 10 == 0) {
            wakeParked(space, orbiting);
        }
        // Visitors follow the vessel whose cell they share, or leave when it's gone or far.
        for (VesselEntity v : orbiting) {
            OrbitalFlight o = v.orbital();
            if (o.anchor() == null) {
                continue;
            }
            VesselEntity anchor = find(orbiting, o.anchor());
            if (anchor == null || anchor.orbital().anchor() != null || !anchor.orbital().body().id().equals(o.body().id())
                    || separation(v, anchor) > LEAVE) {
                detach(space, v);
            } else {
                place(v, anchor);
            }
        }
        // Newcomers: a vessel alone that comes close to another joins its cell.
        for (VesselEntity v : orbiting) {
            if (v.orbital().anchor() != null || hasGuests(orbiting, v)) {
                continue;
            }
            for (VesselEntity other : orbiting) {
                if (other == v || other.orbital().anchor() != null || !other.orbital().body().id().equals(v.orbital().body().id())) {
                    continue;
                }
                if (hosts(other, v) && separation(v, other) < JOIN) {
                    join(space, v, other);
                    break;
                }
            }
        }
        // Docking: ports of a visitor and its host meeting face to face, gently.
        for (VesselEntity v : orbiting) {
            if (v.isRemoved() || v.orbital() == null || v.orbital().anchor() == null) {
                continue;
            }
            VesselEntity host = find(orbiting, v.orbital().anchor());
            if (host != null && !host.isRemoved()) {
                tryDock(host, v);
            }
        }
    }

    /**
     * Which of two meeting vessels keeps its cell (the other comes to it): one nobody is flying
     * (a station) over one being flown, then the bigger, then the older.
     */
    private static boolean hosts(VesselEntity a, VesselEntity b) {
        boolean flownA = a.pilot() != null;
        boolean flownB = b.pilot() != null;
        if (flownA != flownB) {
            return !flownA;
        }
        if (a.structure().size() != b.structure().size()) {
            return a.structure().size() > b.structure().size();
        }
        return a.getId() < b.getId();
    }

    /**
     * Whether a vessel has company: guests in its cell, or another vessel close by. A ship with
     * company doesn't park itself (a station stays awake while someone docks with it).
     */
    static boolean busy(VesselEntity host) {
        for (VesselEntity v : VesselEntity.serverVessels()) {
            if (v == host || v.isRemoved() || v.orbital() == null || host.orbital() == null) {
                continue;
            }
            if (host.getUUID().equals(v.orbital().anchor())
                    || v.orbital().body().id().equals(host.orbital().body().id()) && separation(v, host) < LEAVE) {
                return true;
            }
        }
        return false;
    }

    /** Where a parked ship is in its orbit at time {@code t} (relative to its body). */
    static DVec3 parkedPosition(com.thepearlking.stevespace.world.ParkedShips.Parked p, double t) {
        var body = com.thepearlking.stevespace.universe.ServerUniverse.system().body(p.body());
        double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
        return com.thepearlking.stevespace.universe.Kepler.elementsFromState(p.state(), mu, p.time()).stateAt(t, mu).position();
    }

    /**
     * A parked ship (a station) that a flying vessel comes close to turns back into a vessel,
     * with nobody aboard, so the two can share a cell, see each other and dock. It parks itself
     * again once it's alone.
     */
    private static void wakeParked(ServerLevel space, List<VesselEntity> orbiting) {
        var parkedShips = com.thepearlking.stevespace.world.ParkedShips.get(space.getServer());
        if (parkedShips.all().isEmpty()) {
            return;
        }
        for (var p : List.copyOf(parkedShips.all())) {
            for (VesselEntity v : orbiting) {
                if (v.isRemoved() || !p.body().equals(v.orbital().body().id())) {
                    continue;
                }
                double apart = parkedPosition(p, v.orbital().time()).sub(v.orbital().state().position()).length();
                if (apart < JOIN + 60) {
                    wake(space, p);
                    break;
                }
            }
        }
    }

    /**
     * The block a parked ship's blocks are counted from when it flies again: its pilot seat if it
     * has one, else its first block. (Its chunks must be loaded.)
     */
    static net.minecraft.core.BlockPos parkedOrigin(ServerLevel space, com.thepearlking.stevespace.world.ParkedShips.Parked p) {
        net.minecraft.core.BlockPos origin = null;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(p.min(), p.max())) {
            var state = space.getBlockState(pos);
            if (state.getBlock() instanceof com.thepearlking.stevespace.block.PilotSeatBlock) {
                return pos.immutable();
            }
            if (origin == null && !state.isAir()) {
                origin = pos.immutable();
            }
        }
        return origin;
    }

    /** Turns a parked ship back into a vessel (once its blocks' chunks are loaded). */
    static VesselEntity wake(ServerLevel space, com.thepearlking.stevespace.world.ParkedShips.Parked p) {
        boolean ready = true;
        for (int cx = p.min().getX() >> 4; cx <= p.max().getX() >> 4; cx++) {
            for (int cz = p.min().getZ() >> 4; cz <= p.max().getZ() >> 4; cz++) {
                VesselEntity.loadPathAhead(space, null, cx * 16 + 8, cz * 16 + 8, 0, 0);
                ready &= VesselEntity.isReady(space, cx, cz);
            }
        }
        if (!ready) {
            return null;
        }
        net.minecraft.core.BlockPos origin = parkedOrigin(space, p);
        if (origin == null) {
            com.thepearlking.stevespace.world.ParkedShips.get(space.getServer()).remove(p.cell()); // nothing left of it
            return null;
        }
        var scan = VesselAssembler.scan(space, origin, com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get(), false);
        if (!scan.ok()) {
            return null;
        }
        VesselEntity vessel = VesselEntity.unpark(space, origin, scan.structure(), p, null);
        com.thepearlking.stevespace.StevesSpace.LOGGER.info("{} wakes for a visitor (cell {})", vessel.getName().getString(), p.cell());
        return vessel;
    }

    private static VesselEntity find(List<VesselEntity> vessels, java.util.UUID id) {
        for (VesselEntity v : vessels) {
            if (v.getUUID().equals(id) && !v.isRemoved()) {
                return v;
            }
        }
        return null;
    }

    private static boolean hasGuests(List<VesselEntity> vessels, VesselEntity host) {
        for (VesselEntity v : vessels) {
            if (host.getUUID().equals(v.orbital().anchor())) {
                return true;
            }
        }
        return false;
    }

    /** Distance between two vessels' centres of mass (blocks). */
    static double separation(VesselEntity a, VesselEntity b) {
        return offset(a, b).length();
    }

    /**
     * Where {@code v} is relative to {@code host}, both taken at the host's time (one may have
     * moved a tick further than the other).
     */
    private static DVec3 offset(VesselEntity v, VesselEntity host) {
        OrbitalFlight o = v.orbital();
        OrbitalFlight h = host.orbital();
        DVec3 position = o.state().position().add(o.state().velocity().scale(h.time() - o.time()));
        return position.sub(h.state().position());
    }

    /** Where a visitor sits in its host's cell: its real offset, in the host's axes. */
    static Vec3 spot(VesselEntity v, VesselEntity host) {
        OrbitalFlight h = host.orbital();
        DVec3 offset = offset(v, host);
        return new Vec3(host.getX() + offset.dot(h.axisX()), host.getY() + offset.dot(h.axisY()), host.getZ() + offset.dot(h.axisZ()));
    }

    /**
     * Puts the visitor at its spot in the host's cell, once that spot's chunk is loaded (moving an
     * entity into an unloaded chunk would unload it). Returns false while still waiting.
     */
    static boolean place(VesselEntity v, VesselEntity host) {
        Vec3 at = spot(v, host);
        ServerLevel level = (ServerLevel) v.level();
        VesselEntity.loadPathAhead(level, null, at.x, at.z, 0, 0);
        if (!VesselEntity.isReady(level, (int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4)) {
            return false;
        }
        v.setPos(at.x, at.y, at.z);
        return true;
    }

    private static void join(ServerLevel space, VesselEntity v, VesselEntity host) {
        // Wait until the spot in the host's cell is loaded; the visitor stays in its own until then.
        Vec3 at = spot(v, host);
        VesselEntity.loadPathAhead(space, null, at.x, at.z, 0, 0);
        if (!VesselEntity.isReady(space, (int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4)) {
            return;
        }
        v.dematerialize(); // a visitor in another ship's cell flies there as a vessel
        int oldCell = v.orbital().cell();
        v.orbital().setCell(host.orbital().cell(), host.getUUID());
        v.setPos(at.x, at.y, at.z);
        SpaceCells.get(space.getServer()).free(oldCell);
        com.thepearlking.stevespace.StevesSpace.LOGGER.info("Vessel {} joins {}'s cell ({} blocks apart)", v.getId(), host.getId(),
                String.format(java.util.Locale.ROOT, "%.1f", separation(v, host)));
    }

    /** Back to a cell of its own (once that cell is loaded). */
    static boolean detach(ServerLevel space, VesselEntity v) {
        SpaceCells cells = SpaceCells.get(space.getServer());
        Vec3 centre = SpaceDimension.cellCenter(cells.peek());
        VesselEntity.loadPathAhead(space, null, centre.x, centre.z, 0, 0);
        if (!VesselEntity.isReady(space, (int) Math.floor(centre.x) >> 4, (int) Math.floor(centre.z) >> 4)) {
            return false;
        }
        int cell = cells.allocate();
        v.orbital().setCell(cell, null);
        v.teleportTo(centre.x, centre.y, centre.z);
        return true;
    }

    /** Joins the two if a port on each meets the other face to face, slowly enough. */
    private static void tryDock(VesselEntity host, VesselEntity visitor) {
        if (host.dockCooldown > 0 || visitor.dockCooldown > 0) {
            host.dockCooldown = Math.max(0, host.dockCooldown - 1);
            visitor.dockCooldown = Math.max(0, visitor.dockCooldown - 1);
            return; // just undocked: let them drift apart first
        }
        if (host.stats().ports().isEmpty() || visitor.stats().ports().isEmpty()) {
            return;
        }
        DVec3 relative = visitor.orbital().state().velocity().sub(host.orbital().state().velocity());
        if (relative.length() > DOCK_SPEED) {
            return;
        }
        // Measured in fixed space: the visitor is shown in the host's frame, which needn't be its cell's.
        DVec3 lag = visitor.orbital().state().velocity().scale(host.orbital().time() - visitor.orbital().time());
        for (VesselStats.Port a : host.stats().ports()) {
            DVec3 faceA = host.portFaceInertial(a);
            DVec3 dirA = host.portDirectionInertial(a);
            for (VesselStats.Port b : visitor.stats().ports()) {
                DVec3 faceB = visitor.portFaceInertial(b).add(lag);
                DVec3 dirB = visitor.portDirectionInertial(b);
                double gap = faceA.sub(faceB).length();
                if (host.tickCount % 20 == 0 && gap < 5) {
                    com.thepearlking.stevespace.StevesSpace.LOGGER.debug("Ports {}-{}: gap {} alignment {}", host.getId(), visitor.getId(),
                            gap, dirA.dot(dirB));
                }
                if (gap < DOCK_DISTANCE && dirA.dot(dirB) < -0.95) {
                    if (host.absorb(visitor, a, b)) {
                        return;
                    }
                }
            }
        }
    }
}
