package com.thepearlking.stevespace.client.telescope;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SkyView;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.radio.SkyShips;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** What a telescope at a place can point at right now: every body but the Sun and the ground underfoot. */
public final class SkyCatalog {

    /**
     * One body as seen from here.
     *
     * @param direction      unit vector in local sky axes (east, up, south)
     * @param elevation      degrees above the horizon
     * @param azimuth        degrees from north, clockwise
     * @param angularRadius  radians (the planet itself, not its rings)
     * @param phase          lit fraction of the disc, 0..1
     * @param magnitude      apparent magnitude (smaller is brighter)
     */
    public record Entry(CelestialBody body, SkyShips.Ship ship, DVec3 direction, double distance, double elevation, double azimuth,
                        double angularRadius, double phase, double magnitude, boolean inSpace, CelestialBody blockedBy) {

        /** Ships have ids starting "ship:", bodies use their own id. */
        public String id() {
            return ship != null ? SHIP + ship.id() : body.id();
        }

        public String name() {
            return ship != null ? ship.name() : body.displayName();
        }

        public int color() {
            return ship != null ? TelescopeShips.color(ship) : body.color();
        }

        /** In view: not behind a planet, and (on the ground) above the horizon. In space there is no horizon. */
        public boolean aboveHorizon() {
            return blockedBy == null && (inSpace || elevation > -0.5);
        }

        /** Angular radius including rings, for framing. */
        public double framingRadius() {
            if (ship != null) {
                return Math.max(angularRadius, TelescopeShips.RESOLUTION * 3);
            }
            return body.rings() != null ? angularRadius * body.rings().outerRadius() / body.radius() : angularRadius;
        }
    }

    public static final String SHIP = "ship:";

    private SkyCatalog() {
    }

    /** Everything visible from {@code eye}, brightest above the horizon first, then the rest. */
    public static List<Entry> from(Vec3 eye) {
        List<Entry> list = new ArrayList<>();
        if (!ClientUniverse.isSynced()) {
            return list;
        }
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        SkyView sky = ClientUniverse.telescopeSky(eye);
        CelestialBody home = ClientUniverse.currentBody();
        if (sky == null) {
            return list;
        }
        boolean space = inSpace();
        for (CelestialBody body : system.bodies()) {
            if (body == system.sun() || body.hidden() || !space && home != null && body.id().equals(home.id())) {
                continue;
            }
            list.add(entry(system, sky, body, t));
        }
        for (SkyShips.Ship ship : TelescopeShips.ships()) {
            Entry e = ship(sky, ship);
            // Only ships with a radio can be looked up; the rest you'd have to find by hand.
            if (e != null && ship.radio() && e.distance() > ship.radius() * 3) { // not the one you're standing in
                list.add(e);
            }
        }
        list.sort(Comparator.comparing((Entry e) -> !e.aboveHorizon()).thenComparingDouble(Entry::magnitude));
        return list;
    }

    /** One body as seen from {@code eye}, or null. */
    public static Entry of(Vec3 eye, String bodyId) {
        if (!ClientUniverse.isSynced()) {
            return null;
        }
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        SkyView sky = ClientUniverse.telescopeSky(eye);
        if (sky != null && bodyId.startsWith(SHIP)) {
            SkyShips.Ship ship = TelescopeShips.find(bodyId.substring(SHIP.length()));
            return ship == null ? null : ship(sky, ship);
        }
        CelestialBody body = system.body(bodyId);
        return sky == null || body == null ? null : entry(system, sky, body, t);
    }

