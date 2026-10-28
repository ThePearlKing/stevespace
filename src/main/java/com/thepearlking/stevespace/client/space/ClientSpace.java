package com.thepearlking.stevespace.client.space;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.network.VesselOrbitS2C;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.SpaceView;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * What the client knows about the orbit of the vessel the player is in: kept on rails between the
 * server's updates so the view moves smoothly. Also hides the loading screen when a vessel
 * crosses between a planet and space.
 */
public final class ClientSpace {

    /** How long after a transfer notice a loading screen is still suppressed (ms). */
    private static final long TRANSFER_WINDOW = 10_000;

    private static VesselOrbitS2C last;
    private static OrbitalElements elements;
    private static long transferExpected;

    private ClientSpace() {
    }

    public static void receive(VesselOrbitS2C message) {
        last = message;
        elements = null;
    }

    /** The orbit of the cell the player walks around in (parked ship, spacewalk), and when it came. */
    private static com.thepearlking.stevespace.network.CellOrbitS2C cell;
    private static long cellReceived;

    public static void receiveCell(com.thepearlking.stevespace.network.CellOrbitS2C message) {
        cell = message;
        cellReceived = System.currentTimeMillis();
    }

    /** The orbit of the cell the player is in (aboard a parked ship), or null. */
    public static com.thepearlking.stevespace.network.CellOrbitS2C cellOrbit() {
        Minecraft mc = Minecraft.getInstance();
        if (cell == null || System.currentTimeMillis() - cellReceived > 5000 || mc.level == null || !SpaceDimension.isSpace(mc.level)) {
            return null;
        }
        return cell;
    }

    /** The view from the cell the player is walking around in, or null when not in one. */
    private static View cellView(Camera camera) {
        return cellViewAt(camera.getPosition());
    }

    private static View cellViewAt(Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        if (cell == null || System.currentTimeMillis() - cellReceived > 5000 || mc.level == null || !SpaceDimension.isSpace(mc.level)) {
            return null;
        }
        SolarSystem system = ClientUniverse.system();
        CelestialBody body = system.body(cell.body());
        if (body == null) {
            return null;
        }
        double t = ClientUniverse.time();
        double mu = body.mu() * gravityScale();
        double[] s = cell.state();
        StateVector at = Kepler.elementsFromState(new StateVector(new DVec3(s[0], s[1], s[2]), new DVec3(s[3], s[4], s[5])),
                mu, cell.time()).stateAt(t, mu);
        DVec3[] axes = com.thepearlking.stevespace.vessel.OrbitalFlight.localAxes(system, body, at, t);
        Vec3 offset = eye.subtract(cell.anchorX(), cell.anchorY(), cell.anchorZ());
        DVec3 position = system.absolutePosition(body, t).add(at.position())
                .add(axes[0].scale(offset.x)).add(axes[1].scale(offset.y)).add(axes[2].scale(offset.z));
        return new View(body, new SpaceView(position, axes[0], axes[1], axes[2]));
    }

    public static void expectSeamlessTransfer() {
        transferExpected = System.currentTimeMillis();
    }

    /** True while the vessel is high in the air (map mode) rather than in orbit. */
    public static boolean mapMode() {
        return last != null && last.mapMode();
    }

    /** In map mode: where over the map the vessel is now (blocks), carried on from the last update. */
    public static Vec3 mapPosition() {
        double[] s = last.state();
        double dt = Math.max(0, Math.min(0.5, ClientUniverse.time() - last.time()));
        CelestialBody body = body();
        double eastScale = 1;
        if (body != null) {
            eastScale = 1 / Math.max(0.05, Math.cos(new com.thepearlking.stevespace.world.planet.PlanetGeometry(body.radius()).latitude(s[2])));
        }
        return new Vec3(s[0] + s[3] * eastScale * dt, s[1] + s[4] * dt, s[2] + s[5] * dt);
    }

    /**
     * Where over its body's map something aboard the vessel is: the real position on the surface,
     * the vessel's map position plus the offset from the vessel in map mode.
     */
    public static Vec3 flightPosition(VesselEntity vessel, Vec3 worldPosition, float partialTick) {
        if (mapModeActive() && vessel.getId() == last.entityId()) {
            return mapPosition().add(worldPosition.subtract(vessel.getPosition(partialTick)));
        }
        return worldPosition;
    }

    /** True while the local player is aboard a vessel in map mode. */
    public static boolean mapModeActive() {
        return active() && last.mapMode();
    }

    /** True while the local player is aboard a vessel in orbit. */
    public static boolean orbitActive() {
        return active() && !last.mapMode();
    }

