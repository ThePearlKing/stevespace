package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.network.WeatherS2C;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Earth's weather, place by place: storm systems with a size and a strength, some with thunder,
 * drifting along and coming and going. It rains only under one; /weather clear clears the one
 * you're under, /weather rain or thunder brews one over you. The clouds seen from space are these
 * storms, and so is the lightning flickering in them at night.
 */
public final class WeatherSystems extends SavedData {

    private static final String NAME = "stevespace_weather";
    /** How many storm systems Earth keeps going at once (about a fifth of it under cloud and rain). */
    private static final int STORMS = 48;

    /**
     * One storm: centre (world x, z), radius (blocks), how hard it rains (0..1), thunder or not,
     * drift (blocks per second), and when it started and ends (game ticks).
     */
    public record Storm(int id, double x, double z, double radius, double rain, boolean thunder, double vx, double vz, long born, long ends) {

        /** How far along it is: building up at first, dying away at the end (0..1). */
        public double strength(long now) {
            double life = Math.max(1, ends - born);
            double age = (now - born) / life;
            if (age < 0 || age > 1) {
                return 0;
            }
            return Math.min(1, Math.min(age / 0.12, (1 - age) / 0.2));
        }

        /** How hard it rains at a point (0..1). */
        public double rainAt(double px, double pz, long now, double width) {
            double dx = Math.abs(px - x) % width;
            dx = Math.min(dx, width - dx);
            double d = Math.hypot(dx, pz - z) / radius;
            if (d >= 1) {
                return 0;
            }
            double edge = d < 0.6 ? 1 : 1 - (d - 0.6) / 0.4;
            return rain * strength(now) * edge * edge * (3 - 2 * edge);
        }
    }

    private final List<Storm> storms = new ArrayList<>();
    private int nextId;

