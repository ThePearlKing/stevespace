package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.client.SpaceShaders;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SolarActivity;
import com.thepearlking.stevespace.universe.SolarSystem;
import org.joml.Matrix4f;

/**
 * Auroras on the world you're on or orbiting, where its magnetic field puts them: curtains of
 * light hanging along an oval round each magnetic pole, built in 3D at their real height, so
 * from the ground they stand in the right part of the sky (overhead under the oval, low toward
 * the pole further away) and from orbit they ring the night side. Solar storms brighten them and
 * push the ovals toward the equator. Colours are what the eye would see: Earth's oxygen green
 * and red over nitrogen's magenta fringe; the giant planets' hydrogen glow, a faint pinkish red
 * (their bright aurora is ultraviolet); Mars only in storms, a faint green over the whole night
 * sky.
 */
public final class AuroraRenderer {

    private static final float DOME = 80;
    private static final int SEGMENTS = 240;

    /** For tests: a storm strength to use instead of the real one (NaN = the real one). */
    public static double testStorm = Double.NaN;

    private AuroraRenderer() {
    }

    /**
     * One world's aurora. {@code tilt}: magnetic pole from the spin pole; {@code colat}: the
     * oval's distance from the magnetic pole (both degrees); {@code bottom}/{@code top}: height
     * of the curtains above the ground (blocks = km); {@code quiet}: brightness with no storm.
     */
    private record Kind(double tilt, double poleLongitude, double colat, double width, double bottom, double top,
                        float[] low, float[] mid, float[] high, double quiet, double perStorm, double stormSpread) {
    }

    private static Kind kind(String id) {
        return switch (id) {
            // Earth: under the oval a plain green arc is out most dark nights, storms or not.
            case "earth" -> new Kind(10, -72, 23, 3.5, 100, 320,
                    new float[]{0.85f, 0.25f, 0.7f}, new float[]{0.3f, 1.0f, 0.45f}, new float[]{0.95f, 0.18f, 0.22f}, 0.6, 0.55, 6);
            case "jupiter" -> new Kind(9.6, -159, 16, 2.5, 250, 900,
                    new float[]{0.7f, 0.3f, 0.8f}, new float[]{0.95f, 0.38f, 0.55f}, new float[]{0.85f, 0.25f, 0.35f}, 0.55, 0.15, 1);
            case "saturn" -> new Kind(0, 0, 15, 2.5, 800, 2000,
                    new float[]{0.8f, 0.3f, 0.65f}, new float[]{0.95f, 0.35f, 0.45f}, new float[]{0.85f, 0.2f, 0.3f}, 0.35, 0.3, 2);
            case "uranus" -> new Kind(59, 20, 20, 3, 500, 1500,
                    new float[]{0.75f, 0.3f, 0.7f}, new float[]{0.9f, 0.4f, 0.55f}, new float[]{0.8f, 0.25f, 0.35f}, 0.18, 0.15, 1);
            case "neptune" -> new Kind(47, -60, 20, 3, 500, 1500,
                    new float[]{0.75f, 0.3f, 0.7f}, new float[]{0.9f, 0.4f, 0.55f}, new float[]{0.8f, 0.25f, 0.35f}, 0.15, 0.15, 1);
            case "ganymede" -> new Kind(0, 0, 50, 4, 20, 70,
                    new float[]{0.8f, 0.25f, 0.3f}, new float[]{0.95f, 0.3f, 0.3f}, new float[]{0.9f, 0.2f, 0.25f}, 0.2, 0.1, 1);
            default -> null;
        };
    }

    /** Draws the home world's aurora (directions from the viewer, in the sky's local axes). */
    public static void render(PoseStack pose, SkyState s) {
        renderNatural(pose, s);
        renderArtificial(pose, s);
    }

