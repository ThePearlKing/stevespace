package com.thepearlking.stevespace.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.thepearlking.stevespace.client.cloud.CloudRenderer;
import com.thepearlking.stevespace.network.WeatherS2C;
import com.thepearlking.stevespace.world.WeatherSystems;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Earth's storms on this side: the rain and thunder where you stand (only under a storm), the
 * map of them that the clouds seen from space follow, and thunder from far off arriving late
 * (sound is slow) and quiet.
 */
public final class ClientWeather {

    private static List<WeatherSystems.Storm> storms = List.of();
    private static long serverTime;
    private static long receivedAt;
    private static DynamicTexture map;
    private static ResourceLocation mapId;
    private static boolean mapStale = true;
    private static long mapBuiltAt;
    private static final int MAP_W = 512;
    private static final int MAP_H = 256;
    private static final List<Object[]> THUNDER = new ArrayList<>();

    private ClientWeather() {
    }

    public static void receive(WeatherS2C msg) {
        storms = msg.storms();
        serverTime = msg.now();
        receivedAt = System.currentTimeMillis();
        mapStale = true;
    }

    /** Game time now, as the server counts it (carried on from the last update). */
    private static long now() {
        return serverTime + (System.currentTimeMillis() - receivedAt) / 50;
    }

    private static double width() {
        return 2 * Math.PI * ClientUniverse.system().earth().radius();
    }

    /** The storms, moved on to now. */
    public static List<WeatherSystems.Storm> storms() {
        double dt = (System.currentTimeMillis() - receivedAt) / 1000.0;
        List<WeatherSystems.Storm> list = new ArrayList<>(storms.size());
        for (WeatherSystems.Storm s : storms) {
            list.add(new WeatherSystems.Storm(s.id(), s.x() + s.vx() * dt, s.z() + s.vz() * dt, s.radius(), s.rain(), s.thunder(),
                    s.vx(), s.vz(), s.born(), s.ends()));
        }
        return list;
    }

    /** Rain and thunder (0..1) at a place on Earth. */
    public static double[] at(double x, double z) {
        if (ClientUniverse.system() == null) {
            return new double[2];
        }
        double rain = 0;
        double thunder = 0;
        long now = now();
        double width = width();
        for (WeatherSystems.Storm s : storms()) {
            double r = s.rainAt(x, z, now, width);
            rain = Math.max(rain, r);
            if (s.thunder()) {
                thunder = Math.max(thunder, r);
            }
        }
        return new double[]{rain, thunder};
    }

