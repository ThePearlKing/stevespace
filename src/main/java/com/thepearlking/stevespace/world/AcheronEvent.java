package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.network.AcheronS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.universe.AcheronCourse;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/** When Acheron passes: the time of its next (or current) pass, decided here and sent to everyone. */
public final class AcheronEvent extends SavedData {

    private static final String NAME = "stevespace_acheron";
    private static final double YEAR = 365.25 * 86_400;
    /**
     * Game years (of the universe's clock, so time warp counts) before it first comes, and
     * between passes: long enough that a few missions go by, warp and all, before it's seen.
     */
    private static final double FIRST_MIN = 3;
    private static final double FIRST_MAX = 6;
    private static final double BETWEEN_MIN = 6;
    private static final double BETWEEN_MAX = 12;

    private double pass = Double.NaN;
    /** Passes in a row it came back early for. */
    private int streak;
    /** Hit by a nuclear device since its last pass. */
    private boolean nuked;
    /** Saved before the waits were made long: its first pass is rolled again. */
    private boolean oldTiming;

    public static AcheronEvent get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AcheronEvent::new, AcheronEvent::load, null), NAME);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        get(server).tick(server);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AcheronEvent data = get(player.server);
            SpaceNetwork.sendTo(player, new AcheronS2C(data.current()));
        }
    }

    private double current() {
        return SpaceConfig.ACHERON.get() ? pass : Double.NaN;
    }

    private void tick(MinecraftServer server) {
        double now = ServerUniverse.time(server);
        double before = AcheronCourse.pass();
        Random random = new Random();
        if (oldTiming) {
            oldTiming = false;
            if (!Double.isNaN(pass) && pass - now > AcheronCourse.LEAD) {
                pass = Double.NaN; // not on its way yet: wait the new, longer time
            }
            setDirty();
        }
        if (Double.isNaN(pass)) {
            pass = now + (FIRST_MIN + (FIRST_MAX - FIRST_MIN) * random.nextDouble()) * YEAR + AcheronCourse.LEAD;
            setDirty();
        } else if (now > pass + AcheronCourse.GONE) {
            double wait = (BETWEEN_MIN + (BETWEEN_MAX - BETWEEN_MIN) * random.nextDouble()) * YEAR;
            if (nuked && streak < 2) {
                streak++;
                wait /= 3; // back sooner
            } else {
                streak = 0;
            }
            nuked = false;
            pass = Math.max(pass + AcheronCourse.GONE + wait, now + AcheronCourse.LEAD);
            setDirty();
        }
        double show = current();
        if (Double.compare(show, before) != 0) {
            AcheronCourse.setPass(show);
            send(server);
        }
    }

    private void send(MinecraftServer server) {
        AcheronS2C msg = new AcheronS2C(current());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            SpaceNetwork.sendTo(p, msg);
        }
    }

    /** Brings it in now: it's just about to come into sight from Earth. */
    public static void summon(MinecraftServer server) {
        AcheronEvent data = get(server);
        data.pass = ServerUniverse.time(server) + AcheronCourse.LEAD;
        data.setDirty();
        AcheronCourse.setPass(data.current());
        data.send(server);
    }

    /** A nuclear device went off on it or round it. */
    public static void struck(MinecraftServer server) {
        AcheronEvent data = get(server);
        data.nuked = true;
        data.setDirty();
    }

    /** Tests: its closest pass {@code seconds} from now. */
    public static void testPass(MinecraftServer server, double seconds) {
        AcheronEvent data = get(server);
        data.pass = ServerUniverse.time(server) + seconds;
        data.setDirty();
        AcheronCourse.setPass(data.current());
        data.send(server);
    }

    public static double nextPass(MinecraftServer server) {
        return get(server).pass;
    }

    public static int streak(MinecraftServer server) {
        return get(server).streak;
    }

    private static AcheronEvent load(CompoundTag tag, HolderLookup.Provider registries) {
        AcheronEvent data = new AcheronEvent();
        data.pass = tag.contains("pass") ? tag.getDouble("pass") : Double.NaN;
        data.streak = tag.getInt("streak");
        data.nuked = tag.getBoolean("nuked");
        data.oldTiming = !tag.contains("timing");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!Double.isNaN(pass)) {
            tag.putDouble("pass", pass);
        }
        tag.putInt("streak", streak);
        tag.putBoolean("nuked", nuked);
        tag.putInt("timing", 2);
        return tag;
    }
}
