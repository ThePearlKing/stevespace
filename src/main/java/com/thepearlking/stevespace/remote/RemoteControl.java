package com.thepearlking.stevespace.remote;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.radio.Radio;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.SurfaceFrame;
import com.thepearlking.stevespace.vessel.VesselAssembler;
import com.thepearlking.stevespace.vessel.VesselControls;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.vessel.VesselServer;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flying craft by remote control. A console and a receiver on the same channel: the console
 * lists what's listening, and connecting puts the player "into" the receiver, out of sight and
 * out of harm's way, so the craft flies exactly as if they sat at its controls: its view, its
 * controls, its readouts, wherever it goes. Their body stays safe: disconnecting, the craft being
 * lost (crashed, destroyed), logging out or the server stopping all put them back at the console.
 *
 * <p>Commands travel at the speed of light and need a clear line: a planet or moon in the way
 * means no signal (the craft carries on as it was), and far away they arrive late.
 */
public final class RemoteControl {

    private static final String SAVE_KEY = "stevespace_remote";

    private RemoteControl() {
    }

    /** Somewhere a craft listens: a receiver in a flying craft or standing in the world. */
    public record Probe(String id, String name, String bodyId, String where, DVec3 position, VesselEntity vessel,
                        ServerLevel level, BlockPos receiver, BlockPos relative, int[] functions) {
    }

    private record Pending(long dueMillis, VesselControls controls, int function) {
    }

    private static final class Session {
        final UUID player;
        final String consoleDimension;
        final BlockPos console;
        final Vec3 returnTo;
        final float yaw;
        final float pitch;
        final boolean wasInvisible;
        final boolean wasInvulnerable;
        final int channel;
        String probeId;
        String probeName;
        boolean signal = true;
        String blocker = "";
        double delay;
        final ArrayDeque<Pending> pending = new ArrayDeque<>();

