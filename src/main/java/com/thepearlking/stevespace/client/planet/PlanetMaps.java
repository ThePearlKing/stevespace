package com.thepearlking.stevespace.client.planet;

import com.mojang.blaze3d.platform.NativeImage;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.client.sky.BodyTextures;
import com.thepearlking.stevespace.config.ClientConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.world.planet.earth.EarthClimate;
import com.thepearlking.stevespace.world.planet.earth.EarthSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Whole-planet surface maps (equirectangular, longitude across, north up) for drawing planets
 * from altitude and from space. Earth's comes from the world's real continent layout, generated
 * in the background after joining. Bodies with a fixed terrain (the Moon) get maps drawn from it;
 * the rest use their sky textures.
 */
public final class PlanetMaps {

    private static final ResourceLocation EARTH = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "planet/earth");

    private static long earthSeed;
    private static EarthSettings earthSettings;
    private static boolean earthReady;
    private static int earthWidth;
    private static CompletableFuture<?> earthJob;
    /** Bodies whose map comes from their real terrain: ready, or still being built. */
    private static final Map<String, ResourceLocation> terrainMaps = new HashMap<>();
    private static final java.util.Set<String> terrainJobs = new java.util.HashSet<>();

    private PlanetMaps() {
    }

    /** Called when the server tells us Earth's layout (on joining a world). */
    public static void setEarthLayout(long seed, EarthSettings settings) {
        int width = ClientConfig.textureQuality().width;
        if (seed == earthSeed && settings.equals(earthSettings) && width == earthWidth) {
            return;
        }
        earthWidth = width;
        earthSeed = seed;
        earthSettings = settings;
        earthReady = false;
        int height = width / 2;
        earthJob = CompletableFuture.supplyAsync(() -> renderEarth(seed, settings, width, height), Util.backgroundExecutor())
                .thenAcceptAsync(pixels -> upload(pixels, width, height, seed), Minecraft.getInstance());
    }

    /** The surface map for a body, or null while it is still being generated. */
    public static ResourceLocation surface(CelestialBody body) {
        if (body.id().equals("earth")) {
            if (earthSettings != null && ClientConfig.textureQuality().width != earthWidth) {
                setEarthLayout(earthSeed, earthSettings); // quality changed in the settings
            }
            return earthReady ? EARTH : null;
        }
        ResourceLocation real = terrainMap(body);
        if (real != null) {
            return real;
        }
        return BodyTextures.get(body); // cheap; and the giants' detailed maps swap in once drawn
    }

    /** Grid of a small world's ground heights (see {@link #relief}): longitude across, north at the top. */
    public static final int RELIEF_W = 128;
    public static final int RELIEF_H = 64;
    private static final Map<String, float[]> reliefs = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> reliefJobs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * A small world's real ground, as a fraction of its radius above or below sea level, so its
     * outline from space bumps out where the ground does (Hygiea, the other asteroids, the small
     * moons). Null for big worlds (their hills are nothing at that scale) and while being worked out.
     */
    public static float[] relief(CelestialBody body) {
        if (body.radius() > 1200) {
            return null;
        }
        float[] ready = reliefs.get(body.id());
        if (ready != null || !reliefJobs.add(body.id())) {
            return ready;
        }
        var terrain = terrainOf(body);
        if (terrain == null) {
            return null;
        }
        CompletableFuture.runAsync(() -> reliefs.put(body.id(), buildRelief(terrain)), Util.backgroundExecutor());
        return null;
    }

    private static float[] buildRelief(com.thepearlking.stevespace.world.planet.PlanetTerrain terrain) {
        var geometry = terrain.geometry();
        var s = new com.thepearlking.stevespace.world.planet.TerrainSample();
        double[] heights = new double[RELIEF_W * RELIEF_H];
        for (int y = 0; y < RELIEF_H; y++) {
            double latitude = Math.PI / 2 - (y + 0.5) / RELIEF_H * Math.PI;
            for (int x = 0; x < RELIEF_W; x++) {
                double longitude = (x + 0.5) / RELIEF_W * 2 * Math.PI - Math.PI;
                terrain.sample(geometry.xForLongitude(longitude), geometry.zForLatitude(latitude), s);
                heights[y * RELIEF_W + x] = s.height;
            }
        }
        // Measured from sea level (y 63), the radius everything else uses: the ground drawn from
        // space stands exactly where the real ground is, and buildings (LandmarkRenderer) on it.
        float[] relief = new float[heights.length];
        for (int i = 0; i < heights.length; i++) {
            relief[i] = (float) ((heights[i] - 63) / geometry.radius());
        }
        return relief;
    }

    /** The relief at a latitude and longitude (radians), smoothly between the grid's points. */
    public static double reliefAt(float[] relief, double latitude, double longitude) {
        double fx = (longitude + Math.PI) / (2 * Math.PI) * RELIEF_W - 0.5;
        double fy = (Math.PI / 2 - latitude) / Math.PI * RELIEF_H - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double tx = fx - x0;
        double ty = fy - y0;
        double a = reliefCell(relief, x0, y0) * (1 - tx) + reliefCell(relief, x0 + 1, y0) * tx;
        double b = reliefCell(relief, x0, y0 + 1) * (1 - tx) + reliefCell(relief, x0 + 1, y0 + 1) * tx;
        return a * (1 - ty) + b * ty;
    }

    private static float reliefCell(float[] relief, int x, int y) {
        return relief[Math.max(0, Math.min(RELIEF_H - 1, y)) * RELIEF_W + Math.floorMod(x, RELIEF_W)];
    }

    /** Earth's surface colours from the layout the server sent, or null before it arrives. */
    static EarthSurfaceColors earthColors() {
        if (earthSettings == null) {
            return null;
        }
        if (earthColors == null || earthColorsSeed != earthSeed) {
            earthColors = new EarthSurfaceColors(new EarthClimate(earthSeed, earthSettings), earthSeed);
            earthColorsSeed = earthSeed;
        }
        return earthColors;
    }

    private static EarthSurfaceColors earthColors;
    private static long earthColorsSeed;

    /** The pure terrain of a body the client can compute itself (fixed-seed bodies), or null. */
    public static com.thepearlking.stevespace.world.planet.PlanetTerrain terrainOf(CelestialBody body) {
        return com.thepearlking.stevespace.world.gen.PlanetBodies.terrain(body.id());
    }

    /** A map drawn from the body's real terrain, started in the background on first use. */
    private static ResourceLocation terrainMap(CelestialBody body) {
        ResourceLocation ready = terrainMaps.get(body.id());
        if (ready != null || terrainJobs.contains(body.id())) {
            return ready;
        }
        var terrain = terrainOf(body);
        if (terrain == null) {
            return null;
        }
        terrainJobs.add(body.id());
        int width = Math.min(2048, ClientConfig.textureQuality().width);
        int height = width / 2;
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "planet/" + body.id());
        CompletableFuture.supplyAsync(() -> renderTerrain(body, terrain, width, height), Util.backgroundExecutor())
                .thenAcceptAsync(pixels -> {
                    register(location, pixels, width, height);
                    terrainMaps.put(body.id(), location);
                }, Minecraft.getInstance());
        return null;
    }

    /** Surface colours with hill shading (light from the west), so craters show from orbit. */
    private static int[] renderTerrain(CelestialBody body, com.thepearlking.stevespace.world.planet.PlanetTerrain terrain,
                                       int width, int height) {
        long start = System.nanoTime();
        var geometry = terrain.geometry();
        double circumference = geometry.circumference();
        double step = circumference / width;
        int[] pixels = new int[width * height];
        IntStream.range(0, height).parallel().forEach(py -> {
            var s = new com.thepearlking.stevespace.world.planet.TerrainSample();
            double latitude = Math.PI / 2 - (py + 0.5) / height * Math.PI;
            double z = geometry.zForLatitude(latitude);
            double eastStep = step * Math.max(0.05, Math.cos(latitude));
            for (int px = 0; px < width; px++) {
                double longitude = (px + 0.5) / width * 2 * Math.PI - Math.PI;
                double x = longitude * circumference / (2 * Math.PI);
                terrain.sample(x - eastStep / 2, z, s);
                double west = s.height;
                terrain.sample(x + eastStep / 2, z, s);
                double east = s.height;
                terrain.sample(x, z, s);
                int color = terrain.surfaceColor(s);
                double slope = (west - east) / Math.max(1, eastStep);
                double light = Math.max(0.55, Math.min(1.45, 1 + slope * 0.9));
                int r = (int) Math.min(255, ((color >> 16) & 255) * light);
                int g = (int) Math.min(255, ((color >> 8) & 255) * light);
                int b = (int) Math.min(255, (color & 255) * light);
                pixels[py * width + px] = (r << 16) | (g << 8) | b;
            }
        });
        StevesSpace.LOGGER.info("{} surface map {}x{} built in {} ms", body.id(), width, height, (System.nanoTime() - start) / 1_000_000);
        return pixels;
    }

    public static void clear() {
        earthReady = false;
        earthSettings = null;
        earthSeed = 0;
        earthWidth = 0;
        if (earthJob != null) {
            earthJob.cancel(false);
        }
    }

    private static int[] renderEarth(long seed, EarthSettings settings, int width, int height) {
        long start = System.nanoTime();
        EarthClimate climate = new EarthClimate(seed, settings);
        EarthSurfaceColors colors = new EarthSurfaceColors(climate, seed);
        double circumference = climate.geometry().circumference();
        int[] pixels = new int[width * height];
        IntStream.range(0, height).parallel().forEach(py -> {
            double latitude = Math.PI / 2 - (py + 0.5) / height * Math.PI;
            double z = climate.geometry().zForLatitude(latitude);
            for (int px = 0; px < width; px++) {
                double longitude = (px + 0.5) / width * 2 * Math.PI - Math.PI;
                double x = longitude * circumference / (2 * Math.PI);
                pixels[py * width + px] = colors.colorAt(x, z);
            }
        });
        StevesSpace.LOGGER.info("Earth surface map {}x{} built in {} ms", width, height, (System.nanoTime() - start) / 1_000_000);
        return pixels;
    }

    private static void upload(int[] pixels, int width, int height, long seed) {
        if (seed != earthSeed) {
            return; // a newer layout arrived meanwhile
        }
        register(EARTH, pixels, width, height);
        earthReady = true;
        // Then the relief: Earth's real heights (single player) shade the map, so mountain ranges,
        // ridges and valleys show from orbit the way craters do on the Moon.
        EarthFarTerrain terrain = EarthFarTerrain.create();
        if (terrain != null) {
            CompletableFuture.supplyAsync(() -> shadeRelief(terrain, pixels, width, height), Util.backgroundExecutor())
                    .thenAcceptAsync(shaded -> {
                        if (seed == earthSeed && width == earthWidth) {
                            register(EARTH, shaded, width, height);
                        }
                    }, Minecraft.getInstance());
        }
    }

    /** Heights on a coarse grid over the globe, then light from the west-northwest on every map pixel. */
    private static int[] shadeRelief(EarthFarTerrain terrain, int[] base, int width, int height) {
        long start = System.nanoTime();
        var geometry = terrain.geometry();
        double circumference = geometry.circumference();
        int gw = 512;
        int gh = 256;
        float[] grid = new float[gw * gh];
        IntStream.range(0, gh).parallel().forEach(gy -> {
            var s = new com.thepearlking.stevespace.world.planet.TerrainSample();
            double latitude = Math.PI / 2 - (gy + 0.5) / gh * Math.PI;
            double z = geometry.zForLatitude(latitude);
            for (int gx = 0; gx < gw; gx++) {
                double x = ((gx + 0.5) / gw - 0.5) * circumference;
                terrain.sample(x, z, s);
                grid[gy * gw + gx] = (float) Math.max(63, s.height); // the sea is flat
            }
        });
        int[] out = new int[base.length];
        double cellX = circumference / gw;
        double cellY = circumference / 2 / gh;
        IntStream.range(0, height).parallel().forEach(py -> {
            double gy = (py + 0.5) / height * gh - 0.5;
            double latitude = Math.PI / 2 - (py + 0.5) / height * Math.PI;
            double eastCell = cellX * Math.max(0.05, Math.cos(latitude));
            for (int px = 0; px < width; px++) {
                double gx = (px + 0.5) / width * gw - 0.5;
                double west = gridAt(grid, gw, gh, gx - 0.5, gy);
                double east = gridAt(grid, gw, gh, gx + 0.5, gy);
                double north = gridAt(grid, gw, gh, gx, gy - 0.5);
                double south = gridAt(grid, gw, gh, gx, gy + 0.5);
                double slope = (west - east) / eastCell + 0.5 * (north - south) / cellY;
                double light = Math.max(0.6, Math.min(1.4, 1 + slope * 6));
                int c = base[py * width + px];
                int r = (int) Math.min(255, ((c >> 16) & 255) * light);
                int g = (int) Math.min(255, ((c >> 8) & 255) * light);
                int b = (int) Math.min(255, (c & 255) * light);
                out[py * width + px] = (r << 16) | (g << 8) | b;
            }
        });
        StevesSpace.LOGGER.info("Earth relief {}x{} heights shaded in {} ms", gw, gh, (System.nanoTime() - start) / 1_000_000);
        return out;
    }

    /** Bilinear height from the grid (wrapping east-west, clamped at the poles). */
    private static double gridAt(float[] grid, int gw, int gh, double gx, double gy) {
        gy = Math.max(0, Math.min(gh - 1.001, gy));
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        double fx = gx - x0;
        double fy = gy - y0;
        int xa = Math.floorMod(x0, gw);
        int xb = Math.floorMod(x0 + 1, gw);
        double top = grid[y0 * gw + xa] * (1 - fx) + grid[y0 * gw + xb] * fx;
        double bottom = grid[(y0 + 1) * gw + xa] * (1 - fx) + grid[(y0 + 1) * gw + xb] * fx;
        return top * (1 - fy) + bottom * fy;
    }

    private static void register(ResourceLocation location, int[] pixels, int width, int height) {
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = pixels[y * width + x];
                int abgr = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
                image.setPixelRGBA(x, y, abgr);
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }
}
