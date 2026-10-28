package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.LocalSky;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.VanillaTime;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The client's copy of the universe: the solar system plus a smoothly running clock that
 * the server corrects once a second. Also makes the client's day/night brightness follow the
 * real sun at the camera's own latitude and longitude.
 */
public final class ClientUniverse {

    private static SolarSystem system = new SolarSystem(1200);
    private static double baseTime;
    private static long baseNanos;
    private static double warp = 1;
    private static boolean synced;
    /**
     * How fast universe time really runs, per real second: the warp, unless the server can't
     * keep up its 20 ticks a second (it often can't at high warp), measured from its updates.
     * Extrapolating at the full warp ran ahead and was pulled back every second: planets
     * surged and snapped back.
     */
    private static double rate = 1;
    private static double lastServerTime = Double.NaN;
    private static long lastServerNanos;
    /** An error left from the last update, folded in over the next second instead of jumping. */
    private static double correction;

    private ClientUniverse() {
    }

    /** Called when the server's clock arrives. Small differences are absorbed smoothly. */
    public static void receive(double serverTime, double serverWarp, double earthDaySeconds,
                               double anchorTime, double anchorTurns) {
        if (system.earthDaySeconds() != earthDaySeconds || system.earthTurns(anchorTime) != anchorTurns) {
            system = new SolarSystem(earthDaySeconds, anchorTime, anchorTurns);
        }
        double predicted = time();
        long now = System.nanoTime();
        boolean warpChanged = serverWarp != warp;
        warp = serverWarp;
        if (!warpChanged && !Double.isNaN(lastServerTime)) {
            double realSeconds = (now - lastServerNanos) / 1e9;
            if (realSeconds > 0.3 && realSeconds < 5) {
                double measured = (serverTime - lastServerTime) / realSeconds;
                rate += (Math.max(warp * 0.2, Math.min(warp * 1.02, measured)) - rate) * 0.35;
            }
        } else {
            rate = warp;
        }
        lastServerTime = serverTime;
        lastServerNanos = now;
        if (!synced || warpChanged || Math.abs(predicted - serverTime) > 3 * Math.max(1, rate)) {
            baseTime = serverTime;
            correction = 0;
        } else {
            // Carry on smoothly from where we are; make up the difference over the next second.
            baseTime = predicted;
            correction = serverTime - predicted;
        }
        baseNanos = now;
        synced = true;
    }

    public static SolarSystem system() {
        return system;
    }

    public static double warp() {
        return warp;
    }

    public static boolean isSynced() {
        return synced;
    }

    /** Current universe time in seconds, extrapolated between server updates. */
    public static double time() {
        if (!synced) {
            return 0;
        }
        double elapsed = (System.nanoTime() - baseNanos) / 1e9;
        return baseTime + elapsed * rate + Math.min(1, elapsed) * correction;
    }

