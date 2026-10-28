package com.thepearlking.stevespace.client.planet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.client.SpaceShaders;
import com.thepearlking.stevespace.client.sky.SkyState;
import com.thepearlking.stevespace.config.ClientConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import com.thepearlking.stevespace.world.planet.PlanetTerrain;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * The ground beyond the loaded chunks, out to the horizon: hills, maria and craters shaped from the
 * body's own terrain, lit by the real Sun, and curving away with the planet. Rings of vertices
 * around the camera get further apart with distance, so the detail on screen stays even. The mesh
 * is rebuilt in the background as the player moves.
 */
public final class FarTerrain {

    private static final int SEGMENTS = 384;
    /** Each ring is this much further out than the one inside it. */
    private static final double GROWTH = 1.035;
    private static final double MAX_RADIUS = 6000;
    /** Rebuild once the camera is this far from the mesh centre. */
    private static final double REBUILD_DISTANCE = 40;

    private record Built(String body, double centerX, double centerZ, int innerRadius, double outerRadius, MeshData mesh,
                         ByteBufferBuilder memory) {
    }

    private static VertexBuffer buffer;
    private static String meshBody;
    private static double meshX;
    private static double meshZ;
    private static int meshInner;
    private static double meshOuter;
    private static CompletableFuture<Built> job;

    private FarTerrain() {
    }

    /** Drawn in the sky pass, before the chunks (which cover it wherever they're loaded). */
    public static void render(PoseStack pose, Matrix4f projection, SkyState s, Vec3 camera) {
        CelestialBody body = s.home();
        ShaderInstance shader = SpaceShaders.farTerrain();
        if (!ClientConfig.planetView() || body == null || shader == null
                || com.thepearlking.stevespace.client.sky.BodyAir.deck(body) != null
                || com.thepearlking.stevespace.world.SpaceDimension.isSpace(Minecraft.getInstance().level)) {
            return;
        }
        PlanetTerrain terrain = terrain(body);
        if (terrain == null) {
            return;
        }
        int inner = Math.max(32, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 - 24);
        double outer = Math.min(MAX_RADIUS, terrain.geometry().poleDistance() * 0.9);
        double radius = body.radius();
        double altitude = camera.y - 63;
        // High enough that the horizon lies past the mesh: the whole-planet view takes over.
        double handOver = Math.pow(outer - inner, 2) / (2 * radius);
        if (altitude > handOver) {
            return;
        }
        boolean stale = buffer == null || !body.id().equals(meshBody) || inner != meshInner
                || Math.hypot(camera.x - meshX, camera.z - meshZ) > REBUILD_DISTANCE;
        if (stale && job == null) {
            String id = body.id();
            double cx = camera.x;
            double cz = camera.z;
            job = CompletableFuture.supplyAsync(() -> build(id, terrain, cx, cz, inner, outer), Util.backgroundExecutor());
        }
        if (job != null && job.isDone()) {
            Built built = job.getNow(null);
            job = null;
            if (built != null) {
                upload(built);
            }
        }
        if (buffer == null || !body.id().equals(meshBody)) {
            return;
        }

        // Flat near the chunks while low; blending into the true sphere toward the hand-over.
        double flat = meshInner * Math.max(0, Math.min(1, (handOver - altitude) / (0.5 * handOver)));
        if (WorldCurve.radius() > 0) {
            flat = 0; // a small world's chunks are bent too (WorldCurve): bend from right here, the same way
        }
        double scale = 60 / meshOuter;
        var sun = s.sunDir();
        float[] haze = haze(s);

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(SpaceShaders::farTerrain);
        shader.safeGetUniform("CameraOffset").set((float) (camera.x - meshX), (float) altitude, (float) (camera.z - meshZ));
        shader.safeGetUniform("PlanetRadius").set((float) radius);
        shader.safeGetUniform("FlatRadius").set((float) flat);
        shader.safeGetUniform("DrawScale").set((float) scale);
        shader.safeGetUniform("SunDir").set((float) sun.x(), (float) sun.y(), (float) sun.z());
        shader.safeGetUniform("HazeColor").set(haze[0], haze[1], haze[2]);
        shader.safeGetUniform("HazeDistance").set(haze[3]);
        shader.safeGetUniform("Ambient").set(body.hasAtmosphere() ? 0.12f : 0.035f);
        buffer.bind();
        buffer.drawWithShader(pose.last().pose(), projection, shader);
        VertexBuffer.unbind();
        // Ships and bases out past the loaded chunks, standing on this same ground.
        FarStructures.draw(pose, projection, shader, meshX, meshZ);
        // Storm clouds out here too, so the hills in front hide them.
        com.thepearlking.stevespace.client.ClientWeather.drawFar(pose.last().pose(), projection, (float) scale, flat, camera);
        RenderSystem.depthMask(true); // or the clear leaves the far ground's depth, and it covers the chunks
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
    }

    /** Colour of the air toward the horizon and how far you can see through it (0 = airless). */
    private static float[] haze(SkyState s) {
        CelestialBody body = s.home();
        if (!body.hasAtmosphere() || s.palette() == null) {
            return new float[4];
        }
        float[] c = s.horizonColor(1, 0);
        float[] d = s.horizonColor(-1, 0);
        double density = body.atmosphere().density() * s.density();
        float distance = (float) Math.max(300, 9000 / (0.6 + density * 3));
        return new float[]{(c[0] + d[0]) / 2, (c[1] + d[1]) / 2, (c[2] + d[2]) / 2, distance};
    }

    private static PlanetTerrain cachedTerrain;
    private static String cachedTerrainBody;

    private static long undergroundAt = Long.MIN_VALUE;
    private static boolean undergroundThere;

