package com.thepearlking.stevespace.client.sky;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SkyView;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Everything about the sky for one frame: where the Sun is, how thick the air above is, how far
 * the horizon has dropped with altitude. On a planet's surface the camera's axes are east, up and
 * south; in space they are the vessel cell's fixed axes and {@code home} is the nearest planet.
 * Computed once and shared by the sky, fog and planet renderers.
 *
 * @param altitude      blocks above sea level (y 63)
 * @param density       air thickness above the camera: 1 near the ground, 0 in space
 * @param horizonDip    sine of how far the true horizon lies below level at this altitude
 * @param skyBrightness 0 at night or in space, 1 in full daylight
 */
public record SkyState(SolarSystem system, CelestialBody home, SkyView sky, double time, DVec3 sunDir,
                       SkyPalette palette, double altitude, float density, float horizonDip,
                       float skyBrightness, float rain) {

    public static final int SEA_LEVEL = 63;

    /** The sky at the camera, or null when the camera is somewhere the universe doesn't cover. */
    public static SkyState compute(Camera camera, ClientLevel level, float partialTick) {
        if (!ClientUniverse.isSynced()) {
            return null;
        }
        Vec3 position = camera.getPosition();
        if (com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)) {
            if (!com.thepearlking.stevespace.client.space.ClientSpace.mapModeActive()) {
                return computeSpace(camera);
            }
            // High in the air (map mode): the same sky as over the surface, at the map position.
            var vessel = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(net.minecraft.client.Minecraft.getInstance().player);
            position = com.thepearlking.stevespace.client.space.ClientSpace.flightPosition(vessel, position, partialTick);
        }
        CelestialBody home = ClientUniverse.currentBody();
        if (home == null) {
            return null;
        }
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        SkyView sky = ClientUniverse.skyAt(position, t);
        DVec3 sunDir = sky.offsetTo(system, system.sun(), t).normalize();
        SkyPalette palette = SkyPalette.forBody(home.id());
        // Inside a giant there is no ground: the height goes on down below the one-bar level.
        double altitude = com.thepearlking.stevespace.universe.GiantAir.has(home) ? position.y - SEA_LEVEL : Math.max(0, position.y - SEA_LEVEL);
        float density = atmosphereDensity(home, Math.max(0, altitude));
        double ratio = home.radius() / (home.radius() + Math.max(0, altitude));
        float dip = (float) Math.sqrt(Math.max(0, 1 - ratio * ratio));
        float brightness = palette == null ? 0 : SkyColors.skyBrightness((float) sunDir.y(), density);
        return new SkyState(system, home, sky, t, sunDir, palette, altitude, density, dip, brightness,
                level.getRainLevel(partialTick));
    }

    /** The sky from a vessel in orbit: black, stars, and the planet it orbits below. */
    private static SkyState computeSpace(Camera camera) {
        var space = com.thepearlking.stevespace.client.space.ClientSpace.view(camera);
        if (space == null) {
            return null;
        }
        SolarSystem system = ClientUniverse.system();
        double t = ClientUniverse.time();
        CelestialBody home = space.body();
        DVec3 sunDir = space.view().offsetTo(system, system.sun(), t).normalize();
        DVec3 toHome = system.absolutePosition(home, t).sub(space.view().position());
        double altitude = Math.max(0, toHome.length() - home.radius());
        double ratio = home.radius() / (home.radius() + altitude);
        float dip = (float) Math.sqrt(Math.max(0, 1 - ratio * ratio));
        return new SkyState(system, home, space.view(), t, sunDir, null, altitude, 0f, dip, 0f, 0f);
    }

    /**
     * Air thickness at {@code altitude} blocks above sea level: full strength far above the build
     * limit (up to ~45% of the atmosphere's height, about y 1100 on Earth), then thinning out to
     * nothing at the edge of space.
     */
    public static float atmosphereDensity(CelestialBody body, double altitude) {
        if (!body.hasAtmosphere()) {
            return 0;
        }
        double top = body.atmosphere().height();
        double fadeStart = top * 0.45;
        if (altitude <= fadeStart) {
            return 1;
        }
        double t = (altitude - fadeStart) / (top - fadeStart);
        return (float) Math.exp(-4.5 * t * t - 1.0 * t);
    }

    /** Sky colour in a direction (local axes), clamped and greyed by rain. */
    public float[] skyColor(float x, float y, float z) {
        float[] c = new float[3];
        if (palette == null) {
            return c; // airless: black
        }
        SkyColors.sky(palette, x, y, z, (float) sunDir.x(), (float) sunDir.y(), (float) sunDir.z(), density, horizonDip, c);
        if (rain > 0) {
            float grey = (c[0] * 0.3f + c[1] * 0.59f + c[2] * 0.11f) * 0.7f;
            for (int i = 0; i < 3; i++) {
                c[i] = c[i] + (grey - c[i]) * rain * 0.8f;
            }
        }
        // Vertex colours are packed into bytes without clamping: values above 1 would wrap around.
        for (int i = 0; i < 3; i++) {
            c[i] = Math.max(0, Math.min(1, c[i]));
        }
        return c;
    }

    /** Colour of the haze at the true horizon, in the given horizontal direction. */
    public float[] horizonColor(float x, float z) {
        float length = (float) Math.sqrt(x * x + z * z);
        if (length < 1e-3f) {
            x = 1;
            z = 0;
            length = 1;
        }
        float cos = (float) Math.sqrt(1 - horizonDip * horizonDip);
        return skyColor(x / length * cos, -horizonDip + 0.02f, z / length * cos);
    }
}