    private static Entry entry(SolarSystem system, SkyView sky, CelestialBody body, double t) {
        DVec3 offset = sky.offsetTo(system, body, t);
        double distance = offset.length();
        DVec3 d = offset.scale(1 / distance);
        double elevation = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d.y()))));
        // Local axes: x east, z south; north is -z.
        double azimuth = (Math.toDegrees(Math.atan2(d.x(), -d.z())) + 360) % 360;
        double angularRadius = Math.asin(Math.min(1, body.radius() / distance));
        DVec3 bodyPos = system.absolutePosition(body, t);
        DVec3 toSun = bodyPos.scale(-1).normalize();
        DVec3 toObserver = sky.toInertial(d.scale(-1)).normalize();
        double phase = (1 + toSun.dot(toObserver)) / 2;
        double au = SolarSystem.AU;
        double absolute = 5 * Math.log10(1329 / (body.radius() * 2 * Math.sqrt(Math.max(0.01, body.albedo()))));
        double magnitude = absolute + 5 * Math.log10(bodyPos.length() / au * distance / au) - 2.5 * Math.log10(Math.max(1e-4, phase));
        return new Entry(body, null, d, distance, elevation, azimuth, angularRadius, phase, magnitude, inSpace(),
                blocker(system, sky.position(), system.absolutePosition(body, t), body, t));
    }

    private static boolean inSpace() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)
                && !com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive();
    }

    /** The body standing between {@code from} and {@code to} (absolute positions), if any. */
    private static CelestialBody blocker(SolarSystem system, DVec3 from, DVec3 to, CelestialBody target, double t) {
        DVec3 ray = to.sub(from);
        double length = ray.length();
        if (length < 1e-9) {
            return null;
        }
        DVec3 dir = ray.scale(1 / length);
        for (CelestialBody b : system.bodies()) {
            if (b == target || b == system.sun()) {
                continue;
            }
            DVec3 c = system.absolutePosition(b, t).sub(from);
            double along = c.dot(dir);
            if (along <= 0 || along >= length) {
                continue;
            }
            if (c.sub(dir.scale(along)).length() < b.radius()) {
                return b;
            }
        }
        return null;
    }

    /** A ship as seen from here, or null if it can't be placed. */
    public static Entry ship(SkyView sky, SkyShips.Ship ship) {
        DVec3 at = TelescopeShips.position(ship);
        if (at == null) {
            return null;
        }
        DVec3 offset = sky.toLocal(at.sub(sky.position()));
        double distance = offset.length();
        if (distance < 1e-6) {
            return null;
        }
        DVec3 d = offset.scale(1 / distance);
        double elevation = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d.y()))));
        double azimuth = (Math.toDegrees(Math.atan2(d.x(), -d.z())) + 360) % 360;
        double angularRadius = Math.asin(Math.min(1, ship.radius() / distance));
        float light = TelescopeShips.light(ship);
        // Roughly a grey surface its size, lit or in shadow.
        double magnitude = 5 * Math.log10(1329 / (ship.radius() * 2 * Math.sqrt(0.3))) + 5 * Math.log10(
                Math.max(1e-9, at.length() / SolarSystem.AU * distance / SolarSystem.AU)) - 2.5 * Math.log10(light);
        return new Entry(null, ship, d, distance, elevation, azimuth, angularRadius, light, magnitude, inSpace(),
                blocker(ClientUniverse.system(), sky.position(), at, null, ClientUniverse.time()));
    }

    /** "SW", "NNE"... for an azimuth in degrees. */
    public static String compass(double azimuth) {
        String[] points = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return points[(int) Math.round(azimuth / 22.5) % 16];
    }

    /** Distance in km or AU, whichever reads better. */
    public static String distance(double km) {
        double au = km / SolarSystem.AU;
        return au > 0.05 ? String.format(java.util.Locale.ROOT, "%.2f AU", au) : String.format(java.util.Locale.ROOT, "%,.0f km", km);
    }

    /** Apparent size, in arcseconds or arcminutes. */
    public static String size(double angularRadius) {
        double arcsec = Math.toDegrees(angularRadius * 2) * 3600;
        return arcsec >= 120 ? String.format(java.util.Locale.ROOT, "%.1f′", arcsec / 60) : String.format(java.util.Locale.ROOT, "%.1f″", arcsec);
    }
}
