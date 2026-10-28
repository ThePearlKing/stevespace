package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.platform.NativeImage;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.world.planet.Shaping;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Surface maps (equirectangular) for drawing bodies as spheres in the sky. Generated once per
 * body from noise: maria and highlands on the Moon, bands and storms on the giants, and so on.
 * Bodies whose terrain the client can compute will later get maps from the real terrain.
 */
public final class BodyTextures {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 128;
    private static final Map<String, ResourceLocation> textures = new HashMap<>();

    private BodyTextures() {
    }

    public static ResourceLocation get(CelestialBody body) {
        ResourceLocation detailed = giants.get(body.id());
        if (detailed != null) {
            return detailed;
        }
        if (com.thepearlking.stevespace.world.planet.GiantPlanets.has(body.id()) && giantJobs.add(body.id())) {
            // The giants get a detailed map, drawn in the background; the plain one stands in meanwhile.
            String id = body.id();
            java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> com.thepearlking.stevespace.world.planet.GiantPlanets.render(id, GIANT_WIDTH, GIANT_WIDTH / 2), net.minecraft.Util.backgroundExecutor())
                    .thenAcceptAsync(pixels -> {
                        NativeImage image = new NativeImage(GIANT_WIDTH, GIANT_WIDTH / 2, false);
                        for (int y = 0; y < GIANT_WIDTH / 2; y++) {
                            for (int x = 0; x < GIANT_WIDTH; x++) {
                                image.setPixelRGBA(x, y, 0xFF000000 | abgr(pixels[y * GIANT_WIDTH + x]));
                            }
                        }
                        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "sky/giant/" + id);
                        DynamicTexture texture = new DynamicTexture(image);
                        texture.setFilter(true, false);
                        Minecraft.getInstance().getTextureManager().register(location, texture);
                        giants.put(id, location);
                    }, Minecraft.getInstance());
        }
        return textures.computeIfAbsent(body.id(), id -> create(body));
    }

    private static final int GIANT_WIDTH = 1024;
    private static final Map<String, ResourceLocation> giants = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> giantJobs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Thick air hides the ground: from outside you see the top of the clouds (Venus, Titan). */
    public static boolean cloudCovered(CelestialBody body) {
        return body.kind().isSolid() && body.hasAtmosphere() && body.atmosphere().density() >= 1.5;
    }

    /** The map to draw a body with when seen from far away: its clouds or its real ground. */
    public static ResourceLocation forSky(CelestialBody body) {
        if (!cloudCovered(body)) {
            ResourceLocation ground = com.thepearlking.stevespace.client.planet.PlanetMaps.surface(body);
            if (ground != null) {
                return ground;
            }
        }
        return get(body);
    }

    private static ResourceLocation create(CelestialBody body) {
        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        long seed = body.id().hashCode();
        SphereNoise large = new SphereNoise(seed, 2.2, 5, 0.55);
        SphereNoise fine = new SphereNoise(seed + 1, 14, 3, 0.5);
        SphereNoise bands = new SphereNoise(seed + 2, 6, 3, 0.5);
        for (int y = 0; y < HEIGHT; y++) {
            double lat = Math.PI / 2 - (y + 0.5) / HEIGHT * Math.PI;
            for (int x = 0; x < WIDTH; x++) {
                double lon = (x + 0.5) / WIDTH * 2 * Math.PI - Math.PI;
                double cx = Math.cos(lat) * Math.cos(lon);
                double cy = Math.sin(lat);
                double cz = Math.cos(lat) * Math.sin(lon);
                int color = colorAt(body, lat, large.sample(cx, cy, cz), fine.sample(cx, cy, cz), bands.sample(cx, cy, cz));
                image.setPixelRGBA(x, y, 0xFF000000 | abgr(color));
            }
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "sky/body/" + body.id());
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return location;
    }

    /** Surface colour for one texel, by the kind of body. */
    private static int colorAt(CelestialBody body, double lat, double large, double fine, double bandNoise) {
        int base = body.color();
        return switch (body.kind()) {
            case GAS_GIANT, ICE_GIANT -> {
                // Latitude bands, warped a little so they are not ruler-straight.
                double band = Math.sin(lat * 14 + bandNoise * 1.6 + large * 0.6);
                double strength = body.kind() == CelestialBody.Kind.GAS_GIANT ? 0.16 : 0.05;
                yield Shaping.shade(base, 1 + band * strength + fine * 0.04);
            }
            case STAR -> base;
            default -> {
                if (cloudCovered(body)) {
                    // A cloud deck: nearly even, with faint broad bands and a soft polar hood.
                    double band = Math.sin(lat * 5 + bandNoise * 1.2 + large * 0.8);
                    double hood = Shaping.smoothstep(0.9, 1.4, lat) * 0.08;
                    yield Shaping.shade(base, 1 + band * 0.035 + large * 0.03 + fine * 0.012 - hood);
                }
                if (body.id().equals("moon")) {
                    // Dark basaltic maria on the near side, bright cratered highlands elsewhere.
                    double maria = Shaping.smoothstep(0.08, 0.22, large);
                    int c = Shaping.mixColor(0xCFCCC6, 0x85837F, maria);
                    yield Shaping.shade(c, 1 + fine * 0.12);
                }
                if (body.id().equals("earth")) {
                    double land = Shaping.smoothstep(0.02, 0.06, large);
                    int c = Shaping.mixColor(0x1E4470, 0x5A7A42, land);
                    double ice = Shaping.smoothstep(1.15, 1.3, Math.abs(lat));
                    yield Shaping.mixColor(c, 0xEEF2F4, ice);
                }
                yield Shaping.shade(base, 1 + large * 0.22 + fine * 0.1);
            }
        };
    }

    private static int abgr(int rgb) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        return (b << 16) | (g << 8) | r;
    }
}