    private static void renderNatural(PoseStack pose, SkyState s) {
        CelestialBody body = s.home();
        if (body == null || SpaceShaders.aurora() == null) {
            return;
        }
        SolarSystem system = s.system();
        double t = s.time();
        DVec3 centre = system.absolutePosition(body, t);
        DVec3 eye = s.sky().position();
        // Daylight drowns it (from the ground); rain and cloud hide it.
        float dark = 1 - Math.min(1, s.skyBrightness() * 1.6f);
        float clear = 1 - s.rain();
        if (dark * clear < 0.02f) {
            return;
        }
        double storm = Double.isNaN(testStorm) ? SolarActivity.storm(centre.sub(system.absolutePosition(system.sun(), t)), t) : testStorm;
        if ("mars".equals(body.id())) {
            if (storm > 0.3) {
                diffuse(pose, s, (float) Math.min(1, (storm - 0.3) * 0.35) * dark * clear);
            }
            return;
        }
        Kind k = kind(body.id());
        if (k == null) {
            return;
        }
        double activity = SolarActivity.cycle(t);
        float brightness = (float) Math.min(1.2, k.quiet() * (0.6 + 0.4 * activity) + k.perStorm() * storm) * dark * clear;
        if (brightness < 0.02f) {
            return;
        }
        SolarSystem.BodyFrame frame = system.frameAt(body, t);
        double tilt = Math.toRadians(k.tilt());
        double lon = Math.toRadians(k.poleLongitude());
        DVec3 magnetic = frame.north().scale(Math.cos(tilt))
                .add(frame.meridian().scale(Math.cos(lon) * Math.sin(tilt)))
                .add(frame.east90().scale(Math.sin(lon) * Math.sin(tilt))).normalize();
        DVec3 sunFromBody = system.absolutePosition(system.sun(), t).sub(centre).normalize();
        double spread = Math.min(3, storm) * k.stormSpread();

        var shader = SpaceShaders.aurora();
        RenderSystem.setShader(SpaceShaders::aurora);
        shader.safeGetUniform("AnimTime").set((float) ((net.minecraft.Util.getMillis() / 1000.0) % 10000));
        shader.safeGetUniform("LowColor").set(k.low()[0], k.low()[1], k.low()[2]);
        shader.safeGetUniform("MidColor").set(k.mid()[0], k.mid()[1], k.mid()[2]);
        shader.safeGetUniform("HighColor").set(k.high()[0], k.high()[1], k.high()[2]);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean any = false;
        for (int hemisphere = 0; hemisphere < 2; hemisphere++) {
            DVec3 axis = hemisphere == 0 ? magnetic : magnetic.scale(-1);
            DVec3 helper = Math.abs(axis.y()) < 0.9 ? DVec3.Y : DVec3.X;
            DVec3 e1 = axis.cross(helper).normalize();
            DVec3 e2 = axis.cross(e1).normalize();
            // A few curtains side by side, the poleward ones fainter.
            for (int arc = 0; arc < 3; arc++) {
                double base = k.colat() + spread + (arc - 1) * k.width() * 0.45;
                float arcLight = arc == 1 ? 1f : arc == 0 ? 0.55f : 0.7f;
                any |= arc(builder, matrix, s, centre, eye, body.radius(), axis, e1, e2, base, k, t, arc + hemisphere * 3,
                        sunFromBody, brightness * arcLight, Math.PI, Math.PI);
            }
        }
        if (any) {
            BufferUploader.drawWithShader(builder.buildOrThrow());
        } else {
            builder.build(); // nothing in view
        }
        RenderSystem.defaultBlendFunc();
    }

