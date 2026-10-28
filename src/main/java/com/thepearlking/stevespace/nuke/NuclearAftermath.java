package com.thepearlking.stevespace.nuke;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.SolarSystem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * What a detonation leaves behind. A ground burst: fallout round ground zero, its dose falling as
 * time^-1.2 (the "seven-ten rule": seven times the time, a tenth of the dose). A burst above the
 * air: a man-made radiation belt on the magnetic shell it went off on, as Starfish Prime left one
 * that crippled satellites for months, fading slowly. Universe time counts (time warp speeds it).
 * The server keeps the record; players get a copy for their instruments.
 */
public final class NuclearAftermath extends SavedData {

    private static final String NAME = "stevespace_nuclear_aftermath";

    /** One detonation: where (world and position), how big, what kind, the magnetic shell (space bursts), when. */
    public record Event(String dimension, double x, double y, double z, double kt, int regime, String body, double shell, double time) {
    }

    private final List<Event> events = new ArrayList<>();
    /** The client's copy (from the server: past ones on joining, new ones as they happen). */
    public static final List<Event> CLIENT = new ArrayList<>();

    public static NuclearAftermath get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NuclearAftermath::new, NuclearAftermath::load, null), NAME);
    }

    public List<Event> events() {
        return events;
    }

    void record(ServerLevel level, Vec3 at, double kt, NuclearEffects.Regime regime, Detonation.Setting where, double t) {
        double shell = 0;
        if (regime == NuclearEffects.Regime.SPACE && where.body() != null && where.absolute() != null) {
            SolarSystem system = ServerUniverse.system();
            DVec3 rel = where.absolute().sub(system.absolutePosition(where.body(), t));
            shell = shellOf(where.body(), rel);
        }
        events.add(new Event(level.dimension().location().toString(), at.x, at.y, at.z, kt, regime.ordinal(),
                where.body() == null ? "" : where.body().id(), shell, t));
        // Old enough to have faded to nothing: forgotten.
        events.removeIf(e -> t - e.time() > 400 * 86400.0);
        setDirty();
    }

    /** The magnetic shell (McIlwain L) through a point near a body, from its distance and magnetic latitude. */
    public static double shellOf(CelestialBody body, DVec3 relative) {
        double r = relative.length() / body.radius();
        double sinLat = relative.normalize().dot(body.spinAxis());
        double cos2 = Math.max(0.02, 1 - sinLat * sinLat);
        return r / cos2;
    }

    /**
     * Extra dose rate, µSv/h, from detonations at a place: in world {@code dimension} at (x, y, z),
     * or near {@code body} at {@code relative} (from its centre; null if not known). The burst
     * itself first (its prompt gamma and neutrons, then the glowing cloud overhead for a minute or
     * two), then what it leaves: fallout from a ground burst, soil made radioactive under a low
     * air burst, a pumped radiation belt from one in space.
     */
    public static double extra(List<Event> events, String dimension, double x, double y, double z, CelestialBody body, DVec3 relative, double t) {
        double dose = 0;
        double perKm = NuclearEffects.blocks(1);
        for (Event e : events) {
            double age = t - e.time();
            if (age < 0) {
                continue;
            }
            boolean here = e.dimension().equals(dimension);
            if (here && age < 300 && e.regime() != NuclearEffects.Regime.SPACE.ordinal()) {
                // The burst: about half a sievert a kilometre and a half from 15 kilotons, most of it in
                // the first seconds, falling off with distance squared and the air soaking it up.
                double dx = x - e.x();
                double dy = y - e.y();
                double dz = z - e.z();
                double km = Math.max(0.05, Math.sqrt(dx * dx + dy * dy + dz * dz) / perKm);
                double air = e.regime() == NuclearEffects.Regime.VACUUM.ordinal() ? 0 : 1;
                dose += 3.6e9 * Math.pow(e.kt() / 15, 0.6) / (km * km) * Math.exp(-air * km / 0.4) * Math.exp(-age / 8);
            }
            if (here && e.regime() == NuclearEffects.Regime.AIR.ordinal()) {
                // A low air burst: neutrons make the ground under it radioactive for a while.
                double reach = NuclearEffects.blocks(NuclearEffects.blastKm(e.kt(), 20));
                double d2 = (x - e.x()) * (x - e.x()) + (z - e.z()) * (z - e.z());
                dose += 4e5 * Math.sqrt(e.kt() / 15) * Math.exp(-d2 / (reach * reach)) * Math.pow(Math.max(60, age) / 3600, -1.2);
            }
            if (e.regime() == NuclearEffects.Regime.SURFACE.ordinal() && here) {
                // Fallout: about 30 Sv/h at ground zero an hour after a megaton, spread over the
                // 1 psi circle, then the seven-ten rule.
                double reach = NuclearEffects.blocks(NuclearEffects.blastKm(e.kt(), 1)) * 1.5;
                double d2 = (x - e.x()) * (x - e.x()) + (z - e.z()) * (z - e.z());
                double hourly = 3e7 * Math.sqrt(e.kt() / 1000) * Math.exp(-d2 / (reach * reach));
                // It isn't there at once: the heavy stuff falls back near ground zero within minutes,
                // the rest drifts down out of the cloud further out over the next half hour or so.
                double arrive = 120 + 1800 * Math.sqrt(d2) / reach;
                double settled = Math.max(0, Math.min(1, (age - arrive * 0.5) / arrive));
                dose += hourly * settled * Math.pow(Math.max(60, age) / 3600, -1.2);
            } else if (e.regime() == NuclearEffects.Regime.SPACE.ordinal() && body != null && relative != null
                    && body.id().equals(e.body()) && relative.length() > body.radius() * 1.02) {
                // The pumped belt: trapped electrons on the burst's shell, fading over months.
                double shell = shellOf(body, relative);
                double across = (shell - e.shell()) / 0.35;
                dose += 2e5 * (e.kt() / 1450) * Math.exp(-age / (60 * 86400.0)) * Math.exp(-across * across);
            }
        }
        return dose;
    }

    private static NuclearAftermath load(CompoundTag tag, HolderLookup.Provider registries) {
        NuclearAftermath data = new NuclearAftermath();
        for (Tag t : tag.getList("events", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            data.events.add(new Event(e.getString("dim"), e.getDouble("x"), e.getDouble("y"), e.getDouble("z"), e.getDouble("kt"),
                    e.getInt("regime"), e.getString("body"), e.getDouble("shell"), e.getDouble("time")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Event e : events) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", e.dimension());
            c.putDouble("x", e.x());
            c.putDouble("y", e.y());
            c.putDouble("z", e.z());
            c.putDouble("kt", e.kt());
            c.putInt("regime", e.regime());
            c.putString("body", e.body());
            c.putDouble("shell", e.shell());
            c.putDouble("time", e.time());
            list.add(c);
        }
        tag.put("events", list);
        return tag;
    }

    /** A player joining gets the record (their instruments read what's left). */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.thepearlking.stevespace.network.SpaceNetwork.sendTo(player,
                    new com.thepearlking.stevespace.network.NukeHistoryS2C(List.copyOf(get(player.server).events())));
        }
    }
}
