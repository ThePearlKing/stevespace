package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.client.space.ClientSpace;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.GiantAir;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Random;

/**
 * Inside a giant planet: the cloud decks you fly between, as a ceiling overhead and a floor
 * below, streaming past as you go and curving away at the horizon; and lightning down in the
 * lower decks, lighting the clouds from inside, with thunder rolling in after.
 */
public final class GiantClouds {

    /** How far the decks are drawn (blocks), and the size of one tile of cloud texture. */
    private static final double REACH = 12000;
    private static final double TILE = 1600;
    private static final float DRAWN = 80;
    private static final int RINGS = 28;
    private static final int SEGMENTS = 56;

    private static ResourceLocation texture;
    private static boolean failed;
    private static final Random RANDOM = new Random();
    private static double nextFlash;
    private static double flashTime = -10;
    private static double flashAngle;
    private static double flashDistance;
    private static double flashHeight;
    private static float flashPower;
    private static double thunderAt = -1;
    private static float thunderVolume;

    private GiantClouds() {
    }

    /** For the visual test: the next flash comes {@code seconds} from now, close by. */
    public static void flashIn(double seconds) {
        nextFlash = com.thepearlking.stevespace.client.ClientUniverse.time() + seconds;
        forceNear = true;
        holdFlash = true;
    }

    private static boolean forceNear;
    /** The test's flash stays lit (a screenshot can't catch a real one's tenth of a second). */
    private static boolean holdFlash;
    private static boolean held;

    /** Draws the decks round a vessel flying inside a giant (map mode), over the murk. */
    public static void render(PoseStack pose, SkyState s) {
        CelestialBody body = s.home();
        GiantAir.Layers layers = GiantAir.of(body);
        if (layers == null || !ClientSpace.mapModeActive() || !ready()) {
            return;
        }
        double altitude = s.altitude();
        var map = ClientSpace.mapPosition();
        double seconds = s.time();
        double latitude = new com.thepearlking.stevespace.world.planet.PlanetGeometry(body.radius()).latitude(map.z);
        // The jets: bands of wind east and west, by latitude.
        double wind = 140 * Math.cos(latitude * 14);
        // Kept to one tile (the clock and the map position are far too big for a float texture coordinate).
        double u0 = frac(map.x / TILE) + frac(wind * frac(seconds / 86400) * 86400 / TILE);
        double v0 = frac(map.z / TILE);
        GiantAir.Deck[] decks = layers.decks();
        float[] air = BodyAir.lit(BodyAir.giantAir(layers, altitude), s.sunDir().y());
        double light = BodyAir.giantLight(layers, altitude);
        for (int i = 0; i < decks.length; i++) {
            GiantAir.Deck d = decks[i];
            if (altitude > d.top()) {
                // The nearest deck below: its tops, lit from above.
                deck(pose, s, d.top() - altitude, d.color(), (float) (light * 1.0), air, u0, v0, body.radius());
                break;
            }
            if (altitude >= d.base()) {
                break; // inside it: nothing to see but cloud
            }
            boolean nextBelow = i + 1 < decks.length && altitude > decks[i + 1].top();
            boolean last = i + 1 == decks.length;
            if (nextBelow || last) {
                // Between this deck and the next one down: this one is the ceiling.
                deck(pose, s, d.base() - altitude, d.color(), (float) (light * 0.78), air, u0 + 0.37, v0 + 0.61, body.radius());
            }
        }
        lightning(pose, s, layers, altitude, air);
    }

