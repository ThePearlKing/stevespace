package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.network.SeamlessTransferS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.universe.SurfaceFrame;
import com.thepearlking.stevespace.world.SpaceCells;
import com.thepearlking.stevespace.world.SpaceDimension;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A vessel flies in one of three ways, and this class moves it between them without anything
 * visible happening:
 * <ul>
 *   <li><b>Surface</b>: low down, a normal entity in the planet's dimension, among real blocks.</li>
 *   <li><b>Map</b>: high in the air. The world's chunks no longer matter up there (the planet is
 *       drawn as a sphere), and a fast vessel would outrun them, so it waits in its own cell of
 *       the space dimension while flying a position over the planet's map with the same physics.</li>
 *   <li><b>Orbit</b>: above the atmosphere, on a Kepler orbit, in the same cell.</li>
 * </ul>
 */
public final class VesselTransfer {

    /** Altitude above which a vessel leaves the world for map mode. */
    public static final double SURFACE_CEILING = 700;
    /** Altitude below which a vessel in map mode comes back into the world. */
    public static final double SURFACE_ENTRY = 600;
    /** Altitude gap between going up into orbit and coming back into the air. */
    private static final double HYSTERESIS = 150;

    private VesselTransfer() {
    }

    /** Altitude above which a vessel leaves the world for map mode, on this body (lower on tiny moons). */
    public static double surfaceCeiling(CelestialBody body) {
        return body == null ? SURFACE_CEILING : Math.min(SURFACE_CEILING, spaceAltitude(body) * 0.8);
    }

    /** Altitude below which a vessel in map mode comes back into the world, on this body. */
    public static double surfaceEntry(CelestialBody body) {
        return body == null ? SURFACE_ENTRY : Math.min(SURFACE_ENTRY, spaceAltitude(body) * 0.7);
    }

    /** Altitude above which a vessel is in orbit. */
    public static double spaceAltitude(CelestialBody body) {
        if (body.hasAtmosphere()) {
            return body.atmosphere().height();
        }
        // Airless: 1500 up, but always well inside the body's own pull (a tiny moon close to its
        // planet, like Enceladus, only holds things within a couple of hundred kilometres).
        double soi = com.thepearlking.stevespace.universe.ServerUniverse.system().sphereOfInfluence(body);
        return Math.min(1500, Math.max(200, (soi - body.radius()) * 0.5));
    }

    /** Altitude below which a vessel in orbit is back in the air. */
    public static double entryAltitude(CelestialBody body) {
        double space = spaceAltitude(body);
        return space - Math.min(HYSTERESIS, space * 0.25);
    }

    // ---------------------------------------------------------------------------------------
    // Surface <-> map (dimension change)
    // ---------------------------------------------------------------------------------------

    /** Climbing out of the world: continues in map mode from its own space cell. */
    public static void surfaceToMap(VesselEntity vessel, CelestialBody body) {
        ServerLevel from = (ServerLevel) vessel.level();
        ServerLevel space = from.getServer().getLevel(SpaceDimension.KEY);
        if (space == null) {
            return;
        }
        SpaceCells cells = SpaceCells.get(from.getServer());
        Vec3 centre = SpaceDimension.cellCenter(cells.peek());
        VesselEntity.loadPathAhead(space, null, centre.x, centre.z, 0, 0);
        if (!VesselEntity.isReady(space, (int) Math.floor(centre.x) >> 4, (int) Math.floor(centre.z) >> 4)) {
            return; // its cell is still loading; try again next tick
        }
        int cell = cells.allocate();
        vessel.enterMapMode(body, new Vector3d(vessel.getX(), vessel.getY(), vessel.getZ()), cell);
        transfer(vessel, space, centre);
    }

