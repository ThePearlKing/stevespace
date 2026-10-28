package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thepearlking.stevespace.client.SpaceShaders;
import com.thepearlking.stevespace.client.nuke.NukeClient;
import com.thepearlking.stevespace.nuke.NuclearEffects;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What happens down on a planet, seen from above it (from orbit, or anywhere off its surface):
 * lightning flickering inside the storm clouds, brightest on the night side, and nuclear bursts
 * on or over the ground: the flash, the fireball, then the cloud's top spreading and drifting
 * for many minutes. Drawn over the planet once it has been drawn.
 */
public final class PlanetFlashes {

    /** Flashes tried per second over the whole of the Earth; only those inside a thunderstorm happen. */
    private static final double TRIES = 600;
    /** Height of the cloud tops the lightning lights up, km. */
    private static final double CLOUD_TOP = 11;

    private record Flash(double lat, double lon, double born, double length, double sizeKm, long seed) {
    }

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static long lastNanos;

    private PlanetFlashes() {
    }

    /** Drawn with the sky, after the planets and the ground. */
    static void render(PoseStack pose, SkyState s) {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        double clock = now / 1e9;
        SolarSystem system = s.system();
        var mc = net.minecraft.client.Minecraft.getInstance();
        String here = mc.level == null ? "" : mc.level.dimension().location().toString();
        RenderSystem.setShader(SpaceShaders::skyGlow);
        RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        CelestialBody earth = system.body("earth");
        if (earth != null && offSurface(s, earth, here)) {
            lightning(pose, s, earth, dt, clock);
        }
        for (NukeClient.Event e : NukeClient.events()) {
            if (e.regime() == NuclearEffects.Regime.SPACE || e.msg().body().isEmpty()) {
                continue;
            }
            CelestialBody body = system.body(e.msg().body());
            if (body != null && offSurface(s, body, here)) {
                nuke(pose, s, body, e);
            }
        }
        RenderSystem.defaultBlendFunc();
    }

