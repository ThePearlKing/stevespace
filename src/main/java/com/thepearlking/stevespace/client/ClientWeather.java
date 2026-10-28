package com.thepearlking.stevespace.client;

import com.mojang.blaze3d.platform.NativeImage;
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
    @SubscribeEvent
    public static void onRenderStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || storms.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD || ClientUniverse.system() == null) {
            return;
        }
        var eye = event.getCamera().getPosition();
        double width = width();
        long now = now();
        double radius = ClientUniverse.system().earth().radius();
        List<com.thepearlking.stevespace.client.cloud.PuffRenderer.Puff> puffs = new ArrayList<>();
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
            int n = (int) Math.min(220, 24 + s.radius() * s.radius() / 9000);
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(random.nextDouble()) * s.radius() * 0.85;
                double px = eye.x + dx + Math.cos(a) * d;
                double pz = eye.z + dz + Math.sin(a) * d;
                double edge = 1 - d / s.radius();
                double size = (s.radius() * 0.18) * (0.6 + 0.6 * random.nextDouble()) * (0.5 + 0.5 * strength);
                // A thunderstorm's middle towers up; a rain deck stays low and flat.
                double tower = s.thunder() ? Math.pow(edge, 1.5) * (90 + 140 * random.nextDouble()) * strength : 10 * random.nextDouble();
                double y = 205 + tower * random.nextDouble() + size * 0.15;
                // Far off it sinks below the curve of the world like the hills do.
                double far2 = (px - eye.x) * (px - eye.x) + (pz - eye.z) * (pz - eye.z);
                y -= far2 / (2 * radius);
                double toEye = Math.sqrt(far2 + (y - eye.y) * (y - eye.y));
                if (toEye < size * 1.6) {
                    continue; // right over (or round) you: the storm's dark sky is all there is to see
                }
                float shade = (float) (0.55 + 0.2 * random.nextDouble() - 0.15 * s.rain());
                float alpha = (float) (Math.min(1, strength * 1.4) * (0.55 + 0.35 * edge));
                puffs.add(new com.thepearlking.stevespace.client.cloud.PuffRenderer.Puff(px, y, pz, (float) size, shade, shade, shade * 1.03f,
                        alpha, 0, 0, 0, random.nextInt()));
            }
        }
        if (puffs.isEmpty()) {
            return;
        }
        org.joml.Matrix4f view = new org.joml.Matrix4f().rotation(event.getCamera().rotation().conjugate(new org.joml.Quaternionf()));
        var light = com.thepearlking.stevespace.client.nuke.NukeClient.light(event.getPartialTick());
        com.thepearlking.stevespace.client.cloud.PuffRenderer.draw(puffs, view, event.getCamera(),
                new com.thepearlking.stevespace.client.cloud.PuffRenderer.Light(light.sun(), light.sunColour(), light.ambient(), light.haze(), 9000f),
                (float) ((net.minecraft.Util.getMillis() / 1000.0) % 10000));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        storms = List.of();
        THUNDER.clear();
    }
}
