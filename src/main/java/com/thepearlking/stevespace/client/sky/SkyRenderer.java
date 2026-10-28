package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.client.SpaceShaders;
import com.thepearlking.stevespace.client.planet.PlanetViewRenderer;
import com.thepearlking.stevespace.config.ClientConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SkyView;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the sky seen from a planet's surface: the atmosphere, the stars, the Sun, the other
 * planets as points of light, nearby moons as lit spheres, and (high up) the planet itself.
 * Positions are real for the current time and the camera's latitude and longitude.
 *
 * <p>Everything is drawn at a fixed distance of about 100 blocks with depth writes off, scaled to
 * its angular size, so nothing is ever clipped by the far plane.
 */
public final class SkyRenderer {

    private static final float SKY_RADIUS = 100;
    private static final float BODY_DISTANCE = 90;
    /** Bodies smaller than this angular radius are drawn as points of light. */
    private static final double DOT_THRESHOLD = Math.toRadians(0.03);
    private static final int DOME_SEGMENTS = 40;
    private static final int DOME_RINGS = 24;
    private static final int SPHERE_LON = 32;
    private static final int SPHERE_LAT = 16;

    private static final StarField STARS = new StarField();

    private SkyRenderer() {
    }

    /** The inverse of the orientation of the block ship the player is in (identity otherwise). */
    public static Matrix4f shipTurn(float partialTick) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return new Matrix4f();
        }
        var ship = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(player);
        if (ship == null || !ship.materialized()) {
            return new Matrix4f();
        }
        return new Matrix4f().rotation(ship.renderOrientation(partialTick).conjugate(new org.joml.Quaternionf()));
    }

    /**
     * Renders the whole sky. Returns false (letting vanilla draw its sky) when the universe is not
     * synced yet or this dimension is not a planet surface.
     */
    public static boolean render(ClientLevel level, float partialTick, Camera camera, Matrix4f frameMatrix) {
        SkyState state = SkyState.compute(camera, level, partialTick);
        if (state == null || SpaceShaders.skyGlow() == null) {
            return false;
        }
        if (camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) {
            return true; // under water (or lava, or snow): no sky to see, only the fog
        }
        SkyTextures.ensureCreated();
        PoseStack pose = new PoseStack();
        // Inside a ship that is blocks in space, the blocks never turn: the sky turns instead, the
        // opposite way to the ship (everything out there is in the ship's flight axes).
        Matrix4f shipTurn = shipTurn(partialTick);
        pose.mulPose(new Matrix4f(frameMatrix).mul(shipTurn));
        if (com.thepearlking.stevespace.debug.ClientTest.sampleSky) {
            // Where the Moon is drawn relative to the world (before the camera's own turn).
            DVec3 d = state.sky().offsetTo(state.system(), state.system().body("moon"), state.time()).normalize();
            org.joml.Vector3f w = shipTurn.transformDirection(new org.joml.Vector3f((float) d.x(), (float) d.y(), (float) d.z()));
            var shipQ = new org.joml.Quaternionf();
            shipTurn.getNormalizedRotation(shipQ);
            com.thepearlking.stevespace.StevesSpace.LOGGER.info("[ClientTest] sky {} {} {} {} local {} {} {} q {} {} {} {} cam {} {}", System.nanoTime(), w.x, w.y, w.z,
                    d.x(), d.y(), d.z(), shipQ.x, shipQ.y, shipQ.z, shipQ.w, camera.getYRot(), camera.getXRot());
        }
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // Sky geometry is seen from inside and billboards may face either way: draw both sides.
        RenderSystem.disableCull();
        BodyAir.View air = state.home() == null ? null : BodyAir.at(state.home(), state.altitude());
        float sunThrough = air == null ? 1 : air.sun();
        drawAtmosphere(pose, state);
        if (sunThrough > 0.02f) {
            drawStars(pose, projection, state);
            drawBodies(pose, state, sunThrough);
            NukeSky.render(pose, state);
        }
        // The planet and far terrain are drawn shrunk: take the walking bob's shift out for them.
        PoseStack steady = new PoseStack();
        steady.mulPose(ViewBob.untranslate(partialTick).mul(frameMatrix).mul(shipTurn));
        // Standing on the world, the far hills hide what of the aurora is below the horizon; from
        // above, it shines over the world below.
        boolean onGround = !com.thepearlking.stevespace.world.SpaceDimension.isSpace(level);
        if (onGround) {
            AuroraRenderer.render(pose, state);
        }
        // Down in a cave or below the world, the far ground would show through every gap: not drawn there.
        if (!com.thepearlking.stevespace.client.planet.FarTerrain.underground(state.home(), camera.getPosition())) {
            PlanetViewRenderer.render(steady, projection, state);
            com.thepearlking.stevespace.client.planet.FarTerrain.render(steady, projection, state, camera.getPosition());
        }
        PlanetFlashes.render(pose, state);
        if (!onGround) {
            AuroraRenderer.render(pose, state);
        }
        if (air != null && air.cloud() > 0) {
            drawCloud(pose, air, state.sunDir().y());
        }
        GiantClouds.render(steady, state);

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    // ---------------------------------------------------------------------------------------
    // Atmosphere dome
    // ---------------------------------------------------------------------------------------

    private static void drawAtmosphere(PoseStack pose, SkyState s) {
        Matrix4f matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[][][] colors = new float[DOME_RINGS + 1][DOME_SEGMENTS + 1][];
        float[][][] positions = new float[DOME_RINGS + 1][DOME_SEGMENTS + 1][];
        // The horizon drops as you climb, so pack rings densest around the true horizon.
        double horizon = -Math.asin(s.horizonDip());
        for (int ring = 0; ring <= DOME_RINGS; ring++) {
            double v = ring / (double) DOME_RINGS * 2 - 1;
            double shaped = Math.signum(v) * Math.pow(Math.abs(v), 1.6);
            double elevation = shaped >= 0
                    ? horizon + shaped * (Math.PI / 2 - horizon)
                    : horizon + shaped * (Math.PI / 2 + horizon);
            for (int seg = 0; seg <= DOME_SEGMENTS; seg++) {
                double azimuth = seg / (double) DOME_SEGMENTS * 2 * Math.PI;
                float x = (float) (Math.cos(elevation) * Math.cos(azimuth));
                float y = (float) Math.sin(elevation);
                float z = (float) (Math.cos(elevation) * Math.sin(azimuth));
                positions[ring][seg] = new float[]{x * SKY_RADIUS, y * SKY_RADIUS, z * SKY_RADIUS};
                colors[ring][seg] = s.skyColor(x, y, z);
            }
        }
        for (int ring = 0; ring < DOME_RINGS; ring++) {
            for (int seg = 0; seg < DOME_SEGMENTS; seg++) {
                domeVertex(builder, matrix, positions[ring][seg], colors[ring][seg]);
                domeVertex(builder, matrix, positions[ring][seg + 1], colors[ring][seg + 1]);
                domeVertex(builder, matrix, positions[ring + 1][seg + 1], colors[ring + 1][seg + 1]);
                domeVertex(builder, matrix, positions[ring + 1][seg], colors[ring + 1][seg]);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void domeVertex(BufferBuilder builder, Matrix4f matrix, float[] p, float[] c) {
        builder.addVertex(matrix, p[0], p[1], p[2]).setColor(c[0], c[1], c[2], 1);
    }

    // ---------------------------------------------------------------------------------------
    // Stars
    // ---------------------------------------------------------------------------------------

    private static void drawStars(PoseStack pose, Matrix4f projection, SkyState s) {
        float visibility = (1 - Math.min(1, s.skyBrightness() * 4f)) * (1 - s.rain())
                * (float) ClientConfig.starBrightness();
        // Through a telescope zoomed on a planet the stars' glow sprites would fill the view.
        double magnification = com.thepearlking.stevespace.client.TelescopeView.magnification();
        visibility *= (float) Math.max(0, Math.min(1, 1.5 - Math.log10(magnification)));
        if (visibility <= 0.01f) {
            return;
        }
        pose.pushPose();
        pose.mulPose(inertialToLocal(s.sky()));
        RenderSystem.setShader(SpaceShaders::skyGlow);
        RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1, 1, 1, Math.min(1, visibility));
        VertexBuffer buffer = STARS.buffer();
        buffer.bind();
        buffer.drawWithShader(pose.last().pose(), projection, RenderSystem.getShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        pose.popPose();
    }

    /** Matrix turning ecliptic-frame directions into this observer's local sky axes. */
    public static Matrix4f inertialToLocal(SkyView sky) {
        DVec3 east = sky.toInertial(new DVec3(1, 0, 0));
        DVec3 up = sky.toInertial(new DVec3(0, 1, 0));
        DVec3 south = sky.toInertial(new DVec3(0, 0, 1));
        Matrix4f m = new Matrix4f();
        m.setRow(0, new Vector4f((float) east.x(), (float) east.y(), (float) east.z(), 0));
        m.setRow(1, new Vector4f((float) up.x(), (float) up.y(), (float) up.z(), 0));
        m.setRow(2, new Vector4f((float) south.x(), (float) south.y(), (float) south.z(), 0));
        m.setRow(3, new Vector4f(0, 0, 0, 1));
        return m;
    }

    // ---------------------------------------------------------------------------------------
    // Sun, planets, moons
    // ---------------------------------------------------------------------------------------

    private record Visible(CelestialBody body, DVec3 offset, double distance) {
    }

    /** Inside (or just under) a cloud deck: the whole sky is cloud. */
    private static void drawCloud(PoseStack pose, BodyAir.View air, double sunHeight) {
        Matrix4f matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] c = BodyAir.lit(air.cloudColor(), sunHeight);
        int rings = 12;
        int segments = 24;
        for (int ring = 0; ring < rings; ring++) {
            double e0 = -Math.PI / 2 + ring * Math.PI / rings;
            double e1 = -Math.PI / 2 + (ring + 1) * Math.PI / rings;
            for (int seg = 0; seg < segments; seg++) {
                double a0 = seg * 2 * Math.PI / segments;
                double a1 = (seg + 1) * 2 * Math.PI / segments;
                // A little lighter overhead, where the light comes from.
                float k0 = (float) (0.8 + 0.2 * Math.sin(e0));
                float k1 = (float) (0.8 + 0.2 * Math.sin(e1));
                cloudVertex(builder, matrix, e0, a0, c, k0, air.cloud());
                cloudVertex(builder, matrix, e0, a1, c, k0, air.cloud());
                cloudVertex(builder, matrix, e1, a1, c, k1, air.cloud());
                cloudVertex(builder, matrix, e1, a0, c, k1, air.cloud());
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void cloudVertex(BufferBuilder builder, Matrix4f matrix, double elevation, double azimuth, float[] c, float k, float alpha) {
        float x = (float) (Math.cos(elevation) * Math.cos(azimuth)) * SKY_RADIUS * 0.9f;
        float y = (float) Math.sin(elevation) * SKY_RADIUS * 0.9f;
        float z = (float) (Math.cos(elevation) * Math.sin(azimuth)) * SKY_RADIUS * 0.9f;
        builder.addVertex(matrix, x, y, z).setColor(c[0] * k, c[1] * k, c[2] * k, alpha);
    }

    private static void drawBodies(PoseStack pose, SkyState s, float sunThrough) {
        List<Visible> visible = new ArrayList<>();
        for (CelestialBody body : s.system().bodies()) {
            if (body == s.home() && body.parent() != null) {
                continue; // the planet below is drawn on its own (the Sun is always drawn here)
            }
            DVec3 offset = s.sky().offsetTo(s.system(), body, s.time());
            visible.add(new Visible(body, offset, offset.length()));
        }
        // Far to near, so a moon passing in front of the Sun covers it (eclipses).
        visible.sort(Comparator.comparingDouble(Visible::distance).reversed());
        // Through a telescope, bodies show their real size (no artistic scaling), and smaller
        // ones become discs once they are big enough on screen. Zooming in always makes one
        // bigger, though: the naked eye's enlargement fades out gradually (with the square root
        // of the zoom), so it keeps growing on screen until it's at its true size.
        double magnification = com.thepearlking.stevespace.client.TelescopeView.magnification();
        double scale = Math.max(1, ClientConfig.sunMoonScale() / Math.sqrt(magnification));
        for (Visible v : visible) {
            double angularRadius = Math.asin(Math.min(1, v.body().radius() / v.distance()));
            if (v.body() == s.system().sun()) {
                RenderSystem.setShaderColor(1, 1, 1, sunThrough);
                // Drawn larger than life while small (the sky scale setting), never once it's big.
                double sunSize = Math.max(angularRadius, Math.min(angularRadius * scale, Math.toRadians(2)));
                drawSun(pose, s, v, sunSize);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            } else if (angularRadius * magnification < DOT_THRESHOLD) {
                drawPointOfLight(pose, s, v, angularRadius);
            } else {
                double disc = discRadius(v.body(), angularRadius, scale);
                if (v.body().id().equals("acheron")) {
                    drawHalo(pose, v.offset().normalize(), disc, 0.09f, 0.76f, 0.30f, 0.3f);
                }
                drawSphere(pose, s, v, disc);
            }
        }
        drawShips(pose, s, magnification);
    }

    /**
     * Ships out in space, with the naked eye or a telescope: a point of light while too small to
     * make out, and once they're big enough on screen their real blocks, seen from right here,
     * turned the way they really are in their orbit, and lit by the Sun (dark in a planet's shadow).
     */
    private static void drawShips(PoseStack pose, SkyState s, double magnification) {
        // The ship you're in (or the parked one you're standing in) is all round you, not out there.
        var mc = Minecraft.getInstance();
        String own = null;
        String ownCell = null;
        if (mc.player != null) {
            var ship = com.thepearlking.stevespace.vessel.VesselEntity.shipOf(mc.player);
            own = ship == null ? null : "v:" + ship.getUUID();
            if (com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.level)) {
                ownCell = "p:" + com.thepearlking.stevespace.world.ParkedShips.cellAt(mc.player.getX());
            }
        }
        // Ships close enough to be here in the world are drawn as themselves, not again in the sky.
        java.util.Set<String> here = new java.util.HashSet<>();
        if (mc.level != null) {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof com.thepearlking.stevespace.vessel.VesselEntity v) {
                    here.add("v:" + v.getUUID());
                }
            }
        }
        for (com.thepearlking.stevespace.radio.SkyShips.Ship ship : com.thepearlking.stevespace.client.telescope.TelescopeShips.ships()) {
            if (ship.id().equals(own) || ship.id().equals(ownCell) || here.contains(ship.id())) {
                continue;
            }
            var e = com.thepearlking.stevespace.client.telescope.SkyCatalog.ship(s.sky(), ship);
            if (e == null || e.distance() < ship.radius() * 3) {
                continue;
            }
            CelestialBody body = s.system().body(ship.body());
            if (body != null && body != s.home()) {
                DVec3 toBody = s.sky().offsetTo(s.system(), body, s.time());
                double along = toBody.dot(e.direction());
                if (along > 0 && along < e.distance() && toBody.sub(e.direction().scale(along)).length() < body.radius()) {
                    continue; // behind its planet
                }
            }
            double onScreen = Math.toDegrees(e.angularRadius()) * magnification;
            var mesh = onScreen < 0.06 ? null : com.thepearlking.stevespace.client.telescope.SkyShipModels.mesh(ship);
            if (mesh == null || body == null) {
                double limit = 6.5 - 10.0 * Math.min(1, s.skyBrightness() * 1.25) + 2.5 * Math.log10(magnification);
                float a = (float) Math.max(0, Math.min(1, (limit - e.magnitude()) / 2.0)) * (1 - s.rain());
                if (a > 0.01f) {
                    RenderSystem.setShader(SpaceShaders::skyGlow);
                    RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    billboard(pose, e.direction(), (float) (Math.toRadians(0.12) / magnification), 0.9f, 0.9f, 0.9f, a);
                }
                continue;
            }
            drawShipModel(pose, s, ship, body, e, mesh);
        }
        RenderSystem.defaultBlendFunc();
    }

    private static void drawShipModel(PoseStack pose, SkyState s, com.thepearlking.stevespace.radio.SkyShips.Ship ship, CelestialBody body,
                                      com.thepearlking.stevespace.client.telescope.SkyCatalog.Entry e,
                                      com.thepearlking.stevespace.client.vessel.VesselMesh mesh) {
        double t = s.time();
        var state = com.thepearlking.stevespace.universe.Kepler.elementsFromState(ship.state(), ship.mu(), ship.epoch()).stateAt(t, ship.mu());
        // The axes of its orbit cell (east, up and south under it), as seen in this sky.
        DVec3[] axes = com.thepearlking.stevespace.vessel.OrbitalFlight.localAxes(s.system(), body, state, t);
        DVec3 x = s.sky().toLocal(axes[0]);
        DVec3 y = s.sky().toLocal(axes[1]);
        DVec3 z = s.sky().toLocal(axes[2]);
        DVec3 at = e.direction().scale(BODY_DISTANCE);
        float k = (float) (BODY_DISTANCE / e.distance());
        Matrix4f cell = new Matrix4f(
                (float) x.x(), (float) x.y(), (float) x.z(), 0,
                (float) y.x(), (float) y.y(), (float) y.z(), 0,
                (float) z.x(), (float) z.y(), (float) z.z(), 0,
                (float) at.x(), (float) at.y(), (float) at.z(), 1);
        float[] q = ship.turn();
        double[] c = ship.centre();
        Matrix4f model = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()).mul(cell)
                .rotate(new org.joml.Quaternionf(q[0], q[1], q[2], q[3])).scale(k)
                .translate((float) -c[0], (float) -c[1], (float) -c[2]);
        // Sunlit or in shadow, and a little lost in the daytime glare of the air.
        float light = (float) e.phase();
        float lit = 0.12f + 0.88f * light;
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderColor(lit, lit, lit, 1);
        // Its own depth, cleared before and after, so its faces cover each other properly
        // without it standing in front of anything in the world.
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        mesh.draw(model, RenderSystem.getProjectionMatrix(), com.thepearlking.stevespace.client.telescope.SkyShipModels.daylight());
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderFogStart(fogStart);
    }

    /**
     * How big a body's disc is drawn: its real size times the sky scale setting, kept small
     * enough that it (and its rings, which reach well beyond the planet) stay in front of the
     * viewer. Close to Saturn the scaled rings would otherwise reach round behind the camera.
     */
    private static double discRadius(CelestialBody body, double angularRadius, double scale) {
        double reach = body.rings() != null ? body.rings().outerRadius() / body.radius() : 1;
        double limit = Math.min(Math.toRadians(30), Math.atan(0.75 / reach));
        return Math.max(Math.min(angularRadius, limit), Math.min(angularRadius * scale, limit));
    }

    private static void drawSun(PoseStack pose, SkyState s, Visible sun, double angularRadius) {
        DVec3 dir = sun.offset().normalize();
        // Low sun is reddened by the long path through the air.
        float low = 1 - SkyColors.smoothstep(-0.02f, 0.25f, (float) dir.y());
        float airReddening = low * s.density();
        float r = 1.0f;
        float g = 0.97f - 0.4f * airReddening;
        float b = 0.92f - 0.65f * airReddening;

        RenderSystem.setShader(SpaceShaders::skyGlow);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        // Big on screen (close to it, or through a telescope): its real face, boiling, with its
        // spots, flares, prominences and corona. Smaller than that (from Mercury, say), it's a
        // blinding white disc in its glare, as the eye sees it.
        double magnification = com.thepearlking.stevespace.client.TelescopeView.magnification();
        double onScreen = angularRadius * magnification;
        if (SpaceShaders.sun() != null && (onScreen > Math.toRadians(6) || magnification > 1 && onScreen > Math.toRadians(0.6))) {
            // Still smallish in the sky (from Mercury, say): blinding, white-hot, with its glare.
            // The glare fades as it grows, until up close only its face and corona are left.
            float glare = magnification > 1 ? 0 : 1 - SkyColors.smoothstep((float) Math.toRadians(6), (float) Math.toRadians(16), (float) angularRadius);
            if (glare > 0 || s.density() > 0.2 && magnification <= 1) {
                RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
                billboard(pose, dir, (float) Math.toRadians(14), r, g * 0.95f, b * 0.85f, Math.max(glare * 0.3f, 0.18f + 0.22f * s.density()) * (s.density() > 0.2 ? 1 : glare));
            }
            drawSunClose(pose, s, dir, angularRadius);
            if (glare > 0) {
                // A white-hot core over the disc's middle, as the eye sees a bright sun.
                RenderSystem.setShader(SpaceShaders::skyGlow);
                RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                billboard(pose, dir, (float) (angularRadius * 2.6), 1.0f, 0.97f, 0.9f, 0.85f * glare);
            }
            RenderSystem.defaultBlendFunc();
            return;
        }
        // Wide soft glare (stronger in air), then the tight glow, then the disc.
        RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
        billboard(pose, dir, (float) Math.toRadians(14), r, g * 0.95f, b * 0.85f, 0.18f + 0.22f * s.density());
        billboard(pose, dir, (float) (angularRadius * 5), r, g, b, 0.7f);
        RenderSystem.setShaderTexture(0, SkyTextures.DISC);
        billboard(pose, dir, (float) (angularRadius * 1.05), r, g, b, 1.0f);
    }

    /**
     * The Sun big in the sky: a cube round the camera drawn with the sun shader, which traces
     * each pixel's ray to the Sun's surface (or past its edge) itself.
     */
    private static void drawSunClose(PoseStack pose, SkyState s, DVec3 dir, double angularRadius) {
        double t = s.time();
        org.joml.Matrix3f rot = new org.joml.Matrix3f();
        pose.last().pose().get3x3(rot);
        org.joml.Matrix3f inverse = new org.joml.Matrix3f(rot).invert();
        // Columns: where each local axis points in the ecliptic frame.
        DVec3 ex = s.sky().toInertial(DVec3.X);
        DVec3 ey = s.sky().toInertial(DVec3.Y);
        DVec3 ez = s.sky().toInertial(new DVec3(0, 0, 1));
        org.joml.Matrix3f localToSun = new org.joml.Matrix3f(
                (float) ex.x(), (float) ex.y(), (float) ex.z(),
                (float) ey.x(), (float) ey.y(), (float) ey.z(),
                (float) ez.x(), (float) ez.y(), (float) ez.z());
        org.joml.Matrix3f viewToSun = localToSun.mul(inverse, new org.joml.Matrix3f());
        org.joml.Vector3f sunView = rot.transform(new org.joml.Vector3f((float) dir.x(), (float) dir.y(), (float) dir.z())).normalize();

        float[][] spots = new float[3][16];
        int n = 0;
        for (var spot : com.thepearlking.stevespace.universe.SolarActivity.spots(t)) {
            if (n >= 12 || spot.size() < 0.03) {
                continue;
            }
            DVec3 p = onSun(spot.latitude(), spot.longitude());
            float[] m = spots[n / 4];
            int k = (n % 4) * 4;
            m[k] = (float) p.x();
            m[k + 1] = (float) p.y();
            m[k + 2] = (float) p.z();
            m[k + 3] = (float) spot.size();
            n++;
        }
        float[] flares = new float[16];
        int f = 0;
        var recent = com.thepearlking.stevespace.universe.SolarActivity.flares(t, 3600);
        for (int i = recent.size() - 1; i >= 0 && f < 4; i--) {
            var flare = recent.get(i);
            double flash = flare.flash(t);
            if (flash < 0.01) {
                continue;
            }
            DVec3 p = onSun(flare.from().latitude(), flare.from().longitude());
            flares[f * 4] = (float) p.x();
            flares[f * 4 + 1] = (float) p.y();
            flares[f * 4 + 2] = (float) p.z();
            flares[f * 4 + 3] = (float) (flash * (0.5 + 0.5 * flare.strength()));
            f++;
        }

        var shader = SpaceShaders.sun();
        RenderSystem.setShader(SpaceShaders::sun);
        shader.safeGetUniform("SunDir").set(sunView.x, sunView.y, sunView.z);
        shader.safeGetUniform("SinRadius").set((float) Math.sin(Math.min(angularRadius, Math.toRadians(89))));
        double spin = 2 * Math.PI / (25.4 * 86400);
        shader.safeGetUniform("Spin").set((float) ((t * spin) % (2 * Math.PI)));
        shader.safeGetUniform("AnimTime").set((float) ((net.minecraft.Util.getMillis() / 1000.0) % 20000));
        shader.safeGetUniform("Activity").set((float) com.thepearlking.stevespace.universe.SolarActivity.cycle(t));
        shader.safeGetUniform("Haze").set((float) Math.min(1, s.density() * 1.5));
        shader.safeGetUniform("ViewToSun").set(new Matrix4f().set3x3(viewToSun));
        shader.safeGetUniform("SpotsA").set(new Matrix4f().set(spots[0]));
        shader.safeGetUniform("SpotsB").set(new Matrix4f().set(spots[1]));
        shader.safeGetUniform("SpotsC").set(new Matrix4f().set(spots[2]));
        shader.safeGetUniform("Flares").set(new Matrix4f().set(flares));
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        float e = 50;
        float[][] faces = {
                {e, -e, -e, e, e, -e, e, e, e, e, -e, e}, {-e, -e, e, -e, e, e, -e, e, -e, -e, -e, -e},
                {-e, e, -e, -e, e, e, e, e, e, e, e, -e}, {-e, -e, e, -e, -e, -e, e, -e, -e, e, -e, e},
                {-e, -e, e, e, -e, e, e, e, e, -e, e, e}, {e, -e, -e, -e, -e, -e, -e, e, -e, e, e, -e}};
        for (float[] q : faces) {
            for (int i = 0; i < 4; i++) {
                builder.addVertex(matrix, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /** A point on the Sun's surface (unit, ecliptic), from its latitude and turning longitude. */
    private static DVec3 onSun(double latitude, double longitude) {
        return new DVec3(Math.cos(latitude) * Math.cos(longitude), Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude));
    }

    /**
     * A planet too small to show a disc: a point whose brightness follows its real apparent
     * magnitude, so Venus blazes, Jupiter is bright and Neptune is invisible to the naked eye.
     */
    private static void drawPointOfLight(PoseStack pose, SkyState s, Visible v, double angularRadius) {
        double magnitude = apparentMagnitude(s, v);
        // The faintest magnitude you can see: 6.5 at night, about -3.5 in full daylight.
        double limit = 6.5 - 10.0 * Math.min(1, s.skyBrightness() * 1.25);
        // A telescope gathers more light: fainter things show (moons of Jupiter, Neptune).
        double magnification = com.thepearlking.stevespace.client.TelescopeView.magnification();
        limit += 2.5 * Math.log10(magnification);
        double alpha = Math.max(0, Math.min(1, (limit - magnitude) / 2.0)) * (1 - s.rain());
        if (alpha <= 0.01) {
            return;
        }
        // Points keep the same size on screen however far the view is zoomed in.
        double size = Math.max(angularRadius * 2.5, Math.toRadians(0.11 + 0.045 * Math.max(0, 2.5 - magnitude)) / magnification);
        int color = v.body().color();
        float r = 0.55f + 0.45f * ((color >> 16) & 255) / 255f;
        float g = 0.55f + 0.45f * ((color >> 8) & 255) / 255f;
        float b = 0.55f + 0.45f * (color & 255) / 255f;
        RenderSystem.setShader(SpaceShaders::skyGlow);
        RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        billboard(pose, v.offset().normalize(), (float) size, r, g, b, (float) alpha);
    }

    /** Apparent magnitude from size, albedo, distances and phase. */
    private static double apparentMagnitude(SkyState s, Visible v) {
        double au = SolarSystem.AU;
        DVec3 bodyPos = s.system().absolutePosition(v.body(), s.time());
        double sunDistance = bodyPos.length() / au;
        double observerDistance = v.distance() / au;
        double diameterKm = v.body().radius() * 2;
        double absolute = 5 * Math.log10(1329 / (diameterKm * Math.sqrt(Math.max(0.01, v.body().albedo()))));
        DVec3 toSun = bodyPos.scale(-1).normalize();
        DVec3 toObserver = v.offset().scale(-1).normalize();
        double phase = (1 + toSun.dot(toObserver)) / 2;
        return absolute + 5 * Math.log10(sunDistance * observerDistance) - 2.5 * Math.log10(Math.max(1e-4, phase));
    }

    /**
     * A body big enough to show a disc (the Moon from Earth, Earth from the Moon): a textured
     * sphere lit by the Sun, showing its real phase and the face that really points at us, with
     * its rings if it has any. Drawn with its own depth buffer (cleared before and after), so the
     * near side always hides the far side and the planet hides the rings behind it.
     */
    private static void drawSphere(PoseStack pose, SkyState s, Visible v, double angularRadius) {
        SolarSystem system = s.system();
        CelestialBody body = v.body();
        SolarSystem.BodyFrame bodyFrame = system.frameAt(body, s.time());
        DVec3 bodyPos = system.absolutePosition(body, s.time());
        DVec3 sunFromBody = bodyPos.scale(-1).normalize();
        DVec3 centerDir = v.offset().normalize();
        DVec3 center = centerDir.scale(BODY_DISTANCE);
        double radius = BODY_DISTANCE * Math.tan(angularRadius);
        DVec3 toCamera = s.sky().toInertial(centerDir.scale(-1)).normalize();
        // The unlit side shows the sky through it until it is properly dark; at night it hides
        // the stars behind it and shows a faint grey earthshine.
        float night = 1 - Math.min(1, s.skyBrightness() * 4f);
        // Acheron gives off a little light of its own: its dark side never quite disappears.
        float glow = body.id().equals("acheron") ? 0.32f : 0f;
        float litFraction = (float) ((1 + sunFromBody.dot(toCamera)) / 2);

        if (night > 0.05f && litFraction > 0.02f) {
            // Moonlight halo in the night sky, stronger near full moon.
            RenderSystem.setShader(SpaceShaders::skyGlow);
            RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            billboard(pose, centerDir, (float) (angularRadius * 4), 0.75f, 0.8f, 0.9f, 0.22f * night * litFraction);
        }

        // Mesh detail follows the size on screen: through a telescope a planet can fill the view.
        double onScreen = Math.toDegrees(angularRadius) * com.thepearlking.stevespace.client.TelescopeView.magnification();
        int detail = (int) Math.max(1, Math.min(6, Math.ceil(onScreen / 2.0)));
        int latSteps = SPHERE_LAT * detail;
        int lonSteps = SPHERE_LON * detail;
        // Local-sky normals and positions of every grid point.
        DVec3[][] normals = new DVec3[latSteps + 1][lonSteps + 1];
        DVec3[][] points = new DVec3[latSteps + 1][lonSteps + 1];
        double[] lats = new double[latSteps + 1];
        double[] lons = new double[lonSteps + 1];
        for (int i = 0; i <= latSteps; i++) {
            lats[i] = Math.PI / 2 - i * Math.PI / latSteps;
        }
        for (int j = 0; j <= lonSteps; j++) {
            lons[j] = -Math.PI + j * 2 * Math.PI / lonSteps;
        }
        // A small world's outline bumps out where its ground does.
        float[] relief = com.thepearlking.stevespace.client.planet.PlanetMaps.relief(body);
        for (int i = 0; i <= latSteps; i++) {
            for (int j = 0; j <= lonSteps; j++) {
                DVec3 n = bodyFrame.up(lats[i], lons[j]);
                // Lumpy worlds (Vesta, Pallas) keep their real shape.
                normals[i][j] = body.normal(bodyFrame, n);
                double lift = relief == null ? 1 : 1 + com.thepearlking.stevespace.client.planet.PlanetMaps.reliefAt(relief, lats[i], lons[j]);
                points[i][j] = center.add(s.sky().toLocal(body.surface(bodyFrame, n)).scale(radius * lift));
            }
        }

        Matrix4f matrix = pose.last().pose();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BodyTextures.forSky(body));
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (int i = 0; i < latSteps; i++) {
            for (int j = 0; j < lonSteps; j++) {
                // Only the side facing the camera (at the origin); depth sorts out the limb.
                if (!facing(points, center, i, j) && !facing(points, center, i + 1, j)
                        && !facing(points, center, i + 1, j + 1) && !facing(points, center, i, j + 1)) {
                    continue;
                }
                sphereVertex(builder, matrix, points[i][j], normals[i][j], sunFromBody, lats[i], lons[j], night, glow);
                sphereVertex(builder, matrix, points[i + 1][j], normals[i + 1][j], sunFromBody, lats[i + 1], lons[j], night, glow);
                sphereVertex(builder, matrix, points[i + 1][j + 1], normals[i + 1][j + 1], sunFromBody, lats[i + 1], lons[j + 1], night, glow);
                sphereVertex(builder, matrix, points[i][j + 1], normals[i][j + 1], sunFromBody, lats[i], lons[j + 1], night, glow);
                quads++;
            }
        }
        MeshData mesh = builder.build();
        if (mesh != null && quads > 0) {
            BufferUploader.drawWithShader(mesh);
        }
        if (body.rings() != null) {
            RenderSystem.depthMask(false);
            drawRings(pose, s, v, center, radius);
        }
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
    }

    /** Whether the surface at grid point (i, j) faces the camera at the origin. */
    private static boolean facing(DVec3[][] points, DVec3 center, int i, int j) {
        DVec3 p = points[i][j];
        return p.sub(center).dot(p) < 0;
    }

    private static void sphereVertex(BufferBuilder builder, Matrix4f matrix, DVec3 p, DVec3 normal, DVec3 sunFromBody,
                                     double lat, double lon, float night, float glow) {
        // Soft terminator: real bodies have a narrow twilight band, not a hard line.
        double sunAngle = normal.dot(sunFromBody);
        float light = (float) Math.max(0, Math.min(1, (sunAngle + 0.04) / 0.22));
        // Lit side slightly brighter than the texture (a moon in the sky reads as near-white);
        // dark side a faint earthshine at night, transparent by day.
        float brightness = Math.max(glow, 0.08f * night + light * 1.12f);
        float alpha = Math.max(Math.max(light, night), Math.min(1, glow * 3));
        float u = (float) ((lon + Math.PI) / (2 * Math.PI));
        float v = (float) ((Math.PI / 2 - lat) / Math.PI);
        float c = Math.min(1, brightness);
        builder.addVertex(matrix, (float) p.x(), (float) p.y(), (float) p.z()).setUv(u, v).setColor(c, c, c, alpha);
    }

    /**
     * A planet's rings: a flat lit annulus in its equator plane, at the same scale as the sphere
     * just drawn ({@code center}, {@code radius} in sky units). The depth test hides the part
     * behind the planet; the part in front crosses it.
     */
    private static void drawRings(PoseStack pose, SkyState s, Visible v, DVec3 center, double radius) {
        CelestialBody body = v.body();
        CelestialBody.Rings rings = body.rings();
        SolarSystem system = s.system();
        SolarSystem.BodyFrame frame = system.frameAt(body, s.time());
        double toUnits = radius / body.radius(); // body radius -> sky units
        DVec3 a = s.sky().toLocal(frame.meridian());
        DVec3 b = s.sky().toLocal(frame.east90());
        DVec3 sunDir = s.sky().offsetTo(system, system.sun(), s.time()).normalize();
        DVec3 north = s.sky().toLocal(frame.north());
        // Lit from the Sun's side of the ring plane; the far face is dimmer.
        DVec3 toCamera = center.scale(-1).normalize();
        boolean sameSide = sunDir.dot(north) * toCamera.dot(north) > 0;
        float light = (float) ((sameSide ? 0.75 : 0.35) + 0.4 * Math.abs(sunDir.dot(north)));
        int c = rings.color();
        float r = Math.min(1, ((c >> 16) & 255) / 255f * light);
        float g = Math.min(1, ((c >> 8) & 255) / 255f * light);
        float bl = Math.min(1, (c & 255) / 255f * light);
        // Seen through air by day, the rings fade into the sky like the Moon does.
        float alpha = (float) (rings.opacity() * (1 - 0.6 * s.skyBrightness() * s.density()));
        Matrix4f matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 128;
        double inner = rings.innerRadius() * toUnits;
        double outer = rings.outerRadius() * toUnits;
        // A few bands of slightly different density, like the real ones (and a dark gap).
        double[] edges = {0, 0.18, 0.42, 0.62, 0.66, 0.9, 1};
        float[] density = {0.45f, 0.8f, 1f, 0.12f, 0.85f, 0.6f};
        for (int i = 0; i < segments; i++) {
            double t0 = i * 2 * Math.PI / segments;
            double t1 = (i + 1) * 2 * Math.PI / segments;
            DVec3 d0 = a.scale(Math.cos(t0)).add(b.scale(Math.sin(t0)));
            DVec3 d1 = a.scale(Math.cos(t1)).add(b.scale(Math.sin(t1)));
            for (int k = 0; k < density.length; k++) {
                double r0 = inner + (outer - inner) * edges[k];
                double r1 = inner + (outer - inner) * edges[k + 1];
                float al = alpha * density[k];
                DVec3 p0 = center.add(d0.scale(r0));
                DVec3 p1 = center.add(d0.scale(r1));
                DVec3 p2 = center.add(d1.scale(r1));
                DVec3 p3 = center.add(d1.scale(r0));
                builder.addVertex(matrix, (float) p0.x(), (float) p0.y(), (float) p0.z()).setColor(r, g, bl, al);
                builder.addVertex(matrix, (float) p1.x(), (float) p1.y(), (float) p1.z()).setColor(r, g, bl, al);
                builder.addVertex(matrix, (float) p2.x(), (float) p2.y(), (float) p2.z()).setColor(r, g, bl, al);
                builder.addVertex(matrix, (float) p3.x(), (float) p3.y(), (float) p3.z()).setColor(r, g, bl, al);
            }
        }
        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /**
     * A soft glow round a disc: bright at its edge, fading to nothing a fifth of its size further
     * out (Acheron's own green light, as it was seen in the dream).
     */
    private static void drawHalo(PoseStack pose, DVec3 dir, double angularRadius, float r, float g, float b, float a) {
        DVec3 helper = Math.abs(dir.y()) < 0.95 ? DVec3.Y : DVec3.X;
        DVec3 right = dir.cross(helper).normalize();
        DVec3 up = right.cross(dir).normalize();
        double inner = BODY_DISTANCE * Math.tan(Math.min(1.2, angularRadius * 0.9));
        double outer = BODY_DISTANCE * Math.tan(Math.min(1.45, angularRadius * 1.22));
        DVec3 c = dir.scale(BODY_DISTANCE);
        Matrix4f matrix = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int n = 64;
        for (int i = 0; i <= n; i++) {
            double t = i * 2 * Math.PI / n;
            DVec3 out = right.scale(Math.cos(t)).add(up.scale(Math.sin(t)));
            DVec3 p = c.add(out.scale(inner));
            DVec3 q = c.add(out.scale(outer));
            builder.addVertex(matrix, (float) p.x(), (float) p.y(), (float) p.z()).setColor(r, g, b, a);
            builder.addVertex(matrix, (float) q.x(), (float) q.y(), (float) q.z()).setColor(r, g, b, 0f);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.defaultBlendFunc();
    }

    /** A camera-facing square centred on {@code dir}, {@code angularRadius} in size. */
    static void billboard(PoseStack pose, DVec3 dir, float angularRadius, float r, float g, float b, float a) {
        DVec3 helper = Math.abs(dir.y()) < 0.95 ? DVec3.Y : DVec3.X;
        DVec3 right = dir.cross(helper).normalize();
        DVec3 up = right.cross(dir).normalize();
        double half = BODY_DISTANCE * Math.tan(angularRadius);
        DVec3 c = dir.scale(BODY_DISTANCE);
        DVec3 du = right.scale(half);
        DVec3 dv = up.scale(half);
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        corner(builder, matrix, c.sub(du).sub(dv), 0, 0, r, g, b, a);
        corner(builder, matrix, c.sub(du).add(dv), 0, 1, r, g, b, a);
        corner(builder, matrix, c.add(du).add(dv), 1, 1, r, g, b, a);
        corner(builder, matrix, c.add(du).sub(dv), 1, 0, r, g, b, a);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void corner(BufferBuilder builder, Matrix4f matrix, DVec3 p, float u, float v,
                               float r, float g, float b, float a) {
        builder.addVertex(matrix, (float) p.x(), (float) p.y(), (float) p.z()).setUv(u, v).setColor(r, g, b, a);
    }
}