    /** Coming down below the map-mode floor: back into the world, once the ground there is loaded. */
    public static void mapToSurface(VesselEntity vessel) {
        ServerLevel space = (ServerLevel) vessel.level();
        CelestialBody body = vessel.flightBody();
        ServerLevel surface = surfaceLevel(space, body);
        if (surface == null) {
            return;
        }
        double x = vessel.mapX();
        double y = vessel.mapY();
        double z = vessel.mapZ();
        DVec3 v = vessel.surfaceVelocity();
        VesselEntity.loadPathAhead(surface, body, x, z, v.x(), v.z());
        if (!VesselEntity.isReady(surface, (int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) {
            return; // the ground below is still loading; keep flying in map mode a moment longer
        }
        vessel.dematerialize(true); // the blocks lift out of the cell and fly down, everyone aboard
        SpaceCells.get(space.getServer()).free(vessel.mapCell());
        vessel.leaveMapMode();
        transfer(vessel, surface, new Vec3(x, y, z));
    }

    /**
     * The highest the ground can be under a point of the map (y), for knowing how far a vessel
     * can keep falling in map mode while the ground below is still loading.
     */
    public static double groundBelow(CelestialBody body, double x, double z) {
        var terrain = body == null ? null : com.thepearlking.stevespace.world.gen.PlanetBodies.terrain(body.id());
        if (terrain == null) {
            return 260; // Earth's mountains, at most
        }
        var sample = new com.thepearlking.stevespace.world.planet.TerrainSample();
        terrain.sample(x, z, sample);
        return Math.max(sample.topOfIce(), terrain.seaLevel());
    }

    /** On the way down in map mode: start loading the ground where the vessel will arrive. */
    public static void prepareSurface(VesselEntity vessel, CelestialBody body, Vector3d position, Vector3d velocity) {
        if (vessel.tickCount % 10 != 0) {
            return;
        }
        double above = position.y - 63 - surfaceEntry(body);
        double seconds = above / Math.max(1, -velocity.y);
        if (seconds > 30) {
            return;
        }
        ServerLevel surface = surfaceLevel((ServerLevel) vessel.level(), body);
        if (surface == null) {
            return;
        }
        double eastScale = 1 / Math.max(0.05, Math.cos(new PlanetGeometry(body.radius()).latitude(position.z)));
        double x = position.x + velocity.x * eastScale * seconds;
        double z = position.z + velocity.z * seconds;
        VesselEntity.loadPathAhead(surface, body, x, z, velocity.x, velocity.z);
    }

    // ---------------------------------------------------------------------------------------
    // Map <-> orbit (same cell, no dimension change)
    // ---------------------------------------------------------------------------------------

    /** Leaving the atmosphere: continues on a Kepler orbit. The cell's axes freeze here. */
    public static void mapToOrbit(VesselEntity vessel) {
        ServerLevel level = (ServerLevel) vessel.level();
        CelestialBody body = vessel.flightBody();
        SolarSystem system = ServerUniverse.system();
        double t = ServerUniverse.time(level.getServer());
        DVec3 v = vessel.surfaceVelocity();
        StateVector state = SurfaceFrame.toOrbit(system, body, vessel.mapX(), vessel.mapY(), vessel.mapZ(), v.x(), v.y(), v.z(), t);
        // In orbit the cell's axes are still the local east/up/south, so the orientation carries over as it is.
        vessel.enterSpace(new OrbitalFlight(system, body, state, t, vessel.mapCell()));
    }

    /** Falling back into the atmosphere: continues in map mode. Returns false if there's no surface. */
    public static boolean orbitToMap(VesselEntity vessel, OrbitalFlight orbital) {
        CelestialBody body = orbital.body();
        if (surfaceLevel((ServerLevel) vessel.level(), body) == null && !com.thepearlking.stevespace.universe.GiantAir.has(body)) {
            return false; // no ground to come down onto (a world not made yet)
        }
        if (orbital.anchor() != null && !Rendezvous.detach((ServerLevel) vessel.level(), vessel)) {
            return false; // sharing another vessel's cell: take a cell of its own first
        }
        SolarSystem system = ServerUniverse.system();
        SurfaceFrame.MapState map = SurfaceFrame.toMap(system, body, orbital.state(), orbital.time());
        // Re-express the orientation: from the cell's axes to the local east/up/south here.
        SurfaceFrame.Local local = map.local();
        Matrix3d cellToLocal = new Matrix3d(
                local.east().dot(orbital.axisX()), local.up().dot(orbital.axisX()), local.south().dot(orbital.axisX()),
                local.east().dot(orbital.axisY()), local.up().dot(orbital.axisY()), local.south().dot(orbital.axisY()),
                local.east().dot(orbital.axisZ()), local.up().dot(orbital.axisZ()), local.south().dot(orbital.axisZ()));
        Quaterniond rotation = new Quaterniond().setFromNormalized(cellToLocal);
        vessel.enterAtmosphere(body, new Vector3d(map.x(), map.y(), map.z()), new DVec3(map.east(), map.up(), map.south()), rotation);
        return true;
    }

    // ---------------------------------------------------------------------------------------

    private static ServerLevel surfaceLevel(ServerLevel any, CelestialBody body) {
        if (body == null || body.surfaceDimension() == null) {
            return null;
        }
        return any.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(body.surfaceDimension())));
    }

    /** Changes dimension with the crew aboard, telling their clients to skip the loading screen. */
    private static void transfer(VesselEntity vessel, ServerLevel to, Vec3 position) {
        for (Entity passenger : vessel.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                SpaceNetwork.sendTo(player, new SeamlessTransferS2C());
            }
        }
        vessel.beginTransfer();
        vessel.changeDimension(new DimensionTransition(to, position, Vec3.ZERO, vessel.getYRot(), vessel.getXRot(),
                DimensionTransition.DO_NOTHING));
    }
}