    /** True when the viewer isn't standing in that body's own world (where the real thing is drawn instead). */
    private static boolean offSurface(SkyState s, CelestialBody body, String dimension) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level == null || com.thepearlking.stevespace.world.SpaceDimension.isSpace(level) || s.system().bodyForDimension(dimension) != body;
    }

    // ---------------------------------------------------------------------------------------
    // Lightning
    // ---------------------------------------------------------------------------------------

    private static void lightning(PoseStack pose, SkyState s, CelestialBody earth, double dt, double clock) {
        double t = s.time();
        // New flashes: tried all over, kept where a storm is.
        int tries = (int) Math.round(TRIES * dt + RANDOM.nextDouble() - 0.5);
        for (int i = 0; i < tries; i++) {
            double lat = Math.asin(RANDOM.nextDouble() * 2 - 1);
            double lon = RANDOM.nextDouble() * Math.PI * 2 - Math.PI;
            if (com.thepearlking.stevespace.client.ClientWeather.thunderAt(lat, lon) > RANDOM.nextDouble()) {
                FLASHES.add(new Flash(lat, lon, clock, 0.12 + RANDOM.nextDouble() * 0.45, 12 + RANDOM.nextDouble() * 30, RANDOM.nextLong()));
            }
        }
        FLASHES.removeIf(f -> clock - f.born() > f.length());
        if (FLASHES.isEmpty()) {
            return;
        }
        var frame = s.system().frameAt(earth, t);
        DVec3 centre = s.system().absolutePosition(earth, t);
        DVec3 sun = centre.scale(-1).normalize();
        DVec3 eye = s.sky().position();
        for (Flash f : FLASHES) {
            DVec3 up = frame.up(f.lat(), f.lon());
            DVec3 at = centre.add(up.scale(earth.radius() + CLOUD_TOP));
            DVec3 toEye = eye.sub(at);
            double km = toEye.length();
            if (up.dot(toEye) <= 0) {
                continue; // round the far side
            }
            // A flash flickers: two or three strokes within it.
            double age = clock - f.born();
            double phase = age / f.length();
            double flicker = Math.max(0, Math.sin(phase * Math.PI * (2 + (f.seed() & 3))));
            double night = Math.max(0.06, Math.min(1, (0.15 - up.dot(sun)) / 0.3));
            float a = (float) (flicker * night * 0.85 * (1 - phase * 0.4));
            if (a < 0.01f) {
                continue;
            }
            DVec3 dir = s.sky().toLocal(toEye.scale(-1)).normalize();
            SkyRenderer.billboard(pose, dir, (float) Math.atan(f.sizeKm() / km), 0.82f, 0.88f, 1f, a);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Nuclear bursts on the ground
    // ---------------------------------------------------------------------------------------

    private static void nuke(PoseStack pose, SkyState s, CelestialBody body, NukeClient.Event e) {
        double t = s.time();
        double age = e.age();
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        var frame = s.system().frameAt(body, t);
        DVec3 up = frame.up(geometry.latitude(e.z()), geometry.longitude(e.x()));
        DVec3 centre = s.system().absolutePosition(body, t);
        double kt = e.kt();
        // Sizes as drawn in the world (the same scale-up), in km out here.
        double fireball = NuclearEffects.blocks(NuclearEffects.fireballKm(kt));
        double cap = NuclearEffects.blocks(NuclearEffects.cloudCapKm(kt)) / 2;
        double top = NuclearEffects.blocks(NuclearEffects.cloudTopKm(kt));
        double rise = NuclearEffects.riseSeconds(kt);
        double climb = 1 - Math.exp(-age / rise);
        DVec3 eye = s.sky().position();
        DVec3 sun = centre.scale(-1).normalize();
        double daylight = Math.max(0, Math.min(1, (up.dot(sun) + 0.1) / 0.3));
        // The flash: the whole region lit for an instant, seen from anywhere with a view of it.
        DVec3 burst = centre.add(up.scale(body.radius() + Math.max(1, e.msg().heightKm() > 0 ? 2 : 1)));
        DVec3 toEye = eye.sub(burst);
        if (up.dot(toEye) <= 0) {
            return;
        }
        double km = toEye.length();
        DVec3 dir = s.sky().toLocal(toEye.scale(-1)).normalize();
        if (age < 2) {
            float a = (float) Math.exp(-age / 0.25);
            SkyRenderer.billboard(pose, dir, (float) Math.atan(Math.max(40, fireball * 12) * Math.cbrt(kt / 15) / km), 1f, 1f, 0.95f, a);
        }
        double glow = NuclearEffects.glowSeconds(kt);
        if (age < glow * 4) {
            float a = (float) Math.exp(-age / glow) * 0.9f;
            SkyRenderer.billboard(pose, dir, (float) Math.atan(fireball * 1.5 / km), 1f, 0.6f, 0.25f, a);
        }
        // The cloud itself, the same one as seen on the ground, set on the world where it went off.
        java.util.List<com.thepearlking.stevespace.client.cloud.PuffRenderer.Puff> raw = new java.util.ArrayList<>();
        com.thepearlking.stevespace.client.nuke.NukeRenderer.puffs(e, age, "earth".equals(body.id()), raw);
        if (raw.isEmpty()) {
            return;
        }
        double lat = geometry.latitude(e.z());
        double lon = geometry.longitude(e.x());
        DVec3 east = frame.east(lon);
        DVec3 south = frame.northAt(lat, lon).scale(-1);
        double ground = Double.isNaN(e.groundY()) ? e.y() : e.groundY();
        DVec3 base = centre.add(up.scale(body.radius()));
        DVec3 toBase = base.sub(eye);
        double distance = toBase.length();
        double k = 60 / distance; // into the sky, well inside the far plane
        java.util.List<com.thepearlking.stevespace.client.cloud.PuffRenderer.Puff> puffs = new java.util.ArrayList<>();
        for (var p : raw) {
            DVec3 at = toBase.add(east.scale(p.x() - e.x())).add(up.scale(p.y() - ground)).add(south.scale(p.z() - e.z()));
            DVec3 local = s.sky().toLocal(at).scale(k);
            puffs.add(new com.thepearlking.stevespace.client.cloud.PuffRenderer.Puff(local.x(), local.y(), local.z(), (float) (p.radius() * k),
                    p.r(), p.g(), p.b(), p.a(), p.glowR(), p.glowG(), p.glowB(), p.seed()));
        }
        DVec3 upLocal = s.sky().toLocal(up);
        DVec3 sunLocal = s.sky().toLocal(sun);
        float day = (float) daylight;
        var light = new com.thepearlking.stevespace.client.cloud.PuffRenderer.Light(
                new org.joml.Vector3f((float) sunLocal.x(), (float) sunLocal.y(), (float) sunLocal.z()),
                new org.joml.Vector3f(1f, 0.96f, 0.9f).mul(0.8f * day), new org.joml.Vector3f(0.35f, 0.4f, 0.5f).mul(0.08f + 0.3f * day),
                new org.joml.Vector3f(0.5f, 0.6f, 0.75f), 1e9f);
        com.thepearlking.stevespace.client.cloud.PuffRenderer.drawSky(puffs, new org.joml.Matrix4f(pose.last().pose()),
                new org.joml.Vector3f((float) upLocal.x(), (float) upLocal.y(), (float) upLocal.z()), light,
                (float) ((net.minecraft.Util.getMillis() / 1000.0) % 10000));
        // Back to the sky's own glow drawing for whatever comes next.
        RenderSystem.setShader(SpaceShaders::skyGlow);
        RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }
}