    /**
     * Whether the camera is down inside the ground (a cave, a mine, below bedrock): there the
     * distant ground and the planet seen from above must not show through the gaps. Judged by the
     * world's own ground here, not by buildings (indoors you still see out of the windows).
     */
    public static boolean underground(CelestialBody body, Vec3 camera) {
        var level = Minecraft.getInstance().level;
        if (level == null || body == null || com.thepearlking.stevespace.world.SpaceDimension.isSpace(level)) {
            return false;
        }
        if (camera.y < level.getMinBuildHeight()) {
            return true;
        }
        long key = net.minecraft.core.BlockPos.asLong((int) Math.floor(camera.x), (int) Math.floor(camera.y), (int) Math.floor(camera.z));
        if (key != undergroundAt) {
            undergroundAt = key;
            PlanetTerrain terrain = terrain(body);
            if (terrain == null) {
                undergroundThere = false;
            } else {
                var sample = new com.thepearlking.stevespace.world.planet.TerrainSample();
                terrain.sample(camera.x, camera.z, sample);
                undergroundThere = camera.y < sample.height - 4;
            }
        }
        return undergroundThere;
    }

    private static PlanetTerrain terrain(CelestialBody body) {
        if (body.id().equals("earth")) {
            // Earth's heights come from the integrated server's generator (single player only).
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (!(cachedTerrain instanceof EarthFarTerrain earth) || server == null || !earth.sameWorld(server.overworld())) {
                cachedTerrain = EarthFarTerrain.create();
                cachedTerrainBody = cachedTerrain == null ? null : "earth";
            }
            return cachedTerrain;
        }
        if (!body.id().equals(cachedTerrainBody)) {
            cachedTerrainBody = body.id();
            cachedTerrain = PlanetMaps.terrainOf(body);
        }
        return cachedTerrain;
    }

    private static void upload(Built built) {
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        buffer.bind();
        buffer.upload(built.mesh());
        VertexBuffer.unbind();
        built.memory().close();
        meshBody = built.body();
        meshX = built.centerX();
        meshZ = built.centerZ();
        meshInner = built.innerRadius();
        meshOuter = built.outerRadius();
    }

    /** Samples the terrain on rings around (cx, cz) and builds the mesh (background thread). */
    private static Built build(String body, PlanetTerrain terrain, double cx, double cz, int inner, double outer) {
        long start = System.nanoTime();
        int rings = (int) Math.ceil(Math.log(outer / inner) / Math.log(GROWTH)) + 1;
        double[] radii = new double[rings];
        for (int i = 0; i < rings; i++) {
            radii[i] = Math.min(outer, inner * Math.pow(GROWTH, i));
        }
        PlanetGeometry geometry = terrain.geometry();
        int sea = terrain.seaLevel();
        float[][] x = new float[rings][SEGMENTS];
        float[][] z = new float[rings][SEGMENTS];
        float[][] h = new float[rings][SEGMENTS];
        int[][] color = new int[rings][SEGMENTS];
        IntStream.range(0, rings).parallel().forEach(i -> {
            TerrainSample sample = new TerrainSample();
            for (int j = 0; j < SEGMENTS; j++) {
                double a = j * 2 * Math.PI / SEGMENTS;
                double dx = Math.cos(a) * radii[i];
                double dz = Math.sin(a) * radii[i];
                PlanetGeometry.Wrapped w = geometry.wrap(cx + dx, cz + dz);
                terrain.sample(w.x(), w.z(), sample);
                double ground = sample.topOfIce();
                if (sea != Integer.MIN_VALUE && ground < sea) {
                    ground = sea;
                }
                x[i][j] = (float) dx;
                z[i][j] = (float) dz;
                h[i][j] = (float) (ground - 63);
                color[i][j] = terrain.surfaceColor(sample);
            }
        });
        ByteBufferBuilder memory = new ByteBufferBuilder((rings - 1) * SEGMENTS * 4 * DefaultVertexFormat.POSITION_COLOR_NORMAL.getVertexSize());
        BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (int i = 0; i < rings - 1; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                int j1 = (j + 1) % SEGMENTS;
                // Counter-clockwise seen from above: outward ring first, then around.
                vertex(builder, x, z, h, color, i, j, rings);
                vertex(builder, x, z, h, color, i, j1, rings);
                vertex(builder, x, z, h, color, i + 1, j1, rings);
                vertex(builder, x, z, h, color, i + 1, j, rings);
            }
        }
        MeshData mesh = builder.build();
        StevesSpace.LOGGER.debug("Far terrain for {}: {} rings to {} blocks in {} ms", body, rings, (int) outer,
                (System.nanoTime() - start) / 1_000_000);
        return new Built(body, cx, cz, inner, outer, mesh, memory);
    }

    private static void vertex(BufferBuilder builder, float[][] x, float[][] z, float[][] h, int[][] color, int i, int j, int rings) {
        // Normal from the neighbouring vertices (across the ring and along it).
        int in = Math.max(0, i - 1);
        int out = Math.min(rings - 1, i + 1);
        int prev = (j + SEGMENTS - 1) % SEGMENTS;
        int next = (j + 1) % SEGMENTS;
        float ax = x[out][j] - x[in][j];
        float ay = h[out][j] - h[in][j];
        float az = z[out][j] - z[in][j];
        float bx = x[i][next] - x[i][prev];
        float by = h[i][next] - h[i][prev];
        float bz = z[i][next] - z[i][prev];
        // b × a points up for rings counted outward and segments counter-clockwise from above.
        float nx = by * az - bz * ay;
        float ny = bz * ax - bx * az;
        float nz = bx * ay - by * ax;
        if (ny < 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        int c = color[i][j];
        builder.addVertex(x[i][j], h[i][j], z[i][j])
                .setColor(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, 1f)
                .setNormal(nx / len, ny / len, nz / len);
    }
}
