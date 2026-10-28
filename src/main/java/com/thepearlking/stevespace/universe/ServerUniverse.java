package com.thepearlking.stevespace.universe;

import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.network.UniverseSyncS2C;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the solar system on the server: advances the universe clock, keeps every planet
 * dimension's vanilla day time matched to its real sun, handles sleeping and {@code /time set},
 * and keeps clients in sync.
 */
public final class ServerUniverse {

    private static final int SYNC_INTERVAL_TICKS = 20;
    /** How far the vanilla day time may move in one tick before we treat it as a /time command. */
    private static final long EXTERNAL_CHANGE_THRESHOLD = 200;

    private static SolarSystem system;
    private static final Map<ResourceKey<Level>, Long> lastDayTimeSet = new HashMap<>();

    private ServerUniverse() {
    }

    /** The solar system as currently configured (valid once the server has ticked). */
    public static SolarSystem system() {
        if (system == null) {
            system = new SolarSystem(SpaceConfig.earthDaySeconds());
        }
        return system;
    }

    /**
     * Keeps the solar system in step with the configured day length. A change takes effect from
     * now on without the sky jumping: rotation continues from where it is.
     */
    private static void applyDayLength(MinecraftServer server, UniverseClock clock) {
        double day = SpaceConfig.earthDaySeconds();
        if (clock.dayLength() < 0) {
            clock.setDayLength(day, 0, 0); // a world from before day-length tracking
        } else if (clock.dayLength() != day) {
            double turnsNow = system().earthTurns(clock.time());
            clock.setDayLength(day, clock.time(), turnsNow);
        }
        if (system == null || system.earthDaySeconds() != clock.dayLength()) {
            system = new SolarSystem(clock.dayLength(), clock.rotationAnchorTime(), clock.rotationAnchorTurns());
            SpaceNetwork.sendToAll(syncMessage(clock));
        }
    }

    public static double time(MinecraftServer server) {
        return UniverseClock.get(server).time();
    }

    // ---------------------------------------------------------------------------------------
    // Ticking
    // ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        UniverseClock clock = UniverseClock.get(server);
        applyDayLength(server, clock);
        if (!clock.isStarted()) {
            clock.start(findMorning(server));
        }
        boolean daylightCycle = server.overworld().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        if (daylightCycle || clock.warp() > 1) {
            clock.advance(clock.warp() / 20.0);
        }
        for (ServerLevel level : server.getAllLevels()) {
            updateDayTime(level, clock);
        }
        if (server.getTickCount() % SYNC_INTERVAL_TICKS == 0) {
            SpaceNetwork.sendToAll(syncMessage(clock));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SpaceNetwork.sendTo(player, syncMessage(UniverseClock.get(player.server)));
            ServerLevel overworld = player.server.overworld();
            long layoutSeed = com.thepearlking.stevespace.world.gen.earth.EarthDensityFunctions.seedOf(
                    overworld.getChunkSource().randomState().getOrCreateNoise(net.minecraft.world.level.levelgen.Noises.CONTINENTALNESS));
            SpaceNetwork.sendTo(player, com.thepearlking.stevespace.network.EarthLayoutS2C.of(layoutSeed,
                    com.thepearlking.stevespace.world.gen.earth.EarthWorldgen.settings()));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastDayTimeSet.clear();
        system = null;
    }

    private static UniverseSyncS2C syncMessage(UniverseClock clock) {
        return new UniverseSyncS2C(clock.time(), clock.warp(), system().earthDaySeconds(),
                clock.rotationAnchorTime(), clock.rotationAnchorTurns());
    }

    /** Sets the warp factor and tells every client at once (so their sky speeds up together). */
    public static void setWarp(MinecraftServer server, double warp) {
        UniverseClock clock = UniverseClock.get(server);
        clock.setWarp(warp);
        SpaceNetwork.sendToAll(syncMessage(clock));
    }

    // ---------------------------------------------------------------------------------------
    // Vanilla day time follows the real sun
    // ---------------------------------------------------------------------------------------

    /** The body whose surface this level is, or null (Nether, End, space). */
    public static CelestialBody bodyOf(Level level) {
        return system().bodyForDimension(level.dimension().location().toString());
    }

    private static void updateDayTime(ServerLevel level, UniverseClock clock) {
        CelestialBody body = bodyOf(level);
        if (body == null || level.dimensionType().hasFixedTime()) {
            return;
        }
        Long expected = lastDayTimeSet.get(level.dimension());
        if (expected != null && level.dimension() == Level.OVERWORLD
                && Math.abs(level.getDayTime() - expected) > EXTERNAL_CHANGE_THRESHOLD) {
            // Someone ran /time set or /time add: move the real sun to match.
            moveSunToVanillaTime(level, clock, body, level.getDayTime());
        }
        double[] latLon = referencePoint(level, body);
        double fraction = vanillaFraction(body, latLon[0], latLon[1], clock.time());
        long dayTime = VanillaTime.advance(level.getDayTime(), fraction);
        level.setDayTime(dayTime);
        lastDayTimeSet.put(level.dimension(), dayTime);
    }

