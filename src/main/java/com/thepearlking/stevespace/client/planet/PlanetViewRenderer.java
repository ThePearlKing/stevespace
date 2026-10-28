package com.thepearlking.stevespace.client.planet;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.client.SpaceShaders;
import com.thepearlking.stevespace.client.sky.SkyState;
import com.thepearlking.stevespace.config.ClientConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Draws the planet you are on (or orbiting) as a real sphere, so that high up you see the ground
 * curve away to a true horizon far beyond the loaded chunks, and from orbit the whole disc with
 * its glowing rim of air.
 *
 * <p>The sphere is drawn during the sky pass, scaled down around the camera so it fits inside the
 * sky's distance (scaling about the eye keeps every angle exact). Real chunks are drawn after it
 * and cover it wherever they exist, so the sphere only shows beyond render distance.
 */
public final class PlanetViewRenderer {

    private static final int LON_SEGMENTS = 192;
    private static final int LAT_SEGMENTS = 96;
    /** The planet centre is drawn this many units from the eye (well inside the far plane). */
    private static final double DRAW_DISTANCE = 60;
    /** Visible thickness of the air rim, as a fraction of the radius. */
    private static final double RIM_THICKNESS = 0.018;

    private static VertexBuffer sphere;

    private PlanetViewRenderer() {
    }