    /** The body whose surface the client is on (or high above, in a vessel), or null. */
    public static CelestialBody currentBody() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        if (com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)) {
            return com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive()
                    ? com.thepearlking.stevespace.client.space.ClientSpace.body() : null;
        }
        return system.bodyForDimension(level.dimension().location().toString());
    }

    /**
     * The sky as a telescope at {@code eye} sees it, in the axes of the blocks around it: on a
     * planet the local sky; in space the ship's place on its orbit (or over the map, high in the
     * air), turned with the ship when it is blocks (the blocks stay put, the sky turns).
     */
    public static com.thepearlking.stevespace.universe.SkyView telescopeSky(Vec3 eye) {
        double t = time();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)) {
            return skyAt(eye, t);
        }
        var player = Minecraft.getInstance().player;
        var ship = player == null ? null : com.thepearlking.stevespace.vessel.VesselEntity.shipOf(player);
        com.thepearlking.stevespace.universe.SkyView sky;
        if (com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive() && ship != null) {
            sky = skyAt(com.thepearlking.stevespace.client.space.ClientSpace.flightPosition(ship, eye, 1f), t);
        } else {
            var view = com.thepearlking.stevespace.client.space.ClientSpace.viewAt(eye, 1f);
            sky = view == null ? null : view.view();
        }
        if (sky == null || ship == null || !ship.materialized()) {
            return sky;
        }
        DVec3 x = sky.toInertial(new DVec3(1, 0, 0));
        DVec3 y = sky.toInertial(new DVec3(0, 1, 0));
        DVec3 z = sky.toInertial(new DVec3(0, 0, 1));
        org.joml.Quaternionf q = ship.renderOrientation(1f);
        org.joml.Vector3f a = q.transform(new org.joml.Vector3f(1, 0, 0));
        org.joml.Vector3f b = q.transform(new org.joml.Vector3f(0, 1, 0));
        org.joml.Vector3f c = q.transform(new org.joml.Vector3f(0, 0, 1));
        return new com.thepearlking.stevespace.universe.SpaceView(sky.position(),
                x.scale(a.x).add(y.scale(a.y)).add(z.scale(a.z)),
                x.scale(b.x).add(y.scale(b.y)).add(z.scale(b.z)),
                x.scale(c.x).add(y.scale(c.y)).add(z.scale(c.z)));
    }

    /** The local sky at a world position on the current body, or null when not on a planet. */
    public static LocalSky skyAt(Vec3 position, double t) {
        CelestialBody body = currentBody();
        if (body == null) {
            return null;
        }
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        double altitude = Math.max(0, position.y - 63);
        return LocalSky.at(system, body, geometry.latitude(position.z), geometry.longitude(position.x), altitude, t);
    }

    // ---------------------------------------------------------------------------------------
    // Day time follows the real sun at the camera
    // ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent.Pre event) {
        applyLocalDayTime();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        applyLocalDayTime();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        synced = false;
        com.thepearlking.stevespace.client.planet.PlanetMaps.clear();
    }

    private static void applyLocalDayTime() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (!synced || level == null || mc.gameRenderer.getMainCamera() == null || level.dimensionType().hasFixedTime()) {
            return;
        }
        double t = time();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        if (com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)) {
            if (com.thepearlking.stevespace.client.space.ClientSpace.orbitActive()) {
                // In orbit: daylight unless in the planet's shadow.
                double elevation = orbitalSunElevation(t);
                level.setDayTime(VanillaTime.advance(level.getDayTime(), VanillaTime.dayFraction(elevation, true)));
                return;
            }
            if (!(mc.player != null && com.thepearlking.stevespace.vessel.VesselEntity.shipOf(mc.player) instanceof com.thepearlking.stevespace.vessel.VesselEntity vessel)
                    || !com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive()) {
                // Walking a parked ship or station, or out on a spacewalk: lit unless in shadow.
                var here = com.thepearlking.stevespace.client.space.ClientSpace.viewAt(camera, 1);
                if (here != null) {
                    double elevation = shadowElevation(here.body(), here.view().position().sub(system.absolutePosition(here.body(), t)), t);
                    level.setDayTime(VanillaTime.advance(level.getDayTime(), VanillaTime.dayFraction(elevation, true)));
                }
                return;
            }
            // High in the air (map mode): the same daylight as on the ground below.
            camera = com.thepearlking.stevespace.client.space.ClientSpace.flightPosition(vessel, camera, 1);
        }
        LocalSky now = skyAt(camera, t);
        if (now == null) {
            return;
        }
        LocalSky later = skyAt(camera, t + 1);
        double elevation = now.elevationOf(system, system.sun(), t);
        boolean rising = later.elevationOf(system, system.sun(), t + 1) > elevation;
        level.setDayTime(VanillaTime.advance(level.getDayTime(), VanillaTime.dayFraction(elevation, rising)));
    }

    /**
     * A stand-in sun elevation for a vessel in orbit: well up in sunlight, well down in the
     * planet's shadow, sliding between the two across the shadow's soft edge.
     */
    private static double orbitalSunElevation(double t) {
        var body = com.thepearlking.stevespace.client.space.ClientSpace.body();
        var state = com.thepearlking.stevespace.client.space.ClientSpace.stateAt(t);
        if (body == null || state == null) {
            return Math.toRadians(30);
        }
        return shadowElevation(body, state.position(), t);
    }

    /** The stand-in sun elevation at {@code r} from the centre of {@code body}: lit, or in its shadow. */
    private static double shadowElevation(com.thepearlking.stevespace.universe.CelestialBody body, com.thepearlking.stevespace.universe.DVec3 r, double t) {
        if (body.parent() == null) {
            return Math.toRadians(60); // by the Sun itself
        }
        com.thepearlking.stevespace.universe.DVec3 sun = system.absolutePosition(system.sun(), t)
                .sub(system.absolutePosition(body, t)).normalize();
        double along = r.dot(sun);
        double sideways = r.sub(sun.scale(along)).length();
        if (along > 0) {
            return Math.toRadians(30); // on the sunlit side
        }
        double edge = (sideways - body.radius()) / (body.radius() * 0.03);
        double lit = Math.max(0, Math.min(1, edge * 0.5 + 0.5));
        return Math.toRadians(-30 + 60 * lit);
    }
}