    /**
     * The aurora a burst above the air sets off (Starfish Prime lit the sky over Hawaii red for
     * minutes, and over Samoa, at the other end of the field lines): electrons from the blast run
     * along the magnetic shell it went off on and light the upper air where that shell comes down,
     * round the burst's magnetic longitude and at the matching point in the other hemisphere,
     * spreading east and west as they drift. Mostly the deep red of oxygen high up.
     */
    private static void renderArtificial(PoseStack pose, SkyState s) {
        CelestialBody body = s.home();
        if (body == null || SpaceShaders.aurora() == null) {
            return;
        }
        Kind k = kind(body.id());
        if (k == null) {
            return; // no magnetic field: nothing to guide the electrons
        }
        float dark = (1 - Math.min(1, s.skyBrightness() * 1.6f)) * (1 - s.rain());
        if (dark < 0.02f) {
            return;
        }
        SolarSystem system = s.system();
        double t = s.time();
        DVec3 centre = system.absolutePosition(body, t);
        DVec3 eye = s.sky().position();
        SolarSystem.BodyFrame frame = system.frameAt(body, t);
        double tilt = Math.toRadians(k.tilt());
        double lon = Math.toRadians(k.poleLongitude());
        DVec3 magnetic = frame.north().scale(Math.cos(tilt))
                .add(frame.meridian().scale(Math.cos(lon) * Math.sin(tilt)))
                .add(frame.east90().scale(Math.sin(lon) * Math.sin(tilt))).normalize();
        DVec3 sunFromBody = system.absolutePosition(system.sun(), t).sub(centre).normalize();
        Kind red = new Kind(k.tilt(), k.poleLongitude(), 0, 4, 160, 520,
                new float[]{0.8f, 0.25f, 0.4f}, new float[]{0.95f, 0.32f, 0.26f}, new float[]{1.0f, 0.12f, 0.12f}, 0, 0, 0);
        boolean begun = false;
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = null;
        for (var e : com.thepearlking.stevespace.client.nuke.NukeClient.events()) {
            if (e.regime() != com.thepearlking.stevespace.nuke.NuclearEffects.Regime.SPACE || !body.id().equals(e.msg().body())) {
                continue;
            }
            double age = e.age();
            float strength = (float) (Math.min(1, age / 8) * Math.exp(-age / 360) * 1.3 * Math.min(1.5, Math.cbrt(e.kt() / 1450))) * dark;
            if (strength < 0.02f) {
                continue;
            }
            DVec3 rel = e.absolute().sub(centre);
            double sinLat = rel.normalize().dot(magnetic);
            double shell = Math.max(1.05, rel.length() / body.radius() / Math.max(0.02, 1 - sinLat * sinLat));
            double colat = Math.toDegrees(Math.asin(1 / Math.sqrt(shell)));
            double spreadHalf = Math.toRadians(12 + 55 * Math.min(1, age / 240));
            if (!begun) {
                var shader = SpaceShaders.aurora();
                RenderSystem.setShader(SpaceShaders::aurora);
                shader.safeGetUniform("AnimTime").set((float) ((net.minecraft.Util.getMillis() / 1000.0) % 10000));
                shader.safeGetUniform("LowColor").set(red.low()[0], red.low()[1], red.low()[2]);
                shader.safeGetUniform("MidColor").set(red.mid()[0], red.mid()[1], red.mid()[2]);
                shader.safeGetUniform("HighColor").set(red.high()[0], red.high()[1], red.high()[2]);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                RenderSystem.disableCull();
                RenderSystem.depthMask(false);
                builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                begun = true;
            }
            for (int hemisphere = 0; hemisphere < 2; hemisphere++) {
                DVec3 axis = hemisphere == 0 ? magnetic : magnetic.scale(-1);
                DVec3 helper = Math.abs(axis.y()) < 0.9 ? DVec3.Y : DVec3.X;
                DVec3 e1 = axis.cross(helper).normalize();
                DVec3 e2 = axis.cross(e1).normalize();
                double phiBurst = Math.atan2(rel.dot(e2), rel.dot(e1));
                for (int arc = 0; arc < 2; arc++) {
                    arc(builder, matrix, s, centre, eye, body.radius(), axis, e1, e2, colat + (arc - 0.5) * 1.5, red, t, 11 + arc + hemisphere * 2,
                            sunFromBody, strength * (arc == 0 ? 1f : 0.6f), phiBurst, spreadHalf);
                }
            }
        }
        if (begun) {
            var mesh = builder.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
            RenderSystem.defaultBlendFunc();
        }
    }