        Session(ServerPlayer p, String dimension, BlockPos console, int channel) {
            this.player = p.getUUID();
            this.consoleDimension = dimension;
            this.console = console.immutable();
            this.returnTo = p.position();
            this.yaw = p.getYRot();
            this.pitch = p.getXRot();
            this.wasInvisible = p.isInvisible();
            this.wasInvulnerable = p.isInvulnerable();
            this.channel = channel;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isRemote(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    // ---------------------------------------------------------------------------------------
    // Finding craft
    // ---------------------------------------------------------------------------------------

    /** Every receiver on this channel, with where it is now. */
    public static List<Probe> probes(MinecraftServer server, int channel) {
        List<Probe> list = new ArrayList<>();
        SolarSystem system = ServerUniverse.system();
        double t = ServerUniverse.time(server);
        java.util.Set<String> covered = new java.util.HashSet<>();
        for (VesselEntity v : VesselEntity.allServerVessels()) {
            if (v.isRemoved() || v.structure() == null) {
                continue;
            }
            for (Map.Entry<BlockPos, BlockState> e : v.structure().blocks().entrySet()) {
                if (!(e.getValue().getBlock() instanceof RemoteReceiverBlock)) {
                    continue;
                }
                BlockPos rel = e.getKey();
                BlockPos world = v.materialized() ? v.blockOrigin().offset(rel) : null;
                int ch;
                if (world != null && v.level().getBlockEntity(world) instanceof ChannelEntity live) {
                    ch = live.channel();
                } else {
                    ch = ChannelEntity.channelOf(v.structure().blockEntities().get(rel));
                }
                if (world != null) {
                    covered.add(v.level().dimension().location() + "|" + world.asLong());
                }
                if (ch != channel) {
                    continue;
                }
                CelestialBody body = v.flightBody();
                DVec3 at = vesselPosition(system, v, body, t);
                if (at == null) {
                    continue;
                }
                String where = v.orbital() != null ? "orbit" : v.isGrounded() ? "landed" : "flying";
                list.add(new Probe("v:" + v.getUUID() + ":" + rel.asLong(), v.getName().getString(), body.id(), where, at, v,
                        (ServerLevel) v.level(), world, rel, functions(v.structure().blocks().values())));
            }
        }
        for (RemoteSites.Site site : List.copyOf(RemoteSites.get(server).all())) {
            if (covered.contains(site.dimension() + "|" + site.pos().asLong())) {
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(site.dimension())));
            if (level == null) {
                continue;
            }
            level.getChunk(site.pos()); // receivers are few; look even if nobody is near
            if (!(level.getBlockEntity(site.pos()) instanceof RemoteReceiverEntity receiver)) {
                RemoteSites.get(server).remove(site.dimension(), site.pos()); // gone without telling us
                continue;
            }
            if (receiver.channel() != channel) {
                continue;
            }
            DVec3 at = absolute(level, Vec3.atCenterOf(site.pos()), t);
            if (at == null) {
                continue;
            }
            CelestialBody body = SpaceDimension.isSpace(level) ? parkedBody(level, site.pos()) : ServerUniverse.bodyOf(level);
            int[] fn = new int[5];
            var scan = VesselAssembler.scan(level, site.pos(), com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get(), false);
            if (scan.ok()) {
                fn = functions(scan.structure().blocks().values());
            }
            list.add(new Probe("s:" + site.dimension() + "|" + site.pos().asLong(),
                    Component.translatable("stevespace.remote.probe_on", body == null ? "?" : body.displayName()).getString(),
                    body == null ? "" : body.id(), SpaceDimension.isSpace(level) ? "parked" : "ground", at, null, level, site.pos(), BlockPos.ZERO, fn));
        }
        return list;
    }

    private static int[] functions(java.util.Collection<BlockState> blocks) {
        int[] counts = new int[5];
        for (BlockState s : blocks) {
            if (s.getBlock() instanceof RcFunctionBlock) {
                counts[s.getValue(RcFunctionBlock.FUNCTION) - 1]++;
            }
        }
        return counts;
    }

    // ---------------------------------------------------------------------------------------
    // Where things are in the solar system (for signal and delay)
    // ---------------------------------------------------------------------------------------

    public static DVec3 vesselPosition(SolarSystem system, VesselEntity v, CelestialBody body, double t) {
        if (body == null) {
            return null;
        }
        if (v.orbital() != null) {
            return system.absolutePosition(body, t).add(v.orbital().state().position());
        }
        return system.absolutePosition(body, t).add(SurfaceFrame.toOrbit(system, body, v.mapX(), v.mapY(), v.mapZ(), 0, 0, 0, t).position());
    }

    private static CelestialBody parkedBody(ServerLevel level, BlockPos pos) {
        ParkedShips.Parked parked = ParkedShips.get(level.getServer()).at(pos.getX() + 0.5);
        return parked == null ? null : ServerUniverse.system().body(parked.body());
    }

    /** A point in a world (a planet's surface, or a ship's cell in space), in the solar system; null if unknown. */
    public static DVec3 absolute(ServerLevel level, Vec3 p, double t) {
        SolarSystem system = ServerUniverse.system();
        if (SpaceDimension.isSpace(level)) {
            VesselEntity ship = VesselEntity.materializedAt(level, BlockPos.containing(p));
            if (ship != null) {
                return vesselPosition(system, ship, ship.flightBody(), t);
            }
            ParkedShips.Parked parked = ParkedShips.get(level.getServer()).at(p.x);
            if (parked == null) {
                return null;
            }
            CelestialBody body = system.body(parked.body());
            if (body == null) {
                return null;
            }
            double mu = body.mu() * com.thepearlking.stevespace.config.SpaceConfig.GRAVITY_SCALE.get();
            return system.absolutePosition(body, t).add(Kepler.elementsFromState(parked.state(), mu, parked.time()).stateAt(t, mu).position());
        }
        CelestialBody body = ServerUniverse.bodyOf(level);
        if (body == null) {
            return null;
        }
        return system.absolutePosition(body, t).add(SurfaceFrame.toOrbit(system, body, p.x, p.y, p.z, 0, 0, 0, t).position());
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension)));
    }

    /** Signal from a console to a craft: [delay seconds, or -1 if blocked], and what's in the way. */
    private static Object[] signal(ServerLevel consoleLevel, BlockPos console, DVec3 probe, double t) {
        DVec3 from = absolute(consoleLevel, Vec3.atCenterOf(console), t);
        if (from == null || probe == null) {
            return new Object[]{0.0, ""}; // somewhere we can't place: assume it's close by
        }
        CelestialBody blocker = Radio.blocker(ServerUniverse.system(), from, probe, t);
        if (blocker != null) {
            return new Object[]{-1.0, blocker.displayName()};
        }
        return new Object[]{probe.sub(from).length() / Radio.LIGHT, ""};
    }

    // ---------------------------------------------------------------------------------------
    // The console and receiver screens
    // ---------------------------------------------------------------------------------------

    public static void openTuner(ServerPlayer player, BlockPos pos, int channel, boolean receiver) {
        openTuner(player, pos, channel, 0);
    }

    /** A relay's tuner: its channel and function. */
    public static void openTuner(ServerPlayer player, BlockPos pos, int channel, int function) {
        SpaceNetwork.sendTo(player, new com.thepearlking.stevespace.network.RemoteTunerS2C(pos, channel, function));
    }

    /** Sends the console screen: its channel and every craft listening on it. */
    public static void openConsole(ServerPlayer player, BlockPos console) {
        if (!(player.level().getBlockEntity(console) instanceof RemoteConsoleEntity entity)) {
            return;
        }
        double t = ServerUniverse.time(player.server);
        List<com.thepearlking.stevespace.network.RemoteConsoleS2C.Entry> entries = new ArrayList<>();
        for (Probe probe : probes(player.server, entity.channel())) {
            Object[] s = signal(player.serverLevel(), console, probe.position(), t);
            DVec3 from = absolute(player.serverLevel(), Vec3.atCenterOf(console), t);
            double distance = from == null ? 0 : probe.position().sub(from).length();
            entries.add(new com.thepearlking.stevespace.network.RemoteConsoleS2C.Entry(probe.id(), probe.name(), probe.bodyId(), probe.where(),
                    distance, (double) s[0], (String) s[1], probe.functions()));
        }
        SpaceNetwork.sendTo(player, new com.thepearlking.stevespace.network.RemoteConsoleS2C(console, entity.channel(), entries,
                channelFunctions(player.server, entity.channel())));
    }

    /** A button on a console or receiver screen. */
    public static void action(ServerPlayer player, BlockPos pos, String action, int value, String probeId) {
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 8 * 8) {
            return; // too far from the block to be pressing its buttons
        }
        switch (action) {
            case "tune" -> {
                if (player.level().getBlockEntity(pos) instanceof ChannelEntity entity) {
                    entity.setChannel(value);
                    if (entity instanceof RemoteConsoleEntity) {
                        openConsole(player, pos);
                    } else if (entity instanceof RcFunctionEntity) {
                        openTuner(player, pos, entity.channel(), player.level().getBlockState(pos).getValue(RcFunctionBlock.FUNCTION));
                    } else {
                        openTuner(player, pos, entity.channel(), true);
                    }
                }
            }
            case "connect" -> connect(player, pos, probeId);
            case "function" -> {
                if (player.level().getBlockEntity(pos) instanceof RemoteConsoleEntity console) {
                    fireChannel(player, player.serverLevel(), pos, console.channel(), value);
                }
            }
            case "relay_function" -> {
                BlockState state = player.level().getBlockState(pos);
                if (state.getBlock() instanceof RcFunctionBlock && value >= 1 && value <= 5) {
                    player.level().setBlock(pos, state.setValue(RcFunctionBlock.FUNCTION, value), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    openTuner(player, pos, RcFunctionBlock.channelAt(player.level(), pos), value);
                }
            }
            case "refresh" -> openConsole(player, pos);
            default -> {
            }
        }
    }

    private static Probe find(MinecraftServer server, BlockPos console, ServerLevel level, String probeId) {
        if (!(level.getBlockEntity(console) instanceof RemoteConsoleEntity entity)) {
            return null;
        }
        for (Probe p : probes(server, entity.channel())) {
            if (p.id().equals(probeId)) {
                return p;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // Connecting and coming back
    // ---------------------------------------------------------------------------------------

    private static void connect(ServerPlayer player, BlockPos console, String probeId) {
        if (isRemote(player) || player.isPassenger()) {
            return;
        }
        ServerLevel consoleLevel = player.serverLevel();
        Probe probe = find(player.server, console, consoleLevel, probeId);
        if (probe == null) {
            player.displayClientMessage(Component.translatable("stevespace.remote.not_found"), true);
            return;
        }
        double t = ServerUniverse.time(player.server);
        Object[] s = signal(consoleLevel, console, probe.position(), t);
        if ((double) s[0] < 0) {
            player.displayClientMessage(Component.translatable("stevespace.remote.no_signal_behind", s[1]), true);
            return;
        }
        int channel = consoleLevel.getBlockEntity(console) instanceof ChannelEntity c ? c.channel() : 1;
        Session session = new Session(player, consoleLevel.dimension().location().toString(), console, channel);
        session.probeId = probe.id();
        session.probeName = probe.name();
        session.delay = (double) s[0];
        // Written down first: if anything goes wrong from here (even a crash), they come back.
        saveSession(player, session);
        SESSIONS.put(player.getUUID(), session);
        boolean aboard;
        if (probe.vessel() != null && !probe.vessel().materialized()) {
            VesselEntity v = probe.vessel();
            player.teleportTo((ServerLevel) v.level(), v.getX(), v.getY(), v.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot());
            aboard = v.boardRemote(player, probe.relative());
        } else {
            BlockPos at = probe.receiver();
            player.teleportTo(probe.level(), at.getX() + 0.5, at.getY() + SeatEntity.SEAT_HEIGHT, at.getZ() + 0.5, java.util.Set.of(),
                    player.getYRot(), player.getXRot());
            SeatEntity seat = SeatEntity.create(probe.level(), at);
            probe.level().addFreshEntity(seat);
            aboard = player.startRiding(seat, true);
        }
        if (!aboard) {
            disconnect(player, "stevespace.remote.not_found");
            return;
        }
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.fallDistance = 0;
        StevesSpace.LOGGER.info("{} is flying {} by remote control (channel {})", player.getName().getString(), probe.name(), channel);
        sendState(player, session, probe);
    }

    /** Back to the console, as they were. {@code reason} is shown to them (a translation key), or null. */
    public static void disconnect(ServerPlayer player, String reason) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof VesselEntity v) {
            v.releaseRemote(player);
        } else if (vehicle instanceof SeatEntity seat) {
            seat.requestDismount(player);
        } else if (vehicle != null) {
            player.stopRiding();
        }
        ServerLevel home = level(player.server, session.consoleDimension);
        if (home == null) {
            home = player.server.overworld();
        }
        player.teleportTo(home, session.returnTo.x, session.returnTo.y, session.returnTo.z, java.util.Set.of(), session.yaw, session.pitch);
        player.setInvisible(session.wasInvisible);
        player.setInvulnerable(session.wasInvulnerable);
        player.fallDistance = 0;
        player.getPersistentData().remove(SAVE_KEY);
        SpaceNetwork.sendTo(player, com.thepearlking.stevespace.network.RemoteStateS2C.off());
        if (reason != null) {
            player.displayClientMessage(Component.translatable(reason), true);
        }
    }

    private static void saveSession(ServerPlayer player, Session s) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", s.consoleDimension);
        tag.putDouble("x", s.returnTo.x);
        tag.putDouble("y", s.returnTo.y);
        tag.putDouble("z", s.returnTo.z);
        tag.putFloat("yaw", s.yaw);
        tag.putFloat("pitch", s.pitch);
        tag.putBoolean("invisible", s.wasInvisible);
        tag.putBoolean("invulnerable", s.wasInvulnerable);
        player.getPersistentData().put(SAVE_KEY, tag);
    }

    /** A craft is gone (crashed, blown up, removed): everyone flying it comes home. */
    public static void craftLost(VesselEntity vessel) {
        if (SESSIONS.isEmpty() || vessel.level().isClientSide) {
            return;
        }
        for (ServerPlayer player : vessel.level().getServer().getPlayerList().getPlayers()) {
            if (!isRemote(player)) {
                continue;
            }
            boolean riding = player.getVehicle() == vessel;
            boolean inBlocks = vessel.materialized() && player.getVehicle() instanceof SeatEntity seat
                    && vessel.holds(seat.getX(), seat.getY(), seat.getZ(), 0.5);
            if (riding || inBlocks) {
                disconnect(player, "stevespace.remote.lost");
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Controls and functions, at the speed of light
    // ---------------------------------------------------------------------------------------

    /** Controls from a remote pilot: queued to arrive after the light delay. True if handled here. */
    public static boolean controls(ServerPlayer player, VesselControls controls) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return false;
        }
        if (s.signal) {
            s.pending.add(new Pending(System.currentTimeMillis() + Math.round(s.delay * 1000), controls, 0));
        }
        return true;
    }

    /** A function key pressed while flying remotely: sent from the console you're flying from. */
    public static void function(ServerPlayer player, int n) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || n < 1 || n > 5) {
            return;
        }
        ServerLevel consoleLevel = level(player.server, s.consoleDimension);
        if (consoleLevel != null) {
            fireChannel(player, consoleLevel, s.console, s.channel, n);
        }
    }

    private static final List<Object[]> DELAYED = new ArrayList<>();

    /**
     * One relay a console on some channel can set off: standing in the world (anywhere, any
     * dimension, loaded or not), or in a craft flying as one piece.
     */
    private record Target(ServerLevel level, BlockPos pos, VesselEntity vessel, BlockPos relative, int function) {
    }

    /** Lets a far-off relay's chunk load for a couple of seconds: long enough to pulse and settle. */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> RELAY_TICKET =
            net.minecraft.server.level.TicketType.create("stevespace_relay", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 60);

    private static void loadBriefly(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(pos);
        level.getChunkSource().addRegionTicket(RELAY_TICKET, chunk, 1, chunk);
        level.getChunk(pos);
    }

    /**
     * Every relay that answers on a channel: relays tuned to it, plus every relay in a craft
     * whose receiver listens on it (the receiver passes the call on to its own craft).
     */
    private static List<Target> targets(MinecraftServer server, int channel) {
        Map<String, Target> found = new java.util.LinkedHashMap<>();
        for (RemoteSites.Site site : List.copyOf(RemoteSites.relays(server).all())) {
            ServerLevel level = level(server, site.dimension());
            if (level == null) {
                continue;
            }
            level.getChunk(site.pos()); // relays are few; look even where nobody is (a ship parked in orbit)
            BlockState state = level.getBlockState(site.pos());
            if (!(state.getBlock() instanceof RcFunctionBlock)) {
                RemoteSites.relays(server).remove(site.dimension(), site.pos()); // gone without telling us
                continue;
            }
            if (RcFunctionBlock.channelAt(level, site.pos()) == channel) {
                found.put(site.dimension() + "|" + site.pos().asLong(), new Target(level, site.pos(), null, null, state.getValue(RcFunctionBlock.FUNCTION)));
            }
        }
        for (VesselEntity v : VesselEntity.allServerVessels()) {
            if (v.isRemoved() || v.structure() == null || v.materialized()) {
                continue;
            }
            boolean listening = false;
            for (Map.Entry<BlockPos, BlockState> e : v.structure().blocks().entrySet()) {
                if (e.getValue().getBlock() instanceof RemoteReceiverBlock && ChannelEntity.channelOf(v.structure().blockEntities().get(e.getKey())) == channel) {
                    listening = true;
                }
            }
            for (Map.Entry<BlockPos, BlockState> e : v.structure().blocks().entrySet()) {
                if (e.getValue().getBlock() instanceof RcFunctionBlock
                        && (listening || ChannelEntity.channelOf(v.structure().blockEntities().get(e.getKey())) == channel)) {
                    found.put("v:" + v.getUUID() + "|" + e.getKey().asLong(), new Target((ServerLevel) v.level(), null, v, e.getKey(),
                            e.getValue().getValue(RcFunctionBlock.FUNCTION)));
                }
            }
        }
        // Crafts standing in the world (on the ground, landed, parked in orbit) whose receiver is on this channel.
        for (Probe probe : probes(server, channel)) {
            if (probe.vessel() != null && !probe.vessel().materialized()) {
                continue; // flying: done above
            }
            ServerLevel level = probe.level();
            BlockPos origin;
            Map<BlockPos, BlockState> blocks;
            if (probe.vessel() != null) {
                origin = probe.vessel().blockOrigin();
                blocks = probe.vessel().structure().blocks();
            } else {
                var scan = VesselAssembler.scan(level, probe.receiver(), com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get(), false);
                if (!scan.ok()) {
                    continue;
                }
                origin = probe.receiver();
                blocks = scan.structure().blocks();
            }
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
                if (e.getValue().getBlock() instanceof RcFunctionBlock) {
                    BlockPos pos = origin.offset(e.getKey());
                    found.put(level.dimension().location() + "|" + pos.asLong(), new Target(level, pos, null, null, e.getValue().getValue(RcFunctionBlock.FUNCTION)));
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    /** How many relays answer on each function (1-5) of a channel. */
    public static int[] channelFunctions(MinecraftServer server, int channel) {
        int[] count = new int[5];
        for (Target t : targets(server, channel)) {
            count[t.function() - 1]++;
        }
        return count;
    }

    /**
     * A function sent on a channel from a console: every relay answering on it with that function
     * pulses once the signal reaches it, however far (light delay; nothing if a planet is in the way).
     */
    private static void fireChannel(ServerPlayer player, ServerLevel consoleLevel, BlockPos console, int channel, int n) {
        MinecraftServer server = player.server;
        double t = ServerUniverse.time(server);
        long now = System.currentTimeMillis();
        SolarSystem system = ServerUniverse.system();
        int sent = 0;
        String blocked = null;
        java.util.Set<VesselEntity> flying = new java.util.HashSet<>();
        for (Target target : targets(server, channel)) {
            if (target.function() != n) {
                continue;
            }
            DVec3 where = target.vessel() != null ? vesselPosition(system, target.vessel(), target.vessel().flightBody(), t)
                    : absolute(target.level(), Vec3.atCenterOf(target.pos()), t);
            Object[] s = signal(consoleLevel, console, where, t);
            if ((double) s[0] < 0) {
                blocked = (String) s[1];
                continue;
            }
            sent++;
            long due = now + Math.round((double) s[0] * 1000);
            if (target.vessel() != null) {
                if (flying.add(target.vessel())) {
                    VesselEntity v = target.vessel();
                    DELAYED.add(new Object[]{due, (Runnable) () -> fireCraft(v, n, channel)});
                }
            } else {
                ServerLevel level = target.level();
                BlockPos pos = target.pos();
                DELAYED.add(new Object[]{due, (Runnable) () -> {
                    loadBriefly(level, pos);
                    RcFunctionBlock.pulse(level, pos);
                }});
            }
        }
        if (sent > 0) {
            player.displayClientMessage(Component.translatable("stevespace.remote.function_sent", n), true);
        } else if (blocked != null) {
            player.displayClientMessage(Component.translatable("stevespace.remote.no_signal_behind", blocked), true);
        }
    }

    /** The call reaching a flying craft: its relays on that function (all of them if its receiver heard it). */
    private static void fireCraft(VesselEntity v, int n, int channel) {
        if (v.isRemoved() || v.structure() == null) {
            return;
        }
        boolean listening = v.structure().blocks().entrySet().stream().anyMatch(e -> e.getValue().getBlock() instanceof RemoteReceiverBlock
                && ChannelEntity.channelOf(v.structure().blockEntities().get(e.getKey())) == channel);
        if (v.materialized()) {
            // Turned into blocks meanwhile: its relays are in the world now.
            for (Map.Entry<BlockPos, BlockState> e : v.structure().blocks().entrySet()) {
                BlockPos p = v.blockOrigin().offset(e.getKey());
                BlockState st = v.level().getBlockState(p);
                if (st.getBlock() instanceof RcFunctionBlock && st.getValue(RcFunctionBlock.FUNCTION) == n
                        && (listening || RcFunctionBlock.channelAt(v.level(), p) == channel)) {
                    RcFunctionBlock.pulse((ServerLevel) v.level(), p);
                }
            }
        } else {
            RemoteFunctions.fireInFlight(v, n, listening ? -1 : channel);
        }
    }

    /** The craft a remote pilot is flying right now, as a probe (or null). */
    private static Probe current(ServerPlayer player, Session s) {
        Entity vehicle = player.getVehicle();
        SolarSystem system = ServerUniverse.system();
        double t = ServerUniverse.time(player.server);
        if (vehicle instanceof VesselEntity v) {
            return new Probe(s.probeId, s.probeName, v.flightBody() == null ? "" : v.flightBody().id(), "", vesselPosition(system, v, v.flightBody(), t),
                    v, (ServerLevel) v.level(), null, BlockPos.ZERO, functions(v.structure().blocks().values()));
        }
        if (vehicle instanceof SeatEntity seat) {
            ServerLevel level = player.serverLevel();
            BlockPos at = seat.blockPosition();
            VesselEntity ship = VesselEntity.materializedAt(level, at);
            if (ship != null) {
                return new Probe(s.probeId, s.probeName, ship.flightBody().id(), "", vesselPosition(system, ship, ship.flightBody(), t),
                        ship, level, at, at.subtract(ship.blockOrigin()), functions(ship.structure().blocks().values()));
            }
            int[] fn = new int[5];
            var scan = VesselAssembler.scan(level, at, com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get(), false);
            if (scan.ok()) {
                fn = functions(scan.structure().blocks().values());
            }
            CelestialBody body = ServerUniverse.bodyOf(level);
            return new Probe(s.probeId, s.probeName, body == null ? "" : body.id(), "", absolute(level, Vec3.atCenterOf(at), t), null, level, at,
                    BlockPos.ZERO, fn);
        }
        return null;
    }

    private static void sendState(ServerPlayer player, Session s, Probe probe) {
        SpaceNetwork.sendTo(player, new com.thepearlking.stevespace.network.RemoteStateS2C(true, s.channel, s.probeName, s.signal, s.blocker,
                s.delay, channelFunctions(player.server, s.channel)));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = System.currentTimeMillis();
        if (!DELAYED.isEmpty()) {
            for (Object[] d : List.copyOf(DELAYED)) {
                if ((long) d[0] <= now) {
                    DELAYED.remove(d);
                    ((Runnable) d[1]).run();
                }
            }
        }
        if (SESSIONS.isEmpty()) {
            return;
        }
        for (Session s : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(s.player);
            if (player == null) {
                SESSIONS.remove(s.player); // logged out (put back on the way out)
                continue;
            }
            if (player.getVehicle() == null) {
                disconnect(player, "stevespace.remote.lost");
                continue;
            }
            Probe probe = current(player, s);
            if (server.getTickCount() % 10 == 0) {
                ServerLevel consoleLevel = level(server, s.consoleDimension);
                if (consoleLevel != null && probe != null) {
                    Object[] sig = signal(consoleLevel, s.console, probe.position(), ServerUniverse.time(server));
                    s.signal = (double) sig[0] >= 0;
                    s.blocker = (String) sig[1];
                    s.delay = Math.max(0, (double) sig[0]);
                }
                sendState(player, s, probe);
            }
            while (!s.pending.isEmpty() && s.pending.peek().dueMillis() <= now) {
                Pending p = s.pending.poll();
                if (p.controls() != null) {
                    VesselServer.applyControls(player, p.controls());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            disconnect(player, null);
        }
    }

    /** Back from a session the server never got to end (it stopped or crashed mid-flight). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getPersistentData().contains(SAVE_KEY)) {
            return;
        }
        CompoundTag tag = player.getPersistentData().getCompound(SAVE_KEY);
        player.getPersistentData().remove(SAVE_KEY);
        if (player.getVehicle() != null) {
            player.stopRiding();
        }
        ServerLevel home = level(player.server, tag.getString("dim"));
        if (home == null) {
            home = player.server.overworld();
        }
        player.teleportTo(home, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), java.util.Set.of(), tag.getFloat("yaw"), tag.getFloat("pitch"));
        player.setInvisible(tag.getBoolean("invisible"));
        player.setInvulnerable(tag.getBoolean("invulnerable"));
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            disconnect(player, null);
        }
        DELAYED.clear();
    }

    /** True while this level-side entity is someone flying remotely (client shows no hand, etc.). */
    public static boolean isRemote(Level level, UUID id) {
        return !level.isClientSide && SESSIONS.containsKey(id);
    }
}