    /**
     * Draws the planet the camera is on or orbiting ({@code s.home()}), seen from wherever the
     * camera is. Works the same on the surface and in space, so the view is continuous.
     */
    public static void render(PoseStack pose, Matrix4f projection, SkyState s) {
        if (!ClientConfig.planetView() || s.home() == null) {
            return;
        }
        CelestialBody body = s.home();
        if (body.parent() == null) {
            return; // orbiting the Sun: the sky draws it, burning, not as a planet
        }
        // Under a cloud deck you see none of it; above it, you see the cloud tops instead.
        var deck = com.thepearlking.stevespace.client.sky.BodyAir.deck(body);
        if (deck != null && s.altitude() < deck.top()) {
            return;
        }
        ResourceLocation map = deck != null ? com.thepearlking.stevespace.client.sky.BodyTextures.get(body) : PlanetMaps.surface(body);
        ShaderInstance surface = SpaceShaders.planetSurface();
        if (map == null || surface == null) {
            return;
        }
        SolarSystem system = s.system();
        double radius = body.radius() + (deck != null ? deck.top() : 0);
        SolarSystem.BodyFrame frame = system.frameAt(body, s.time());
        DVec3 fromCentre = s.sky().position().sub(system.absolutePosition(body, s.time()));
        double distance = fromCentre.length();
        if (distance < radius * 0.5) {
            return;
        }
        // Everything below is in the planet's own rotating frame, in units of its radius.
        DVec3 cameraBody = inBodyFrame(frame, fromCentre).scale(1 / radius);
        DVec3 axisX = inBodyFrame(frame, s.sky().toInertial(new DVec3(1, 0, 0)));
        DVec3 axisY = inBodyFrame(frame, s.sky().toInertial(new DVec3(0, 1, 0)));
        DVec3 axisZ = inBodyFrame(frame, s.sky().toInertial(new DVec3(0, 0, 1)));

        // Scale about the eye so the planet's centre sits DRAW_DISTANCE away: angles stay exact.
        double k = DRAW_DISTANCE / distance;
        Matrix4f model = bodyToLocal(axisX, axisY, axisZ, cameraBody, radius * k);
        // A lumpy world (Vesta, Pallas, Haumea's egg): its sphere stretched to its real shape from
        // far off, easing back to round close in, where the ground you fly over is the round world's
        // (stretched there, its long ends would cut through the view right round you).
        double[] shape = body.shape();
        double radii = cameraBody.length(); // from the middle
        double far = Math.max(0, Math.min(1, (radii - 1.3) / 1.7));
        far = far * far * (3 - 2 * far);
        model.scale((float) (1 + (shape[0] - 1) * far), (float) (1 + (shape[1] - 1) * far), (float) (1 + (shape[2] - 1) * far));

        DVec3 sun = inBodyFrame(frame, system.absolutePosition(system.sun(), s.time())
                .sub(system.absolutePosition(body, s.time())).normalize());
        float[] haze = averageHorizon(s);
        float hazeDensity = body.hasAtmosphere() ? 0.25f + 1.35f * s.density() : 0f;
        Matrix4f modelView = new Matrix4f(pose.last().pose()).mul(model);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShader(SpaceShaders::planetSurface);
        RenderSystem.setShaderTexture(0, map);
        surface.safeGetUniform("SunDir").set((float) sun.x(), (float) sun.y(), (float) sun.z());
        surface.safeGetUniform("CameraPos").set((float) cameraBody.x(), (float) cameraBody.y(), (float) cameraBody.z());
        surface.safeGetUniform("HazeColor").set(haze[0], haze[1], haze[2]);
        surface.safeGetUniform("SelfGlow").set(body.id().equals("acheron") ? 0.32f : 0f);
        surface.safeGetUniform("HazeDensity").set(hazeDensity);
        surface.safeGetUniform("Brightness").set(1.0f);
        VertexBuffer mesh = meshFor(body);
        mesh.bind();
        mesh.drawWithShader(modelView, projection, surface);
        VertexBuffer.unbind();
        // Big buildings: all of them from space or high up; lower down, the ones past the loaded
        // ground (close by, their real blocks are in view instead). On a small world you can see
        // past the loaded ground from only a little way up.
        boolean high = s.altitude() > 400 || com.thepearlking.stevespace.world.SpaceDimension.isSpace(net.minecraft.client.Minecraft.getInstance().level);
        double loaded = high ? 0 : net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 - 8;
        LandmarkRenderer.draw(body, new Matrix4f(pose.last().pose()).mul(bodyToLocal(axisX, axisY, axisZ, cameraBody, radius * k)),
                projection, cameraBody, sun, loaded);
        mesh.bind();

        // Earth's clouds, once you are above them: a shell at cloud height.
        ShaderInstance clouds = SpaceShaders.planetClouds();
        if (clouds != null && deck == null && PlanetClouds.has(body) && s.altitude() > PlanetClouds.HANDOVER_Y - 63) {
            ResourceLocation cloudMap = PlanetClouds.texture(body);
            if (cloudMap != null) {
                float cloudShell = (float) ((radius + PlanetClouds.CLOUD_Y - 63) / radius);
                Matrix4f cloudModelView = new Matrix4f(pose.last().pose()).mul(bodyToLocal(axisX, axisY, axisZ, cameraBody, radius * k * cloudShell));
                DVec3 cameraShell = cameraBody.scale(1 / cloudShell);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShader(SpaceShaders::planetClouds);
                RenderSystem.setShaderTexture(0, cloudMap);
                // Where the storms are: there the clouds close up into solid decks.
                ResourceLocation weather = com.thepearlking.stevespace.client.ClientWeather.map();
                if (weather != null) {
                    RenderSystem.setShaderTexture(1, weather);
                }
                clouds.safeGetUniform("SunDir").set((float) sun.x(), (float) sun.y(), (float) sun.z());
                clouds.safeGetUniform("CameraPos").set((float) cameraShell.x(), (float) cameraShell.y(), (float) cameraShell.z());
                var mc = net.minecraft.client.Minecraft.getInstance();
                double ticks = mc.level == null ? 0 : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
                clouds.safeGetUniform("Drift").set(PlanetClouds.drift(ticks));
                // Fade in over the first few dozen blocks above the clouds.
                clouds.safeGetUniform("Opacity").set((float) Math.min(1, (s.altitude() - (PlanetClouds.HANDOVER_Y - 63)) / 40 + 0.5));
                mesh.drawWithShader(cloudModelView, projection, clouds);
                RenderSystem.disableBlend();

            }
        }

        // The glowing rim of air, once the sky above has started to thin out.
        ShaderInstance atmosphere = SpaceShaders.planetAtmosphere();
        boolean glows = body.id().equals("acheron");
        float rimStrength = glows ? 0.55f : body.hasAtmosphere() ? (1 - s.density()) * 1.4f : 0;
        if (atmosphere != null && rimStrength > 0.01f) {
            float shell = (float) (1 + (glows ? 0.12 : RIM_THICKNESS));
            Matrix4f shellModelView = new Matrix4f(pose.last().pose()).mul(bodyToLocal(axisX, axisY, axisZ, cameraBody, radius * k * shell));
            int c = glows ? 0x16C14C : body.atmosphere().color();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(SpaceShaders::planetAtmosphere);
            atmosphere.safeGetUniform("SunDir").set((float) sun.x(), (float) sun.y(), (float) sun.z());
            atmosphere.safeGetUniform("CameraPos").set((float) cameraBody.x(), (float) cameraBody.y(), (float) cameraBody.z());
            atmosphere.safeGetUniform("AtmosphereColor").set(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
            atmosphere.safeGetUniform("Strength").set(rimStrength);
            atmosphere.safeGetUniform("ShellRadius").set(shell);
            atmosphere.safeGetUniform("Glow").set(glows ? 1f : 0f);
            mesh.drawWithShader(shellModelView, projection, atmosphere);
            RenderSystem.defaultBlendFunc();
        }
        VertexBuffer.unbind();
        // Towers taller than the clouds stand up through them.
        if (deck == null && PlanetClouds.has(body) && (s.altitude() > 400
                || com.thepearlking.stevespace.world.SpaceDimension.isSpace(net.minecraft.client.Minecraft.getInstance().level))) {
            LandmarkRenderer.draw(body, new Matrix4f(pose.last().pose()).mul(bodyToLocal(axisX, axisY, axisZ, cameraBody, radius * k)),
                    projection, cameraBody, sun, (int) PlanetClouds.CLOUD_Y + 4);
        }
        RenderSystem.enableBlend();
    }

    /** Components of an ecliptic-frame vector along the planet's (meridian, east90, north) axes. */
    private static DVec3 inBodyFrame(SolarSystem.BodyFrame frame, DVec3 v) {
        return new DVec3(v.dot(frame.meridian()), v.dot(frame.east90()), v.dot(frame.north()));
    }

    /**
     * Matrix taking a unit-sphere point p (planet frame) to camera-local axes:
     * local = scale · axes · (p − cameraPos/radius·…) — written out as rotation, then translation.
     */
    private static Matrix4f bodyToLocal(DVec3 axisX, DVec3 axisY, DVec3 axisZ, DVec3 cameraBody, double scale) {
        Matrix4f rotation = new Matrix4f();
        rotation.setRow(0, new Vector4f((float) axisX.x(), (float) axisX.y(), (float) axisX.z(), 0));
        rotation.setRow(1, new Vector4f((float) axisY.x(), (float) axisY.y(), (float) axisY.z(), 0));
        rotation.setRow(2, new Vector4f((float) axisZ.x(), (float) axisZ.y(), (float) axisZ.z(), 0));
        rotation.setRow(3, new Vector4f(0, 0, 0, 1));
        // Scale about the eye: the sphere has radius `scale`, its centre is at -cameraBody·scale.
        return new Matrix4f().scale((float) scale).mul(rotation)
                .translate((float) -cameraBody.x(), (float) -cameraBody.y(), (float) -cameraBody.z());
    }

    /** Colour of the air seen over the planet: the horizon colour, or from space the planet's own sky tint. */
    private static float[] averageHorizon(SkyState s) {
        if (s.palette() == null) {
            com.thepearlking.stevespace.client.sky.SkyPalette own = com.thepearlking.stevespace.client.sky.SkyPalette.forBody(s.home().id());
            return own == null ? new float[3] : own.horizon().clone();
        }
        float[] sum = new float[3];
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            float[] c = s.horizonColor((float) Math.cos(a), (float) Math.sin(a));
            for (int j = 0; j < 3; j++) {
                sum[j] += c[j] / 4;
            }
        }
        return sum;
    }