    /** One curtain round the oval: quads from its bottom edge to its top, where they can be seen. */
    private static boolean arc(BufferBuilder builder, Matrix4f matrix, SkyState s, DVec3 centre, DVec3 eye, double radius,
                               DVec3 axis, DVec3 e1, DVec3 e2, double colatDeg, Kind k, double t, int seed,
                               DVec3 sunFromBody, float brightness, double phiCentre, double phiHalf) {
        boolean any = false;
        double slow = (net.minecraft.Util.getMillis() / 1000.0) % 100000;
        DVec3[] prev = null;
        float prevLight = 0;
        double prevU = 0;
        for (int i = 0; i <= SEGMENTS; i++) {
            double phi = phiCentre - phiHalf + 2 * phiHalf * i / SEGMENTS;
            // A partial arc fades out at its ends.
            double endFade = phiHalf >= Math.PI ? 1 : Math.sin(Math.PI * i / SEGMENTS);
            // The curtain folds and wanders, slowly.
            double wiggle = Math.sin(phi * 5 + seed * 1.7 + slow * 0.03) * 1.2 + Math.sin(phi * 13 - seed + slow * 0.07) * 0.5
                    + Math.sin(phi * 29 + seed * 3.1 - slow * 0.11) * 0.2;
            double c = Math.toRadians(colatDeg + wiggle);
            DVec3 dir = axis.scale(Math.cos(c)).add(e1.scale(Math.cos(phi) * Math.sin(c))).add(e2.scale(Math.sin(phi) * Math.sin(c)));
            DVec3 bottom = centre.add(dir.scale(radius + k.bottom()));
            DVec3 top = centre.add(dir.scale(radius + k.top()));
            // Only the night side shows (the day side is lost in sunlight), and only what isn't
            // behind the planet from here.
            double night = smoothstep(0.15, -0.25, dir.dot(sunFromBody));
            boolean seen = visible(eye, bottom, centre, radius) || visible(eye, top, centre, radius);
            float light = seen ? (float) (brightness * night * endFade * (0.6 + 0.4 * Math.sin(phi * 3 + seed + slow * 0.05))) : 0;
            DVec3 lowDir = s.sky().toLocal(bottom.sub(eye)).normalize().scale(DOME);
            DVec3 highDir = s.sky().toLocal(top.sub(eye)).normalize().scale(DOME);
            double u = phi * 40;
            // The band of glow the curtain stands in, lying flat at its foot (what orbit sees).
            double half = Math.toRadians(k.width() * 0.5);
            DVec3 inner = axis.scale(Math.cos(c - half)).add(e1.scale(Math.cos(phi) * Math.sin(c - half))).add(e2.scale(Math.sin(phi) * Math.sin(c - half)));
            DVec3 outer = axis.scale(Math.cos(c + half)).add(e1.scale(Math.cos(phi) * Math.sin(c + half))).add(e2.scale(Math.sin(phi) * Math.sin(c + half)));
            double glowHeight = radius + k.bottom() * 1.15;
            DVec3 innerDir = s.sky().toLocal(centre.add(inner.scale(glowHeight)).sub(eye)).normalize().scale(DOME);
            DVec3 midDir = s.sky().toLocal(centre.add(dir.scale(glowHeight)).sub(eye)).normalize().scale(DOME);
            DVec3 outerDir = s.sky().toLocal(centre.add(outer.scale(glowHeight)).sub(eye)).normalize().scale(DOME);
            DVec3[] here = {lowDir, highDir, innerDir, midDir, outerDir};
            if (prev != null && (light > 0.005f || prevLight > 0.005f)) {
                vertex(builder, matrix, prev[0], prevU, 0, prevLight);
                vertex(builder, matrix, prev[1], prevU, 1, prevLight);
                vertex(builder, matrix, here[1], u, 1, light);
                vertex(builder, matrix, here[0], u, 0, light);
                // Band: faded to nothing at both edges, brightest along the middle.
                float bandPrev = prevLight * 0.35f;
                float band = light * 0.35f;
                vertex(builder, matrix, prev[2], prevU, -1, 0);
                vertex(builder, matrix, prev[3], prevU, -1, bandPrev);
                vertex(builder, matrix, here[3], u, -1, band);
                vertex(builder, matrix, here[2], u, -1, 0);
                vertex(builder, matrix, prev[3], prevU, -1, bandPrev);
                vertex(builder, matrix, prev[4], prevU, -1, 0);
                vertex(builder, matrix, here[4], u, -1, 0);
                vertex(builder, matrix, here[3], u, -1, band);
                any = true;
            }
            prev = here;
            prevLight = light;
            prevU = u;
        }
        return any;
    }

    /** True if the straight line from the eye to the point doesn't pass through the planet. */
    private static boolean visible(DVec3 eye, DVec3 point, DVec3 centre, double radius) {
        DVec3 d = point.sub(eye);
        double length = d.length();
        DVec3 dir = d.scale(1 / length);
        DVec3 toCentre = centre.sub(eye);
        double along = toCentre.dot(dir);
        if (along <= 0 || along >= length) {
            return true; // the planet's middle isn't between them
        }
        double miss = toCentre.sub(dir.scale(along)).length();
        return miss > radius * 0.999;
    }

    /** Mars in a solar storm: a faint green glow over the whole night sky, strongest low down. */
    private static void diffuse(PoseStack pose, SkyState s, float strength) {
        if (strength < 0.01f) {
            return;
        }
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int ring = 0; ring < 6; ring++) {
            double e0 = Math.toRadians(ring * 15);
            double e1 = Math.toRadians((ring + 1) * 15);
            float a0 = strength * 0.35f * (1 - ring / 6f);
            float a1 = strength * 0.35f * (1 - (ring + 1) / 6f);
            for (int i = 0; i <= 48; i++) {
                double az = 2 * Math.PI * i / 48;
                builder.addVertex(matrix, (float) (Math.cos(e0) * Math.cos(az) * DOME), (float) (Math.sin(e0) * DOME), (float) (Math.cos(e0) * Math.sin(az) * DOME))
                        .setColor(0.35f, 0.9f, 0.5f, a0);
                builder.addVertex(matrix, (float) (Math.cos(e1) * Math.cos(az) * DOME), (float) (Math.sin(e1) * DOME), (float) (Math.cos(e1) * Math.sin(az) * DOME))
                        .setColor(0.35f, 0.9f, 0.5f, a1);
            }
            BufferUploader.drawWithShader(builder.buildOrThrow());
            builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        }
        builder.build();
        RenderSystem.defaultBlendFunc();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, DVec3 p, double u, float v, float alpha) {
        builder.addVertex(matrix, (float) p.x(), (float) p.y(), (float) p.z()).setUv((float) u, v).setColor(1f, 1f, 1f, Math.min(1f, alpha));
    }

    private static double smoothstep(double a, double b, double x) {
        double tt = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return tt * tt * (3 - 2 * tt);
    }
}