    /** One deck as a sheet {@code height} blocks above (+) or below (-) the eye. */
    private static void deck(PoseStack pose, SkyState s, double height, int color, float shade, float[] air,
                             double u0, double v0, double radius) {
        Matrix4f matrix = pose.last().pose();
        float[] c = BodyAir.lit(new float[]{((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f},
                s.sunDir().y());
        double scale = DRAWN / REACH;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int ring = 0; ring < RINGS; ring++) {
            double r0 = REACH * Math.pow((double) ring / RINGS, 2);
            double r1 = REACH * Math.pow((double) (ring + 1) / RINGS, 2);
            for (int seg = 0; seg < SEGMENTS; seg++) {
                double a0 = seg * 2 * Math.PI / SEGMENTS;
                double a1 = (seg + 1) * 2 * Math.PI / SEGMENTS;
                vertex(builder, matrix, r0, a0, height, radius, scale, c, shade, air, u0, v0);
                vertex(builder, matrix, r0, a1, height, radius, scale, c, shade, air, u0, v0);
                vertex(builder, matrix, r1, a1, height, radius, scale, c, shade, air, u0, v0);
                vertex(builder, matrix, r1, a0, height, radius, scale, c, shade, air, u0, v0);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, double r, double a, double height, double radius,
                               double scale, float[] c, float shade, float[] air, double u0, double v0) {
        double x = r * Math.cos(a);
        double z = r * Math.sin(a);
        // The deck curves away with the planet: it drops below a level line toward the horizon.
        double y = height - r * r / (2 * radius);
        // Far off, the deck fades into the murk between (how far depends on how high you are).
        double clear = Math.min(REACH, 1500 + Math.abs(height) * 9);
        float fade = (float) smoothstep(clear * 0.25, clear, Math.sqrt(r * r + height * height));
        float[] mixed = new float[3];
        for (int i = 0; i < 3; i++) {
            mixed[i] = Math.min(1, c[i] * shade * (1 - fade) + air[i] * fade);
        }
        // The sheet's own edge fades out, so from above the clouds the planet beyond carries on.
        float edge = 1 - (float) smoothstep(REACH * 0.6, REACH, r);
        // High over the top deck the sheet gives way to the planet's own banded clouds.
        edge *= 1 - (float) smoothstep(-300, -1200, height);
        builder.addVertex(matrix, (float) (x * scale), (float) (y * scale), (float) (z * scale))
                .setUv((float) (u0 + x / TILE), (float) (v0 + z / TILE))
                .setColor(mixed[0], mixed[1], mixed[2], edge);
    }

    /**
     * Lightning in the lower decks: a flash lights a patch of cloud from inside, flickering, and
     * the thunder follows (later the further off it was). Seen from higher up, the flashes glow
     * through the decks below, far off and silent.
     */
    private static void lightning(PoseStack pose, SkyState s, GiantAir.Layers layers, double altitude, float[] air) {
        double now = s.time();
        GiantAir.Deck water = layers.decks()[2];
        GiantAir.Deck middle = layers.decks()[1];
        if (altitude > middle.top() + 1500) {
            return;
        }
        if (now >= nextFlash || nextFlash - now > 30) {
            // Flashes come every few seconds down among the storms, rarer higher up.
            double near = 1 - smoothstep(water.top(), middle.top() + 1500, altitude);
            nextFlash = now + (1.5 + RANDOM.nextDouble() * 5) / Math.max(0.15, near);
            flashTime = now;
            flashAngle = RANDOM.nextDouble() * 2 * Math.PI;
            flashDistance = forceNear ? 900 : 400 + RANDOM.nextDouble() * 5000;
            held = holdFlash;
            forceNear = false;
            holdFlash = false;
            // In the water deck, or under you in it if you are higher.
            flashHeight = water.base() + RANDOM.nextDouble() * (water.top() - water.base()) - altitude;
            flashPower = (float) (0.6 + RANDOM.nextDouble() * 0.4);
            double away = Math.sqrt(flashDistance * flashDistance + flashHeight * flashHeight);
            if (away < 4000) {
                thunderAt = now + away / 900;
                thunderVolume = (float) (1.6 * (1 - away / 4000) * flashPower);
            }
        }
        if (thunderAt > 0 && now >= thunderAt) {
            thunderAt = -1;
            var player = Minecraft.getInstance().player;
            if (player != null) {
                Minecraft.getInstance().level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, thunderVolume, 0.55f + RANDOM.nextFloat() * 0.15f, false);
            }
        }
        double age = held && now - flashTime < 20 ? 0 : now - flashTime;
        if (age < 0 || age > 0.6) {
            return;
        }
        // A main stroke, then a flicker or two.
        float flicker = (float) (Math.exp(-age / 0.07) + 0.6 * Math.exp(-Math.pow((age - 0.22) / 0.04, 2))
                + 0.4 * Math.exp(-Math.pow((age - 0.4) / 0.05, 2)));
        float glow = flashPower * flicker;
        Matrix4f matrix = pose.last().pose();
        double scale = DRAWN / REACH;
        double cx = flashDistance * Math.cos(flashAngle);
        double cz = flashDistance * Math.sin(flashAngle);
        double size = 900 + Math.abs(flashHeight) * 0.6;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = 24;
        float cy = (float) (flashHeight * scale);
        for (int i = 0; i < n; i++) {
            double a0 = i * 2 * Math.PI / n;
            double a1 = (i + 1) * 2 * Math.PI / n;
            builder.addVertex(matrix, (float) (cx * scale), cy, (float) (cz * scale)).setColor(0.85f, 0.88f, 1f, Math.min(1, glow));
            builder.addVertex(matrix, (float) ((cx + Math.cos(a0) * size) * scale), cy, (float) ((cz + Math.sin(a0) * size) * scale))
                    .setColor(0.7f, 0.75f, 1f, 0f);
            builder.addVertex(matrix, (float) ((cx + Math.cos(a1) * size) * scale), cy, (float) ((cz + Math.sin(a1) * size) * scale))
                    .setColor(0.7f, 0.75f, 1f, 0f);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        // Close by, the whole murk lights up for an instant.
        double away = Math.sqrt(flashDistance * flashDistance + flashHeight * flashHeight);
        float sky = (float) (glow * 0.35 * Math.max(0, 1 - away / 3500));
        if (sky > 0.01f) {
            builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float d = DRAWN * 0.9f;
            float[][] faces = {{-d, -d, -d, d, -d, -d, d, d, -d, -d, d, -d}, {-d, -d, d, d, -d, d, d, d, d, -d, d, d},
                    {-d, -d, -d, -d, d, -d, -d, d, d, -d, -d, d}, {d, -d, -d, d, d, -d, d, d, d, d, -d, d},
                    {-d, d, -d, d, d, -d, d, d, d, -d, d, d}, {-d, -d, -d, d, -d, -d, d, -d, d, -d, -d, d}};
            for (float[] f : faces) {
                for (int i = 0; i < 4; i++) {
                    builder.addVertex(matrix, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(0.8f, 0.82f, 1f, sky);
                }
            }
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }
        RenderSystem.defaultBlendFunc();
    }

    /** The cloud texture: soft billows, tiling, made once. */
    private static boolean ready() {
        if (texture != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            int size = 256;
            NativeImage image = new NativeImage(size, size, true);
            Random random = new Random(1979); // Voyager's year at Jupiter
            int[] cells = {8, 16, 32, 64};
            double[][] grids = new double[cells.length][];
            for (int o = 0; o < cells.length; o++) {
                grids[o] = new double[cells[o] * cells[o]];
                for (int i = 0; i < grids[o].length; i++) {
                    grids[o][i] = random.nextDouble();
                }
            }
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double v = 0;
                    double amp = 0.5;
                    for (int o = 0; o < cells.length; o++) {
                        v += amp * sample(grids[o], cells[o], x * cells[o] / (double) size, y * cells[o] / (double) size);
                        amp *= 0.5;
                    }
                    // Billows: rounded tops, darker gaps between them.
                    double billow = Math.pow(Math.max(0, Math.min(1, (v - 0.3) / 0.6)), 0.8);
                    int k = (int) (255 * (0.62 + 0.38 * billow));
                    image.setPixelRGBA(x, y, 0xFF000000 | (k << 16) | (k << 8) | k);
                }
            }
            DynamicTexture dynamic = new DynamicTexture(image);
            texture = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "giant_clouds");
            Minecraft.getInstance().getTextureManager().register(texture, dynamic);
            dynamic.bind();
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            return true;
        } catch (Exception e) {
            StevesSpace.LOGGER.warn("No giant clouds: {}", e.toString());
            failed = true;
            return false;
        }
    }

    /** Smooth value noise on a wrapping grid. */
    private static double sample(double[] grid, int cells, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = grid[Math.floorMod(y0, cells) * cells + Math.floorMod(x0, cells)];
        double b = grid[Math.floorMod(y0, cells) * cells + Math.floorMod(x0 + 1, cells)];
        double c = grid[Math.floorMod(y0 + 1, cells) * cells + Math.floorMod(x0, cells)];
        double d = grid[Math.floorMod(y0 + 1, cells) * cells + Math.floorMod(x0 + 1, cells)];
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
