package com.thepearlking.stevespace.radio;

import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.SurfaceFrame;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Radio between pods, stations and the ground. Anyone sitting in a ship with a radio rack, or
 * standing by a radio (a rack or a Mission Control console), can talk to every other radio that
 * isn't hidden behind a planet. Messages travel at the speed of light: from the Moon they take a
 * second, from Mars minutes (less under time warp, since the delay is in universe time).
 */
public final class Radio {

    /** Light speed in blocks per second (one block is a kilometre). */
    public static final double LIGHT = 299_792.458;
    /** How close you must stand to a ground radio to use it. */
    private static final double REACH = 8;

    /** A radio somewhere in the solar system, with its position (ecliptic frame) right now. */
    public record Station(String id, String name, String body, DVec3 position, VesselEntity vessel, ServerLevel level, BlockPos pos) {
    }

    /** A message on its way. */
    private record Pending(double arrival, String from, String to, String text) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private Radio() {
    }

    // -------------------------------------------------------------------------------------------
    // Where the radios are
    // -------------------------------------------------------------------------------------------

    public static boolean isRadio(BlockState state) {
        return state.is(ModBlocks.RADIO_RACK.get()) || state.is(ModBlocks.MISSION_CONTROL.get());
    }

    public static List<Station> stations(MinecraftServer server) {
        List<Station> list = new ArrayList<>();
        SolarSystem system = ServerUniverse.system();
        double t = ServerUniverse.time(server);
        for (VesselEntity v : VesselEntity.allServerVessels()) {
            if (v.isRemoved() || v.structure() == null || v.structure().blocks().values().stream().noneMatch(Radio::isRadio)) {
                continue;
            }
            CelestialBody body = v.flightBody();
            DVec3 at = vesselPosition(system, v, body, t);
            if (at != null) {
                list.add(new Station("v:" + v.getUUID(), v.getName().getString(), body.id(), at, v, null, null));
            }
        }
        for (RadioSites.Site site : RadioSites.get(server).all()) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(site.dimension())));
            if (level == null) {
                continue;
            }
            if (level.isLoaded(site.pos()) && !isRadio(level.getBlockState(site.pos()))) {
                continue; // broken or replaced without us hearing about it
            }
            String name = site.name();
            CelestialBody body;
            DVec3 at;
            if (SpaceDimension.isSpace(level)) {
                ParkedShips.Parked parked = ParkedShips.get(server).at(site.pos().getX() + 0.5);
                if (parked == null || !parked.contains(site.pos().getX() + 0.5, site.pos().getY() + 0.5, site.pos().getZ() + 0.5, 1)) {
                    continue;
                }
                body = system.body(parked.body());
                if (body == null) {
                    continue;
                }
                double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
                at = system.absolutePosition(body, t)
                        .add(Kepler.elementsFromState(parked.state(), mu, parked.time()).stateAt(t, mu).position());
                name = shipName(server, parked.name(), name);
            } else {
                body = ServerUniverse.bodyOf(level);
                if (body == null) {
                    continue;
                }
                at = system.absolutePosition(body, t).add(SurfaceFrame.toOrbit(system, body,
                        site.pos().getX() + 0.5, site.pos().getY() + 1, site.pos().getZ() + 0.5, 0, 0, 0, t).position());
            }
            list.add(new Station("s:" + site.dimension() + "|" + site.pos().asLong(), name, body.id(), at, null, level, site.pos()));
        }
        return list;
    }

    static String shipName(MinecraftServer server, String json, String fallback) {
        try {
            Component c = Component.Serializer.fromJson(json, server.registryAccess());
            return c == null ? fallback : c.getString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static DVec3 vesselPosition(SolarSystem system, VesselEntity v, CelestialBody body, double t) {
        if (body == null) {
            return null;
        }
        if (v.orbital() != null) {
            return system.absolutePosition(body, t).add(v.orbital().state().position());
        }
        return system.absolutePosition(body, t).add(SurfaceFrame.toOrbit(system, body, v.mapX(), v.mapY(), v.mapZ(), 0, 0, 0, t).position());
    }

    /** The radio this player can use right now, or null. */
    public static Station stationOf(ServerPlayer player, List<Station> stations) {
        Station found = findStation(player, stations);
        if (found == null && discover(player)) {
            // A radio built before the registry knew about it (an older world, say): now it does.
            List<Station> again = stations(player.server);
            stations.clear();
            stations.addAll(again);
            found = findStation(player, stations);
        }
        return found;
    }

    /** Registers any radio within reach of the player that isn't known yet. */
    private static boolean discover(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean added = false;
        BlockPos centre = player.blockPosition();
        int r = (int) REACH;
        RadioSites sites = RadioSites.get(player.server);
        String dim = level.dimension().location().toString();
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            BlockState state = level.getBlockState(p);
            if (isRadio(state) && sites.all().stream().noneMatch(s -> s.dimension().equals(dim) && s.pos().equals(p))) {
                var body = ServerUniverse.bodyOf(level);
                String name = state.is(ModBlocks.MISSION_CONTROL.get()) ? "Mission Control"
                        : "Radio" + (body == null ? "" : " (" + body.displayName() + ")");
                sites.add(new RadioSites.Site(dim, p.immutable(), name));
                added = true;
            }
        }
        return added;
    }

    private static Station findStation(ServerPlayer player, List<Station> stations) {
        for (Station s : stations) {
            if (s.vessel() != null && VesselEntity.shipOf(player, 2) == s.vessel()) {
                return s;
            }
        }
        Station best = null;
        double bestDistance = REACH * REACH;
        for (Station s : stations) {
            if (s.level() == player.level()) {
                double d = player.distanceToSqr(s.pos().getX() + 0.5, s.pos().getY() + 0.5, s.pos().getZ() + 0.5);
                if (d <= bestDistance) {
                    bestDistance = d;
                    best = s;
                }
            }
        }
        return best;
    }

    /** True when no planet or moon stands between the two points. */
    public static boolean lineOfSight(SolarSystem system, DVec3 a, DVec3 b, double t) {
        return blocker(system, a, b, t) == null;
    }

    /** The planet or moon standing between the two points, or null. */
    public static CelestialBody blocker(SolarSystem system, DVec3 a, DVec3 b, double t) {
        DVec3 d = b.sub(a);
        double length = d.length();
        if (length < 1e-6) {
            return null;
        }
        DVec3 u = d.scale(1 / length);
        for (CelestialBody body : system.bodies()) {
            if (body.parent() == null) {
                continue; // the Sun: radio goes past it in practice
            }
            DVec3 c = system.absolutePosition(body, t);
            double along = c.sub(a).dot(u);
            if (along <= 0 || along >= length) {
                continue;
            }
            double miss = c.sub(a.add(u.scale(along))).length();
            if (miss < body.radius() * 0.995) {
                return body;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------------
    // Talking
    // -------------------------------------------------------------------------------------------

    /** A contact as the radio screen shows it. */
    public record Contact(String name, String body, double distance, String blockedBy, double mhz) {

        public boolean visible() {
            return blockedBy.isEmpty();
        }
    }

    public static List<Contact> contacts(ServerPlayer player) {
        MinecraftServer server = player.server;
        List<Station> stations = stations(server);
        Station own = stationOf(player, stations);
        List<Contact> list = new ArrayList<>();
        if (own == null) {
            return null;
        }
        double t = ServerUniverse.time(server);
        for (Station s : stations) {
            if (s.id().equals(own.id())) {
                continue;
            }
            double distance = s.position().sub(own.position()).length();
            CelestialBody in = blocker(ServerUniverse.system(), own.position(), s.position(), t);
            list.add(new Contact(s.name(), s.body(), distance, in == null ? "" : in.displayName(),
                    RadioSites.get(server).frequency(s.id())));
        }
        list.sort(java.util.Comparator.comparingDouble(Contact::distance));
        return list;
    }

    /** Tunes the player's radio (the one they're at, or their ship's) to {@code mhz}. */
    public static void tune(ServerPlayer player, double mhz) {
        List<Station> stations = stations(player.server);
        Station own = stationOf(player, stations);
        if (own == null) {
            player.displayClientMessage(Component.translatable("stevespace.radio.no_radio"), true);
            return;
        }
        RadioSites.get(player.server).tune(own.id(), mhz);
    }

    /** The frequency the player's radio is on, or the default if they have none. */
    public static double frequencyOf(ServerPlayer player) {
        List<Station> stations = stations(player.server);
        Station own = stationOf(player, stations);
        return own == null ? RadioSites.DEFAULT_MHZ : RadioSites.get(player.server).frequency(own.id());
    }

    /** Sends a message from the player's radio to every radio on the same frequency that can hear it. */
    public static void transmit(ServerPlayer player, String text) {
        text = text.strip();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > 200) {
            text = text.substring(0, 200);
        }
        MinecraftServer server = player.server;
        List<Station> stations = stations(server);
        Station own = stationOf(player, stations);
        if (own == null) {
            player.displayClientMessage(Component.translatable("stevespace.radio.no_radio"), true);
            return;
        }
        double t = ServerUniverse.time(server);
        RadioSites tuning = RadioSites.get(server);
        double mhz = tuning.frequency(own.id());
        String from = own.name() + " (" + player.getName().getString() + ") \u00B7 " + String.format(java.util.Locale.ROOT, "%.1f MHz", mhz);
        int reached = 0;
        for (Station s : stations) {
            if (s.id().equals(own.id())) {
                continue;
            }
            if (Math.abs(tuning.frequency(s.id()) - mhz) > 0.05) {
                continue; // tuned elsewhere: they don't hear it
            }
            if (!lineOfSight(ServerUniverse.system(), own.position(), s.position(), t)) {
                continue;
            }
            double delay = s.position().sub(own.position()).length() / LIGHT;
            PENDING.add(new Pending(t + delay, from, s.id(), text));
            reached++;
        }
        // Your own crew hear you at once.
        deliver(server, stations, own.id(), from, text);
        if (reached == 0) {
            player.displayClientMessage(Component.translatable("stevespace.radio.nobody"), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        double t = ServerUniverse.time(server);
        List<Station> stations = null;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.arrival() <= t) {
                if (stations == null) {
                    stations = stations(server);
                }
                deliver(server, stations, p.to(), p.from(), p.text());
                it.remove();
            }
        }
    }

    private static void deliver(MinecraftServer server, List<Station> stations, String stationId, String from, String text) {
        Component line = Component.literal("[" + from + "] ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.literal(text).withStyle(ChatFormatting.GREEN));
        for (ServerPlayer listener : server.getPlayerList().getPlayers()) {
            Station at = stationOf(listener, stations);
            if (at != null && at.id().equals(stationId)) {
                listener.sendSystemMessage(line);
                listener.playNotifySound(com.thepearlking.stevespace.registry.ModSounds.RADIO.get(), SoundSource.PLAYERS, 0.6f, 1f);
            }
        }
    }

    /** For tests: messages still on their way. */
    public static int pending() {
        return PENDING.size();
    }

    /** Messages still on their way to one station. */
    public static long pendingTo(String station) {
        return PENDING.stream().filter(p -> p.to().equals(station)).count();
    }

    /** The id of the radio the player talks through, or null. */
    public static String stationIdOf(ServerPlayer player) {
        Station own = stationOf(player, stations(player.server));
        return own == null ? null : own.id();
    }

}