    public static WeatherSystems get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WeatherSystems::new, WeatherSystems::load, null), NAME);
    }

    public List<Storm> storms() {
        return storms;
    }

    private static double width(MinecraftServer server) {
        return 2 * Math.PI * ServerUniverse.system().earth().radius();
    }

    /** Rain (0..1) and thunder (0..1) where a player stands. */
    public double[] at(double x, double z, long now, double width) {
        double rain = 0;
        double thunder = 0;
        for (Storm s : storms) {
            double r = s.rainAt(x, z, now, width);
            rain = Math.max(rain, r);
            if (s.thunder()) {
                thunder = Math.max(thunder, r);
            }
        }
        return new double[]{rain, thunder};
    }

    private Storm spawn(MinecraftServer server, RandomSource random, long now, double x, double z, double radius, double rain,
                        boolean thunder, long life) {
        PlanetGeometry geometry = new PlanetGeometry(ServerUniverse.system().earth().radius());
        double lat = geometry.latitude(z);
        // Westerlies at mid latitudes, trade winds (from the east) in the tropics: a slow drift.
        double east = Math.abs(lat) > Math.toRadians(30) ? 0.6 : -0.4;
        Storm s = new Storm(nextId++, x, z, radius, rain, thunder, east + random.nextGaussian() * 0.2, random.nextGaussian() * 0.2, now, now + life);
        storms.add(s);
        setDirty();
        return s;
    }

    /** A new storm somewhere: mostly in the tropics and the middle latitudes, few near the poles. */
    private void spawnSomewhere(MinecraftServer server, RandomSource random, long now) {
        PlanetGeometry geometry = new PlanetGeometry(ServerUniverse.system().earth().radius());
        double lat;
        do {
            lat = Math.asin(random.nextDouble() * 2 - 1);
        } while (random.nextDouble() > 0.25 + 0.75 * Math.pow(Math.cos(lat), 2));
        double lon = random.nextDouble() * 2 * Math.PI - Math.PI;
        double radius = 350 + random.nextDouble() * random.nextDouble() * 1400;
        boolean thunder = random.nextDouble() < (Math.abs(lat) < Math.toRadians(25) ? 0.5 : 0.25);
        long life = (long) (20 * 60 * (8 + random.nextDouble() * 30)); // 8 to 38 minutes
        // Some start already part way through (so a fresh world isn't all just beginning).
        long born = now - (storms.isEmpty() ? (long) (random.nextDouble() * life * 0.8) : 0);
        Storm s = spawn(server, random, born, geometry.xForLongitude(lon), geometry.zForLatitude(lat), radius,
                0.5 + random.nextDouble() * 0.5, thunder, life);
        storms.set(storms.size() - 1, new Storm(s.id(), s.x(), s.z(), s.radius(), s.rain(), s.thunder(), s.vx(), s.vz(), born, born + life));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        // The whole-world rain of the game itself is kept off: rain is where the storms are.
        if (overworld.isRaining() || overworld.isThundering()) {
            overworld.setWeatherParameters(12000, 0, false, false);
        }
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        WeatherSystems data = get(server);
        long now = overworld.getGameTime();
        double width = width(server);
        // Drift, and forget the finished ones.
        List<Storm> moved = new ArrayList<>();
        for (Storm s : data.storms) {
            if (now > s.ends()) {
                continue;
            }
            double x = s.x() + s.vx();
            x = ((x % width) + width * 1.5) % width - width / 2;
            moved.add(new Storm(s.id(), x, s.z() + s.vz(), s.radius(), s.rain(), s.thunder(), s.vx(), s.vz(), s.born(), s.ends()));
        }
        data.storms.clear();
        data.storms.addAll(moved);
        RandomSource random = overworld.getRandom();
        while (data.storms.size() < STORMS) {
            data.spawnSomewhere(server, random, now);
        }
        data.setDirty();
        // Lightning, now and then, wherever someone is under a thunderstorm.
        for (ServerPlayer p : overworld.players()) {
            double[] w = data.at(p.getX(), p.getZ(), now, width);
            if (w[1] > 0.3 && random.nextDouble() < 0.12 * w[1]) {
                strike(overworld, p, random);
            }
        }
        if (server.getTickCount() % 100 == 0) {
            data.send(server, null);
        }
    }

    private static void strike(ServerLevel level, ServerPlayer near, RandomSource random) {
        double a = random.nextDouble() * Math.PI * 2;
        double d = 20 + random.nextDouble() * 140;
        int x = (int) Math.floor(near.getX() + Math.cos(a) * d);
        int z = (int) Math.floor(near.getZ() + Math.sin(a) * d);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            level.addFreshEntity(bolt);
        }
    }

    void send(MinecraftServer server, ServerPlayer only) {
        WeatherS2C msg = new WeatherS2C(server.overworld().getGameTime(), List.copyOf(storms));
        if (only != null) {
            SpaceNetwork.sendTo(only, msg);
        } else {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                SpaceNetwork.sendTo(p, msg);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            get(player.server).send(player.server, player);
        }
    }

    /** /weather works on the storm where you are, not the whole world. */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var parse = event.getParseResults();
        String input = parse.getReader().getString().trim();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        String[] parts = input.split("\\s+");
        if (parts.length < 2 || !parts[0].equals("weather")) {
            return;
        }
        var source = parse.getContext().getSource();
        if (source.getLevel().dimension() != Level.OVERWORLD || !source.hasPermission(2)) {
            return; // elsewhere (or not allowed): the game's own command
        }
        // Where it was asked from: the player, a command block, or the console (the world's spawn).
        double px = source.getPosition().x;
        double pz = source.getPosition().z;
        event.setCanceled(true);
        MinecraftServer server = source.getServer();
        WeatherSystems data = get(server);
        long now = server.overworld().getGameTime();
        double width = width(server);
        long life = 20L * 60 * 10;
        if (parts.length > 2) {
            try {
                life = 20L * Long.parseLong(parts[2].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                // keep ten minutes
            }
        }
        switch (parts[1]) {
            case "clear" -> {
                int cleared = 0;
                for (int i = 0; i < data.storms.size(); i++) {
                    Storm s = data.storms.get(i);
                    double dx = Math.abs(px - s.x()) % width;
                    dx = Math.min(dx, width - dx);
                    if (Math.hypot(dx, pz - s.z()) < s.radius() + 200 && s.ends() > now + 200) {
                        // It breaks up over a few seconds rather than vanishing.
                        long born = now - (long) ((s.ends() - s.born()) * 0.85);
                        data.storms.set(i, new Storm(s.id(), s.x(), s.z(), s.radius(), s.rain(), s.thunder(), s.vx(), s.vz(),
                                born, born + (long) ((now + 200 - born) / 1.0)));
                        cleared++;
                    }
                }
                source.sendSuccess(() -> Component.translatable("stevespace.weather.cleared"), true);
            }
            case "rain", "thunder" -> {
                // Asked for: already in full swing.
                long early = (long) (life * 0.13);
                data.spawn(server, server.overworld().getRandom(), now - early, px, pz, 900, 0.9,
                        parts[1].equals("thunder"), life + early);
                source.sendSuccess(() -> Component.translatable(parts[1].equals("thunder") ? "stevespace.weather.thunder" : "stevespace.weather.rain"), true);
            }
            default -> event.setCanceled(false);
        }
        data.setDirty();
        data.send(server, null);
    }

    private static WeatherSystems load(CompoundTag tag, HolderLookup.Provider registries) {
        WeatherSystems data = new WeatherSystems();
        data.nextId = tag.getInt("next");
        for (Tag t : tag.getList("storms", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.storms.add(new Storm(c.getInt("id"), c.getDouble("x"), c.getDouble("z"), c.getDouble("r"), c.getDouble("rain"),
                    c.getBoolean("thunder"), c.getDouble("vx"), c.getDouble("vz"), c.getLong("born"), c.getLong("ends")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", nextId);
        ListTag list = new ListTag();
        for (Storm s : storms) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", s.id());
            c.putDouble("x", s.x());
            c.putDouble("z", s.z());
            c.putDouble("r", s.radius());
            c.putDouble("rain", s.rain());
            c.putBoolean("thunder", s.thunder());
            c.putDouble("vx", s.vx());
            c.putDouble("vz", s.vz());
            c.putLong("born", s.born());
            c.putLong("ends", s.ends());
            list.add(c);
        }
        tag.put("storms", list);
        return tag;
    }
}