    /** The vessel's state relative to its body at universe time {@code t}, or null. */
    public static StateVector stateAt(double t) {
        if (last == null) {
            return null;
        }
        if (last.mapMode()) {
            CelestialBody body = body();
            if (body == null) {
                return null;
            }
            Vec3 p = mapPosition();
            double[] s = last.state();
            return com.thepearlking.stevespace.universe.SurfaceFrame.toOrbit(ClientUniverse.system(), body, p.x, p.y, p.z,
                    s[3], s[4], s[5], t);
        }
        CelestialBody body = body();
        StateVector state = new StateVector(new DVec3(last.state()[0], last.state()[1], last.state()[2]),
                new DVec3(last.state()[3], last.state()[4], last.state()[5]));
        if (body == null) {
            return state;
        }
        double mu = body.mu() * gravityScale();
        if (last.coasting()) {
            if (elements == null) {
                elements = Kepler.elementsFromState(state, mu, last.time());
            }
            return elements.stateAt(t, mu);
        }
        // Burning: carry on in a straight line until the next update (one tick away).
        double dt = t - last.time();
        return new StateVector(state.position().add(state.velocity().scale(dt)), state.velocity());
    }

    /** The same for one particular vessel: only if it is the one the player is flying in orbit. */
    public static double[] axesOf(VesselEntity vessel) {
        return last != null && vessel.getId() == last.entityId() ? axes() : null;
    }

    /** The vessel cell's axes in the ecliptic frame (x, y, z rows) while in orbit, or null. */
    public static double[] axes() {
        if (!orbitActive()) {
            return null;
        }
        double t = ClientUniverse.time();
        StateVector state = stateAt(t);
        CelestialBody body = body();
        if (state == null || body == null) {
            return last.axes();
        }
        DVec3[] a = com.thepearlking.stevespace.vessel.OrbitalFlight.localAxes(ClientUniverse.system(), body, state, t);
        return new double[]{a[0].x(), a[0].y(), a[0].z(), a[1].x(), a[1].y(), a[1].z(), a[2].x(), a[2].y(), a[2].z()};
    }

    public static CelestialBody body() {
        return last == null ? null : ClientUniverse.system().body(last.body());
    }

    public static OrbitalElements orbit() {
        StateVector state = stateAt(ClientUniverse.time());
        CelestialBody body = body();
        if (state == null || body == null) {
            return null;
        }
        return Kepler.elementsFromState(state, body.mu() * gravityScale(), ClientUniverse.time());
    }

    /** True while the local player is aboard a vessel in space with a known orbit. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return last != null && mc.level != null && SpaceDimension.isSpace(mc.level)
                && mc.player != null && VesselEntity.shipOf(mc.player) instanceof VesselEntity vessel && vessel.getId() == last.entityId();
    }

    public record View(CelestialBody body, SpaceView view) {
    }

    /** The camera's place in the solar system while in orbit, or null if unknown. */
    public static View view(Camera camera) {
        return viewAt(camera.getPosition(), camera.getPartialTickTime());
    }

    /** The same for any point (a telescope's eyepiece), in the world's own axes. */
    public static View viewAt(Vec3 eye, float partialTick) {
        if (!orbitActive()) {
            return cellViewAt(eye);
        }
        Minecraft mc = Minecraft.getInstance();
        VesselEntity vessel = VesselEntity.shipOf(mc.player);
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        StateVector state = stateAt(t);
        CelestialBody body = body();
        // The cell's east/up/south, worked out for this very moment (the same way the server
        // does). Taking them from the last update instead froze them between updates, and the
        // whole sky stuttered 20 times a second (badly through a telescope, worse in time warp).
        DVec3[] axes = com.thepearlking.stevespace.vessel.OrbitalFlight.localAxes(system, body, state, t);
        DVec3 x = axes[0];
        DVec3 y = axes[1];
        DVec3 z = axes[2];
        Vec3 offset = eye.subtract(vessel.getPosition(partialTick));
        DVec3 position = system.absolutePosition(body, t).add(state.position())
                .add(x.scale(offset.x)).add(y.scale(offset.y)).add(z.scale(offset.z));
        return new View(body, new SpaceView(position, x, y, z));
    }

    private static double gravityScale() {
        try {
            return SpaceConfig.GRAVITY_SCALE.get();
        } catch (IllegalStateException e) {
            return 1;
        }
    }

    /** Crossing into or out of space happens without a loading screen. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof ReceivingLevelScreen
                && System.currentTimeMillis() - transferExpected < TRANSFER_WINDOW) {
            event.setCanceled(true);
        }
    }
}