    private static final java.util.Map<String, VertexBuffer> lumpy = new java.util.HashMap<>();

    /** The unit sphere, or for a small world its real outline, bumped out where its ground is. */
    private static VertexBuffer meshFor(CelestialBody body) {
        float[] relief = PlanetMaps.relief(body);
        if (relief == null) {
            return sphere();
        }
        return lumpy.computeIfAbsent(body.id(), id -> {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < LAT_SEGMENTS; i++) {
                double lat0 = Math.PI / 2 - i * Math.PI / LAT_SEGMENTS;
                double lat1 = Math.PI / 2 - (i + 1) * Math.PI / LAT_SEGMENTS;
                for (int j = 0; j < LON_SEGMENTS; j++) {
                    double lon0 = -Math.PI + j * 2 * Math.PI / LON_SEGMENTS;
                    double lon1 = -Math.PI + (j + 1) * 2 * Math.PI / LON_SEGMENTS;
                    vertex(builder, lat0, lon0, relief);
                    vertex(builder, lat1, lon0, relief);
                    vertex(builder, lat1, lon1, relief);
                    vertex(builder, lat0, lon1, relief);
                }
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
            return buffer;
        });
    }

    /** Unit sphere in the planet frame (x = longitude 0, z = north), UV = equirectangular map. */
    private static VertexBuffer sphere() {
        if (sphere == null) {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int i = 0; i < LAT_SEGMENTS; i++) {
                double lat0 = Math.PI / 2 - i * Math.PI / LAT_SEGMENTS;
                double lat1 = Math.PI / 2 - (i + 1) * Math.PI / LAT_SEGMENTS;
                for (int j = 0; j < LON_SEGMENTS; j++) {
                    double lon0 = -Math.PI + j * 2 * Math.PI / LON_SEGMENTS;
                    double lon1 = -Math.PI + (j + 1) * 2 * Math.PI / LON_SEGMENTS;
                    vertex(builder, lat0, lon0);
                    vertex(builder, lat1, lon0);
                    vertex(builder, lat1, lon1);
                    vertex(builder, lat0, lon1);
                }
            }
            sphere = new VertexBuffer(VertexBuffer.Usage.STATIC);
            sphere.bind();
            sphere.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
        }
        return sphere;
    }

    private static void vertex(BufferBuilder builder, double lat, double lon) {
        vertex(builder, lat, lon, null);
    }

    private static void vertex(BufferBuilder builder, double lat, double lon, float[] relief) {
        double r = relief == null ? 1 : 1 + PlanetMaps.reliefAt(relief, lat, lon);
        float x = (float) (r * Math.cos(lat) * Math.cos(lon));
        float y = (float) (r * Math.cos(lat) * Math.sin(lon));
        float z = (float) (r * Math.sin(lat));
        builder.addVertex(x, y, z).setUv((float) ((lon + Math.PI) / (2 * Math.PI)), (float) ((Math.PI / 2 - lat) / Math.PI));
    }
}
