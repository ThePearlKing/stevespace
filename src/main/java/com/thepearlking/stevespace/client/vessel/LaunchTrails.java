package com.thepearlking.stevespace.client.vessel;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.cloud.PuffRenderer;
import com.thepearlking.stevespace.client.nuke.NukeClient;
import com.thepearlking.stevespace.network.LaunchTrackS2C;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.vessel.LaunchBeacon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The trails rockets leave in the air: a launch's white exhaust column climbing away (with the
 * cloud it throws up off the pad), or the streak of something coming down fast. They hang in the
 * sky for minutes, spreading, drifting and shearing in the wind (which changes with height), and
 * stay sunlit high up after sunset. They're drawn as far as the haze allows, well beyond where
 * the rocket itself can be seen, the way a launch can be watched from a hundred kilometres away.
 */
public final class LaunchTrails {

    /** How long a trail hangs in the air, seconds. */
    private static final double LIFE = 300;
    private static final int MAX_POINTS = 2500;
    private static final int MAX_PUFFS = 6000;

    private enum Kind { EXHAUST, PAD, STREAK }

    /** One bit of trail: where it was left, when (seconds), how strong, how high (blocks above sea level). */
    private record Point(double x, double y, double z, double born, float strength, Kind kind, int seed) {
    }

    private static final class Trail {
        final List<Point> points = new ArrayList<>();
        double lastX = Double.NaN;
        double lastY;
        double lastZ;
        double lastSeen;
        float throttle;
        float speed;
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static String dimension;
    private static int seeds;

    private LaunchTrails() {
    }