    /** Where vanilla's single per-dimension clock should be "local time": the first player, else spawn. */
    private static double[] referencePoint(ServerLevel level, CelestialBody body) {
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        List<ServerPlayer> players = level.players();
        BlockPos at = players.isEmpty() ? level.getSharedSpawnPos() : players.get(0).blockPosition();
        return new double[]{geometry.latitude(at.getZ()), geometry.longitude(at.getX())};
    }

    /** Height of the Sun above the horizon (radians) where this player stands on {@code body}. */
    public static double sunElevation(ServerPlayer player, CelestialBody body) {
        PlanetGeometry geometry = new PlanetGeometry(body.radius());
        SolarSystem sys = system();
        double t = time(player.server);
        return LocalSky.at(sys, body, geometry.latitude(player.getZ()), geometry.longitude(player.getX()), 0, t)
                .elevationOf(sys, sys.sun(), t);
    }

    private static double vanillaFraction(CelestialBody body, double lat, double lon, double t) {
        SolarSystem sys = system();
        double now = LocalSky.at(sys, body, lat, lon, 0, t).elevationOf(sys, sys.sun(), t);
        double soon = LocalSky.at(sys, body, lat, lon, 0, t + 1).elevationOf(sys, sys.sun(), t + 1);
        return VanillaTime.dayFraction(now, soon > now);
    }

    /**
     * Searches forward up to one solar day for the moment the local solar hour matches the
     * requested vanilla time of day (6000 = noon, 18000 = midnight), and jumps the clock there.
     */
    private static void moveSunToVanillaTime(ServerLevel level, UniverseClock clock, CelestialBody body, long dayTime) {
        double target = Math.floorMod(dayTime, VanillaTime.TICKS_PER_DAY) / (double) VanillaTime.TICKS_PER_DAY;
        double[] latLon = referencePoint(level, body);
        double day = solarDaySeconds(body);
        double best = clock.time();
        double bestError = 2;
        int steps = 720;
        SolarSystem sys = system();
        for (int i = 1; i <= steps; i++) {
            double t = clock.time() + day * i / steps;
            // Match by local solar hour (noon = 0.25 in vanilla), which exists at every latitude.
            double hour = LocalSky.at(sys, body, latLon[0], latLon[1], 0, t).hourAngle(sys, sys.sun(), t);
            double f = Math.floorMod((long) Math.round((0.25 + hour / (2 * Math.PI)) * 1e6), 1_000_000L) / 1e6;
            double error = Math.abs(f - target);
            error = Math.min(error, 1 - error);
            if (error < bestError) {
                bestError = error;
                best = t;
            }
        }
        clock.setTime(best);
    }

    /** Jumps the universe clock so the overworld's local time is {@code dayTime} (vanilla units). */
    public static void setLocalTime(MinecraftServer server, long dayTime) {
        ServerLevel level = server.overworld();
        CelestialBody body = bodyOf(level);
        if (body != null) {
            UniverseClock clock = UniverseClock.get(server);
            moveSunToVanillaTime(level, clock, body, dayTime);
            SpaceNetwork.sendToAll(syncMessage(clock));
        }
    }

    /** Moves the universe clock on by {@code seconds} (tests). */
    public static void advance(MinecraftServer server, double seconds) {
        UniverseClock clock = UniverseClock.get(server);
        clock.setTime(clock.time() + seconds);
        SpaceNetwork.sendToAll(syncMessage(clock));
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        CelestialBody body = bodyOf(level);
        if (body == null) {
            return;
        }
        UniverseClock clock = UniverseClock.get(level.getServer());
        // Wake at local sunrise (vanilla wakes you at day time 0, just after dawn).
        moveSunToVanillaTime(level, clock, body, 0);
        double[] latLon = referencePoint(level, body);
        long dayTime = VanillaTime.advance(level.getDayTime(), vanillaFraction(body, latLon[0], latLon[1], clock.time()));
        event.setTimeAddition(dayTime);
        lastDayTimeSet.put(level.dimension(), dayTime);
    }

    /** Average length of a sunrise-to-sunrise day on this body, in seconds. */
    public static double solarDaySeconds(CelestialBody body) {
        SolarSystem sys = system();
        CelestialBody planet = body;
        while (planet.parent() != null && planet.parent() != sys.sun()) {
            planet = planet.parent();
        }
        double year = planet.orbit().period(sys.sun().mu());
        double rotation = body.isTidallyLocked()
                ? body.orbit().period(body.parent().mu())
                : sys.siderealPeriod(body);
        return Math.abs(1 / (1 / rotation - 1 / year));
    }

    /** A morning start for new worlds: the sun ~10° up and rising at spawn. */
    private static double findMorning(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        CelestialBody earth = system().earth();
        double[] latLon = referencePoint(overworld, earth);
        SolarSystem sys = system();
        double day = solarDaySeconds(earth);
        for (double t = 0; t < day; t += 2) {
            LocalSky sky = LocalSky.at(sys, earth, latLon[0], latLon[1], 0, t);
            double elevation = sky.elevationOf(sys, sys.sun(), t);
            double later = LocalSky.at(sys, earth, latLon[0], latLon[1], 0, t + 2).elevationOf(sys, sys.sun(), t + 2);
            if (later > elevation && elevation > Math.toRadians(10)) {
                return t;
            }
        }
        return 0;
    }
}