    /** How stormy (thunderclouds) it is at a latitude and longitude (radians), 0..1: where lightning is. */
    public static double thunderAt(double lat, double lon) {
        if (ClientUniverse.system() == null) {
            return 0;
        }
        PlanetGeometry geometry = new PlanetGeometry(ClientUniverse.system().earth().radius());
        double[] w = at(geometry.xForLongitude(lon), geometry.zForLatitude(lat));
        return w[1];
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.level.dimension() == Level.OVERWORLD && ClientUniverse.system() != null
                && !storms.isEmpty()) {
            double[] w = at(mc.player.getX(), mc.player.getZ());
            // Eased, so walking in and out of a storm is gradual.
            float rain = mc.level.getRainLevel(1);
            float thunder = mc.level.getThunderLevel(1);
            rain += (float) Math.max(-0.01, Math.min(0.01, w[0] - rain));
            thunder += (float) Math.max(-0.01, Math.min(0.01, w[1] - thunder));
            mc.level.setRainLevel(rain);
            mc.level.setThunderLevel(thunder);
        }
        // Far thunder, arriving now.
        long t = System.currentTimeMillis();
        THUNDER.removeIf(d -> {
            if ((long) d[0] > t) {
                return false;
            }
            mc.getSoundManager().play((SoundInstance) d[1]);
            return true;
        });
    }

    /**
     * Thunder travels at the speed of sound: a strike a few kilometres off is heard seconds after
     * its flash, and quieter (and deeper) the further it is.
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getOriginalSound();
        if (sound == null || !sound.getLocation().equals(SoundEvents.LIGHTNING_BOLT_THUNDER.getLocation()) || sound instanceof Delayed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return;
        }
        var eye = mc.gameRenderer.getMainCamera().getPosition();
        double d = eye.distanceTo(new net.minecraft.world.phys.Vec3(sound.getX(), sound.getY(), sound.getZ()));
        if (d < 30) {
            return; // right here: as loud and as sudden as it is
        }
        event.setSound(null);
        float volume = (float) Math.max(0.06, Math.min(1, 90 / (d + 40)));
        float pitch = (float) Math.max(0.55, Math.min(1, 1.05 - d / 1200));
        THUNDER.add(new Object[]{System.currentTimeMillis() + Math.round(d / 343.0 * 1000),
                new Delayed(sound.getLocation(), volume, pitch)});
    }

    /** A thunderclap arriving late from far off (no position: it rolls in from all round). */
    private static final class Delayed extends SimpleSoundInstance {
        Delayed(ResourceLocation id, float volume, float pitch) {
            super(id, SoundSource.WEATHER, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                    SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        }
    }

    /**
     * Where the storms are, as a map round the world (equirectangular, longitude across, north at
     * the top): red is cloud cover. The clouds seen from space follow it.
     */
    public static ResourceLocation map() {
        if (ClientUniverse.system() == null) {
            return null;
        }
        if (map == null) {
            map = new DynamicTexture(MAP_W, MAP_H, false);
            map.setFilter(true, false);
            mapId = ResourceLocation.fromNamespaceAndPath(com.thepearlking.stevespace.StevesSpace.MODID, "weather_map");
            Minecraft.getInstance().getTextureManager().register(mapId, map);
        }
        if (mapStale || System.currentTimeMillis() - mapBuiltAt > 4000) {
            draw();
        }
        return mapId;
    }

    private static void draw() {
        mapStale = false;
        mapBuiltAt = System.currentTimeMillis();
        NativeImage image = map.getPixels();
        if (image == null) {
            return;
        }
        PlanetGeometry geometry = new PlanetGeometry(ClientUniverse.system().earth().radius());
        long now = now();
        float[] cover = new float[MAP_W * MAP_H];
        for (WeatherSystems.Storm s : storms()) {
            double strength = s.strength(now);
            if (strength <= 0) {
                continue;
            }
            double lat = geometry.latitude(s.z());
            double lon = geometry.longitude(s.x());
            // Its size in map pixels (wider near the poles, as the map stretches there).
            double perPixelY = Math.PI * geometry.radius() / MAP_H;
            double ry = s.radius() / perPixelY;
            double rx = ry / Math.max(0.05, Math.cos(lat));
            int cx = (int) Math.round((lon + Math.PI) / (2 * Math.PI) * MAP_W);
            int cy = (int) Math.round((Math.PI / 2 - lat) / Math.PI * MAP_H);
            for (int y = (int) (cy - ry - 1); y <= cy + ry + 1; y++) {
                if (y < 0 || y >= MAP_H) {
                    continue;
                }
                for (int x = (int) (cx - rx - 1); x <= cx + rx + 1; x++) {
                    double dx = (x - cx) / rx;
                    double dy = (y - cy) / ry;
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d >= 1) {
                        continue;
                    }
                    double edge = d < 0.5 ? 1 : 1 - (d - 0.5) / 0.5;
                    int i = y * MAP_W + Math.floorMod(x, MAP_W);
                    cover[i] = (float) Math.max(cover[i], strength * edge * (0.75 + 0.25 * s.rain()));
                }
            }
        }
        for (int y = 0; y < MAP_H; y++) {
            for (int x = 0; x < MAP_W; x++) {
                int v = Math.round(Math.min(1, cover[y * MAP_W + x]) * 255);
                image.setPixelRGBA(x, y, 0xFF000000 | v); // ABGR: red channel = cover
            }
        }
        map.upload();
    }

    /** How far off (blocks) a storm's clouds are drawn: as far as you'd see them over the horizon. */
    private static final double SEEN = 7000;

    /**
     * The storms' own clouds, from the ground: a heavy grey deck over each one, piled up into
     * towering heads where there's thunder, so a storm can be seen sitting out on the horizon
     * (and from under it, the sky's a dark lid).
     */
    /** Where a storm's cloud deck starts (blocks, world y). */
    private static final double CLOUD_BASE = 200;
    /** The frame the far clouds were last drawn over the far terrain (then the world pass leaves them out). */
    private static int farFrame = -1;
    private static int frame;

    /**
     * The storms' clouds round the eye (world positions, no drop for the curve of the world yet),
     * leaving out any already gone below the horizon or right over your head.
     */
    private static List<CloudRenderer.Cloud> clouds(net.minecraft.world.phys.Vec3 eye) {
        List<CloudRenderer.Cloud> clouds = new ArrayList<>();
        if (storms.isEmpty() || ClientUniverse.system() == null) {
            return clouds;
        }
        double width = width();
        long now = now();
        double radius = ClientUniverse.system().earth().radius();
        double eyeHorizon = Math.sqrt(2 * radius * Math.max(1, eye.y - 63));
        for (WeatherSystems.Storm s : storms()) {
            double strength = s.strength(now);
            if (strength <= 0.02) {
                continue;
            }
            double dx = s.x() - eye.x;
            dx -= Math.round(dx / width) * width;
            double dz = s.z() - eye.z;
            if (Math.hypot(dx, dz) - s.radius() > SEEN) {
                continue;
            }
            java.util.Random random = new java.util.Random(s.id() * 7919L);
            int n = (int) Math.min(70, 6 + s.radius() * s.radius() / 40000);
            for (int i = 0; i < n; i++) {
                double halfWidth;
                double height;
                boolean anvil = false;
                double a = random.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(random.nextDouble()) * s.radius() * 0.8;
                if (i == 0 && s.thunder()) {
                    // The thunderhead itself, in the middle, flattening out into an anvil.
                    d = 0;
                    height = (260 + 160 * random.nextDouble()) * (0.4 + 0.6 * strength);
                    halfWidth = height * (0.8 + 0.3 * random.nextDouble());
                    anvil = true;
                } else if (s.thunder()) {
                    // Heaped cumulus round it, taller near the middle.
                    double edge = 1 - d / s.radius();
                    height = (50 + 150 * edge * random.nextDouble()) * (0.5 + 0.5 * strength);
                    halfWidth = height * (0.6 + 0.6 * random.nextDouble()) + 30;
                } else {
                    // A rain deck: low, broad and lumpy.
                    height = (25 + 40 * random.nextDouble()) * (0.5 + 0.5 * strength);
                    halfWidth = 70 + 110 * random.nextDouble();
                }
                double px = eye.x + dx + Math.cos(a) * d;
                double pz = eye.z + dz + Math.sin(a) * d;
                // Heaped-up storm clouds are white where the Sun catches them; a rain deck is grey right through.
                float shade = (float) ((s.thunder() ? 0.82 : 0.62) + 0.12 * random.nextDouble() - 0.15 * s.rain());
                int seed = random.nextInt(997);
                double far = Math.hypot(px - eye.x, pz - eye.z);
                if (far > 1.1 * (eyeHorizon + Math.sqrt(2 * radius * Math.max(0, CLOUD_BASE + height - 63)))) {
                    continue; // down past the curve of the world
                }
                if (far < halfWidth * 1.1) {
                    continue; // right over you: the storm's dark sky is all there is to see
                }
                float alpha = (float) Math.min(1, strength * 1.5) * 0.95f;
                clouds.add(new CloudRenderer.Cloud(px, CLOUD_BASE, pz, (float) halfWidth, (float) height,
                        shade, shade, shade * 1.03f, alpha, anvil, seed));
            }
        }
        return clouds;
    }

    private static com.thepearlking.stevespace.client.cloud.PuffRenderer.Light light(float partial) {
        var light = com.thepearlking.stevespace.client.nuke.NukeClient.light(partial);
        return new com.thepearlking.stevespace.client.cloud.PuffRenderer.Light(light.sun(), light.sunColour(), light.ambient(), light.haze(), 9000f);
    }

    private static float animTime() {
        return (float) ((net.minecraft.Util.getMillis() / 1000.0) % 10000);
    }

    /** Past the loaded chunks, clouds are drawn with the far terrain instead (it hides them behind hills). */
    private static double nearReach() {
        return Math.max(32, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 - 24);
    }

    /**
     * The storm clouds out past the loaded chunks, drawn in with the far terrain while its depth is
     * still there: bent down over the world's curve as it is ({@code flat}: how far out it stays flat),
     * shrunk by the same {@code scale}.
     */
    public static void drawFar(org.joml.Matrix4f view, org.joml.Matrix4f projection, float scale, double flat, net.minecraft.world.phys.Vec3 eye) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return;
        }
        farFrame = frame;
        double radius = ClientUniverse.system().earth().radius();
        double reach = nearReach();
        List<CloudRenderer.Cloud> rel = new ArrayList<>();
        for (var c : clouds(eye)) {
            double dx = c.x() - eye.x;
            double dz = c.z() - eye.z;
            double d = Math.hypot(dx, dz);
            if (d <= reach) {
                continue;
            }
            // The same bend as the far terrain's.
            double h = c.y() - 63;
            double bend = Math.max(0, d - flat) / radius;
            double horizontal = d;
            double vertical = h;
            if (bend > 0) {
                horizontal = flat + (radius + h) * Math.sin(bend);
                vertical = (radius + h) * Math.cos(bend) - radius;
            }
            double k = horizontal / Math.max(1e-3, d);
            rel.add(new CloudRenderer.Cloud(dx * k, vertical - (eye.y - 63), dz * k, c.halfWidth(), c.height(),
                    c.r(), c.g(), c.b(), c.a(), c.anvil(), c.seed()));
        }
        CloudRenderer.draw(rel, view, projection, scale, light(mc.getTimer().getGameTimeDeltaPartialTick(false)), animTime());
    }

    @SubscribeEvent
    public static void onRenderStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        // Whether the far terrain took the far clouds this frame.
        boolean farDrawn = farFrame == frame;
        frame++;
        Minecraft mc = Minecraft.getInstance();
        if (storms.isEmpty() || mc.level == null || mc.level.dimension() != Level.OVERWORLD || ClientUniverse.system() == null) {
            return;
        }
        var eye = event.getCamera().getPosition();
        double radius = ClientUniverse.system().earth().radius();
        double reach = nearReach();
        List<CloudRenderer.Cloud> clouds = new ArrayList<>();
        for (var c : clouds(eye)) {
            double far2 = (c.x() - eye.x) * (c.x() - eye.x) + (c.z() - eye.z) * (c.z() - eye.z);
            if (farDrawn && far2 > reach * reach) {
                continue;
            }
            // Far off it sinks below the curve of the world like the hills do.
            clouds.add(new CloudRenderer.Cloud(c.x() - eye.x, c.y() - far2 / (2 * radius) - eye.y, c.z() - eye.z, c.halfWidth(), c.height(),
                    c.r(), c.g(), c.b(), c.a(), c.anvil(), c.seed()));
        }
        if (clouds.isEmpty()) {
            return;
        }
        org.joml.Matrix4f view = new org.joml.Matrix4f().rotation(event.getCamera().rotation().conjugate(new org.joml.Quaternionf()));
        CloudRenderer.draw(clouds, view, null, 1, light(event.getPartialTick()), animTime());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        storms = List.of();
        THUNDER.clear();
    }
}