    /** Seconds on the client's clock (stops while paused). */
    private static double now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true)) / 20.0;
    }

    public static void track(LaunchTrackS2C msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String dim = mc.level.dimension().location().toString();
        if (!dim.equals(dimension)) {
            TRAILS.clear();
            dimension = dim;
        }
        CelestialBody body = ClientUniverse.currentBody();
        if (body == null || !body.hasAtmosphere()) {
            return;
        }
        Trail trail = TRAILS.computeIfAbsent(msg.id(), id -> new Trail());
        double t = now();
        double top = body.atmosphere().height();
        if (!Double.isNaN(trail.lastX) && t - trail.lastSeen < 2) {
            // Fill in from the last place, about a puff's width apart.
            double dx = msg.x() - trail.lastX;
            double dy = msg.y() - trail.lastY;
            double dz = msg.z() - trail.lastZ;
            double gap = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double alt = Math.max(0, msg.y() - 63);
            double spacing = 0.6 * startRadius(alt / top, Kind.EXHAUST, 1);
            int steps = (int) Math.min(200, Math.ceil(gap / Math.max(0.4, spacing)));
            for (int i = 1; i <= steps; i++) {
                double f = i / (double) steps;
                add(trail, trail.lastX + dx * f, trail.lastY + dy * f, trail.lastZ + dz * f,
                        t - (1 - f) * (t - trail.lastSeen), msg, top);
            }
        } else {
            add(trail, msg.x(), msg.y(), msg.z(), t, msg, top);
        }
        trail.lastX = msg.x();
        trail.lastY = msg.y();
        trail.lastZ = msg.z();
        trail.lastSeen = t;
        trail.throttle = msg.throttle();
        trail.speed = msg.speed();
    }

    private static void add(Trail trail, double x, double y, double z, double born, LaunchTrackS2C msg, double top) {
        double alt = y - 63;
        if (alt > top) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (msg.throttle() > 0.01f) {
            // Low and slow (still lifting off): the exhaust hits the ground and billows out.
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            boolean pad = false;
            if (msg.speed() < 25 && mc.level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
                pad = y - mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz) < 14;
            }
            trail.points.add(new Point(x, y, z, born, msg.throttle(), pad ? Kind.PAD : Kind.EXHAUST, seeds++));
        } else {
            float heat = Math.min(1, (msg.speed() - LaunchBeacon.STREAK_SPEED) / 120f);
            if (heat > 0) {
                trail.points.add(new Point(x, y, z, born, heat, Kind.STREAK, seeds++));
            }
        }
        if (trail.points.size() > MAX_POINTS) {
            trail.points.remove(0);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (TRAILS.isEmpty()) {
            return;
        }
        double t = now();
        for (Iterator<Trail> it = TRAILS.values().iterator(); it.hasNext(); ) {
            Trail trail = it.next();
            trail.points.removeIf(p -> t - p.born() > LIFE || t < p.born() - 5);
            if (trail.points.isEmpty() && t - trail.lastSeen > 2) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRAILS.clear();
    }

    /** Radius of a fresh bit of trail (blocks): thin near the ground, ballooning in thin air high up. */
    private static double startRadius(double heightFraction, Kind kind, float strength) {
        return switch (kind) {
            case PAD -> 2.5 + 3.5 * strength;
            case STREAK -> 0.5 + 1.2 * strength + 3 * heightFraction;
            case EXHAUST -> 0.9 + 0.5 * strength + 5 * heightFraction * heightFraction;
        };
    }

    /** The wind at this height (blocks per second, east and south): its direction veers with height, and it's stronger higher up. */
    private static double[] wind(double alt, long seed) {
        double turn = (seed % 1000) / 1000.0 * Math.PI * 2;
        double angle = turn + 1.6 * Math.sin(alt / 380 + turn) + 0.7 * Math.sin(alt / 140 + 2 * turn);
        double speed = 0.35 + 0.9 * Math.min(1, alt / 700) + 0.9 * Math.exp(-Math.pow((alt - 300) / 130, 2));
        return new double[]{Math.cos(angle) * speed, Math.sin(angle) * speed};
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || TRAILS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        CelestialBody body = ClientUniverse.currentBody();
        if (mc.level == null || body == null || !body.hasAtmosphere()
                || !mc.level.dimension().location().toString().equals(dimension)) {
            return;
        }
        double top = body.atmosphere().height();
        double radius = body.radius();
        double airDensity = Math.max(0.15, Math.min(1, Math.sqrt(body.atmosphere().density())));
        long windSeed = dimension.hashCode() & 0xffffffL;
        Vec3 eye = event.getCamera().getPosition();
        double t = now();
        PuffRenderer.Light light = NukeClient.light(event.getPartialTick());
        // How far below the horizon the Sun is (radians), for the trail's high part catching it after sunset.
        double sunDip = Math.max(0, -Math.asin(Math.max(-1, Math.min(1, light.sun().y))));
        List<PuffRenderer.Puff> lit = new ArrayList<>();
        List<PuffRenderer.Puff> dark = new ArrayList<>();
        int total = 0;
        for (Trail trail : TRAILS.values()) {
            total += trail.points.size();
        }
        int stride = Math.max(1, (int) Math.ceil(total / (double) MAX_PUFFS));
        for (Trail trail : TRAILS.values()) {
            List<Point> points = trail.points;
            for (int i = 0; i < points.size(); i += stride) {
                Point p = points.get(i);
                double age = Math.max(0, t - p.born());
                double alt = Math.max(0, p.y() - 63);
                double hf = Math.min(1, alt / top);
                // Spreading slowly as it mixes into the air (the pad cloud faster), and thinning as it spreads.
                double r0 = startRadius(hf, p.kind(), p.strength());
                double r = r0 + Math.pow(age, 0.75) * (p.kind() == Kind.PAD ? 0.2 : 0.07);
                double thinning = Math.pow(r0 / r, 1.4);
                // Drift with the wind, a slow sinking of the heavy pad cloud, and a little turbulence.
                double[] w = wind(alt, windSeed);
                double jitter = Math.sqrt(age) * 0.35 * r / (1 + r);
                long s = p.seed() * 2654435761L;
                double x = p.x() + w[0] * age + jitter * (((s >> 8) & 255) / 127.5 - 1);
                double z = p.z() + w[1] * age + jitter * (((s >> 16) & 255) / 127.5 - 1);
                double y = p.y() + (p.kind() == Kind.PAD ? 0.25 * Math.sqrt(age) * r * 0.3 : 0) + jitter * (((s >> 24) & 255) / 127.5 - 1);
                // Far off, the trail sinks below the curve of the planet like everything else.
                double dx = x - eye.x;
                double dz = z - eye.z;
                y -= (dx * dx + dz * dz) / (2 * radius);
                double fadeIn = Math.min(1, age / 0.25);
                double fadeOut = Math.max(0, Math.min(1, (LIFE - age) / (LIFE * 0.45)));
                double thin = Math.pow(1 - hf, 0.6);
                float a = (float) ((p.kind() == Kind.STREAK ? 0.55 : 0.8) * Math.sqrt(p.strength()) * fadeIn * fadeOut * thin * airDensity * thinning);
                if (a < 0.01f) {
                    continue;
                }
                float shade = switch (p.kind()) {
                    case PAD -> 0.84f;
                    case STREAK -> 0.8f;
                    case EXHAUST -> 0.94f;
                };
                float gr = 0;
                float gg = 0;
                float gb = 0;
                double hot = p.kind() == Kind.STREAK ? 0.9 : 0.35;
                if (age < hot) {
                    // Right behind the flame (or the glowing craft), the plume is lit orange from inside.
                    float g = (float) ((1 - age / hot) * (p.kind() == Kind.STREAK ? p.strength() : 1));
                    gr = g;
                    gg = 0.55f * g;
                    gb = 0.22f * g;
                }
                float tint = p.kind() == Kind.PAD ? 0.95f : 1f;
                PuffRenderer.Puff puff = new PuffRenderer.Puff(x, y, z, (float) r, shade, shade * tint, shade * tint * tint, a, gr, gg, gb, p.seed());
                // Still in sunlight here? (After sunset only the high part is.)
                double kmUp = alt / top * 100;
                boolean sunlit = sunDip <= 0 || kmUp > radius * (1 / Math.cos(Math.min(1.5, sunDip)) - 1);
                (sunlit ? lit : dark).add(puff);
            }
            // The flame itself while it burns (or the craft glowing as it streaks in): a bright spark at any distance.
            if (t - trail.lastSeen < 0.6 && !Double.isNaN(trail.lastX)) {
                boolean burning = trail.throttle > 0.01f;
                float heat = Math.min(1, (trail.speed - LaunchBeacon.STREAK_SPEED) / 120f);
                if (burning || heat > 0) {
                    double dx = trail.lastX - eye.x;
                    double dy = trail.lastY - eye.y;
                    double dz = trail.lastZ - eye.z;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > 48) {
                        float size = (float) Math.max(burning ? 0.7 : 0.4, d * 0.0035);
                        float k = burning ? Math.max(0.4f, trail.throttle) : heat;
                        lit.add(new PuffRenderer.Puff(trail.lastX, trail.lastY - (dx * dx + dz * dz) / (2 * radius), trail.lastZ, size,
                                1f, 0.85f, 0.6f, Math.min(1, 0.6f + k), k, 0.75f * k, 0.4f * k, 7));
                    }
                }
            }
        }
        if (lit.isEmpty() && dark.isEmpty()) {
            return;
        }
        org.joml.Matrix4f view = new org.joml.Matrix4f().rotation(event.getCamera().rotation().conjugate(new org.joml.Quaternionf()));
        float anim = (float) ((net.minecraft.Util.getMillis() / 1000.0) % 10000);
        float haze = 6000f;
        if (!dark.isEmpty()) {
            // Out of the sunlight: only the night sky's faint light on it, a dim grey, never black.
            PuffRenderer.Light night = new PuffRenderer.Light(light.sun(), new Vector3f(),
                    new Vector3f(light.ambient()).max(new Vector3f(0.16f, 0.17f, 0.2f)), light.haze(), haze);
            dark.replaceAll(p -> new PuffRenderer.Puff(p.x(), p.y(), p.z(), p.radius(), p.r(), p.g(), p.b(), p.a() * 0.45f,
                    p.glowR(), p.glowG(), p.glowB(), p.seed()));
            PuffRenderer.draw(dark, view, event.getCamera(), night, anim);
        }
        if (!lit.isEmpty()) {
            PuffRenderer.Light sun = light;
            if (sunDip > 0) {
                // Sunlight grazing in from below the horizon: low, red and gold.
                sun = new PuffRenderer.Light(new Vector3f(light.sun().x, 0.02f, light.sun().z).normalize(),
                        new Vector3f(1f, 0.58f, 0.32f).mul(0.8f), light.ambient(), light.haze(), haze);
            } else {
                sun = new PuffRenderer.Light(light.sun(), light.sunColour(), light.ambient(), light.haze(), haze);
            }
            PuffRenderer.draw(lit, view, event.getCamera(), sun, anim);
        }
    }
}
