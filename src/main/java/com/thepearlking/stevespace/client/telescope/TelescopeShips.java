package com.thepearlking.stevespace.client.telescope;

import com.mojang.blaze3d.platform.NativeImage;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.TelescopeView;
import com.thepearlking.stevespace.network.SkyShipsC2S;
import com.thepearlking.stevespace.network.SkyShipsS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.radio.SkyShips;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ships out in space that can be seen from here: the server lists them every few seconds (every
 * second at the eyepiece); the ones carrying a radio show in the target list. Each shows as its silhouette, blurred as much as the distance
 * and the air make it.
 */
public final class TelescopeShips {

    /** The finest detail the telescope resolves through the air, in radians (about 8 arcseconds). */
    public static final double RESOLUTION = 4e-5;
    private static final int TEX = 64;

    private static List<SkyShips.Ship> ships = List.of();
    private static long received;
    private static int ticks;
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, DynamicTexture> IMAGES = new HashMap<>();
    private static final Map<String, Integer> BLUR = new HashMap<>();

    private TelescopeShips() {
    }

    public static void receive(SkyShipsS2C msg) {
        ships = msg.ships();
        SkyShipModels.prune(ships);
        received = System.currentTimeMillis();
    }

    public static List<SkyShips.Ship> ships() {
        return ships;
    }

    public static SkyShips.Ship find(String id) {
        for (SkyShips.Ship s : ships()) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        // Ships can be seen with the naked eye too: keep the list fresh (faster at the eyepiece).
        boolean looking = TelescopeView.active() || mc.screen instanceof TelescopeScreen;
        if (ticks++ % (looking ? 20 : 60) == 0) {
            SpaceNetwork.sendToServer(new SkyShipsC2S());
        }
    }

    /** Where a ship is right now, in the ecliptic frame (blocks), or null. */
    public static DVec3 position(SkyShips.Ship ship) {
        SolarSystem system = ClientUniverse.system();
        CelestialBody body = system.body(ship.body());
        if (body == null) {
            return null;
        }
        double t = ClientUniverse.time();
        return system.absolutePosition(body, t).add(ship.relative(t));
    }

    /** How brightly the Sun lights it: dim in its planet's shadow. */
    public static float light(SkyShips.Ship ship) {
        SolarSystem system = ClientUniverse.system();
        CelestialBody body = system.body(ship.body());
        if (body == null) {
            return 1;
        }
        double t = ClientUniverse.time();
        DVec3 rel = ship.relative(t);
        DVec3 sun = system.absolutePosition(body, t).scale(-1).normalize();
        double along = rel.dot(sun);
        double across = rel.sub(sun.scale(along)).length();
        return along < 0 && across < body.radius() ? 0.06f : 1;
    }

    /** Blur, in silhouette pixels, for a ship this big in the sky. */
    public static double blur(double angularRadius) {
        return 1.0 + SkyShips.SIZE * RESOLUTION / Math.max(1e-12, angularRadius * 2);
    }

    /** The ship's picture, blurred by {@code sigma} silhouette pixels. */
    public static ResourceLocation texture(SkyShips.Ship ship, double sigma) {
        int bucket = (int) Math.min(40, Math.round(sigma * 2));
        ResourceLocation id = TEXTURES.get(ship.id());
        Integer had = BLUR.get(ship.id());
        if (id != null && had != null && had == bucket) {
            return id;
        }
        NativeImage image = render(ship.pixels(), bucket / 2.0);
        DynamicTexture texture = IMAGES.get(ship.id());
        if (texture == null) {
            texture = new DynamicTexture(image);
            id = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "telescope_ship/" + Integer.toHexString(ship.id().hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            IMAGES.put(ship.id(), texture);
            TEXTURES.put(ship.id(), id);
        } else {
            texture.setPixels(image);
            texture.upload();
        }
        BLUR.put(ship.id(), bucket);
        return TEXTURES.get(ship.id());
    }

    /** Silhouette scaled into the middle of a TEX×TEX image with room for the blur, then blurred. */
    private static NativeImage render(int[] pixels, double sigma) {
        int n = SkyShips.SIZE;
        double scale = TEX * 0.7 / n;
        double sig = Math.max(0.35, sigma * scale);
        float[][] c = new float[4][TEX * TEX];
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                int sx = (int) Math.floor((x - TEX * 0.15) / scale);
                int sy = (int) Math.floor((y - TEX * 0.15) / scale);
                int argb = sx >= 0 && sy >= 0 && sx < n && sy < n ? pixels[sy * n + sx] : 0;
                float a = (argb >>> 24) / 255f;
                int i = y * TEX + x;
                c[0][i] = a * ((argb >> 16) & 255) / 255f;
                c[1][i] = a * ((argb >> 8) & 255) / 255f;
                c[2][i] = a * (argb & 255) / 255f;
                c[3][i] = a;
            }
        }
        int radius = (int) Math.ceil(sig * 3);
        float[] kernel = new float[radius * 2 + 1];
        float sum = 0;
        for (int k = -radius; k <= radius; k++) {
            kernel[k + radius] = (float) Math.exp(-k * k / (2 * sig * sig));
            sum += kernel[k + radius];
        }
        for (int k = 0; k < kernel.length; k++) {
            kernel[k] /= sum;
        }
        for (int ch = 0; ch < 4; ch++) {
            c[ch] = pass(pass(c[ch], kernel, radius, true), kernel, radius, false);
        }
        NativeImage image = new NativeImage(TEX, TEX, false);
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                int i = y * TEX + x;
                float a = c[3][i];
                int r = a > 1e-4 ? clamp(c[0][i] / a) : 0;
                int g = a > 1e-4 ? clamp(c[1][i] / a) : 0;
                int b = a > 1e-4 ? clamp(c[2][i] / a) : 0;
                image.setPixelRGBA(x, y, clamp(a) << 24 | b << 16 | g << 8 | r); // ABGR
            }
        }
        return image;
    }

    private static float[] pass(float[] in, float[] kernel, int radius, boolean horizontal) {
        float[] out = new float[in.length];
        for (int y = 0; y < TEX; y++) {
            for (int x = 0; x < TEX; x++) {
                float v = 0;
                for (int k = -radius; k <= radius; k++) {
                    int xx = horizontal ? x + k : x;
                    int yy = horizontal ? y : y + k;
                    if (xx >= 0 && yy >= 0 && xx < TEX && yy < TEX) {
                        v += in[yy * TEX + xx] * kernel[k + radius];
                    }
                }
                out[y * TEX + x] = v;
            }
        }
        return out;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    /** The ship's average colour, for list swatches and far-off points. */
    public static int color(SkyShips.Ship ship) {
        long r = 0, g = 0, b = 0, n = 0;
        for (int p : ship.pixels()) {
            if (p >>> 24 != 0) {
                r += (p >> 16) & 255;
                g += (p >> 8) & 255;
                b += p & 255;
                n++;
            }
        }
        return n == 0 ? 0xB0B0B0 : (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }
}
