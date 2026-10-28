package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.network.LandmarksS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What players have built, in 8 x 8 block cells: how much, how high, and its colour, so a big
 * enough building can be seen from space (a skyscraper as a block of its own colour and size).
 */
public final class Landmarks extends SavedData {

    private static final String NAME = "stevespace_landmarks";
    /** Cell size, blocks. */
    public static final int CELL = 8;
    /** Fewer blocks than this in a cell and it isn't worth drawing from space. */
    public static final int VISIBLE = 40;

    /** One cell: blocks placed, lowest and highest, and the colour of what's on top. */
    public record Cell(int count, int minY, int maxY, int color) {
    }

    private final Map<String, Map<Long, Cell>> cells = new HashMap<>();
    private boolean changed;

    public static Landmarks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Landmarks::new, Landmarks::load, null), NAME);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public static int cellX(long key) {
        return (int) (key >> 32);
    }

    public static int cellZ(long key) {
        return (int) key;
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player)
                || SpaceDimension.isSpace(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        int color = event.getPlacedBlock().getMapColor(level, pos).col;
        get(level.getServer()).add(level.dimension().location().toString(), pos, color == 0 ? 0x9A9A9A : color);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() != null && !SpaceDimension.isSpace(level)) {
            get(level.getServer()).remove(level.dimension().location().toString(), event.getPos());
        }
    }

    /** For tests and world tools: a block built here, as if placed by a player. */
    public static void recordBuilt(ServerLevel level, BlockPos pos, int color) {
        get(level.getServer()).add(level.dimension().location().toString(), pos, color);
    }

    private void add(String dimension, BlockPos pos, int color) {
        Map<Long, Cell> map = cells.computeIfAbsent(dimension, d -> new HashMap<>());
        long k = key(Math.floorDiv(pos.getX(), CELL), Math.floorDiv(pos.getZ(), CELL));
        Cell c = map.get(k);
        int y = pos.getY();
        Cell next = c == null ? new Cell(1, y, y, color)
                : new Cell(c.count() + 1, Math.min(c.minY(), y), Math.max(c.maxY(), y), y >= c.maxY() ? color : c.color());
        map.put(k, next);
        if (next.count() >= VISIBLE) {
            changed = true;
        }
        setDirty();
    }

    private void remove(String dimension, BlockPos pos) {
        Map<Long, Cell> map = cells.get(dimension);
        if (map == null) {
            return;
        }
        long k = key(Math.floorDiv(pos.getX(), CELL), Math.floorDiv(pos.getZ(), CELL));
        Cell c = map.get(k);
        if (c == null) {
            return;
        }
        if (c.count() <= 1) {
            map.remove(k);
        } else {
            map.put(k, new Cell(c.count() - 1, c.minY(), c.maxY(), c.color()));
        }
        if (c.count() >= VISIBLE) {
            changed = true;
        }
        setDirty();
    }

    /** The cells big enough to see from space, in one world. */
    public Map<Long, Cell> visible(String dimension) {
        Map<Long, Cell> out = new HashMap<>();
        Map<Long, Cell> map = cells.get(dimension);
        if (map != null) {
            map.forEach((k, c) -> {
                if (c.count() >= VISIBLE) {
                    out.put(k, c);
                }
            });
        }
        return out;
    }

    /** Everyone gets the big buildings of every world (they can look at any world from space). */
    private void send(MinecraftServer server, ServerPlayer only) {
        List<LandmarksS2C.World> worlds = new ArrayList<>();
        for (String dimension : cells.keySet()) {
            worlds.add(new LandmarksS2C.World(dimension, visible(dimension)));
        }
        LandmarksS2C msg = new LandmarksS2C(worlds);
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

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 0) {
            return;
        }
        Landmarks data = get(event.getServer());
        if (data.changed) {
            data.changed = false;
            data.send(event.getServer(), null);
        }
    }

    private static Landmarks load(CompoundTag tag, HolderLookup.Provider registries) {
        Landmarks data = new Landmarks();
        for (String dimension : tag.getAllKeys()) {
            Map<Long, Cell> map = new HashMap<>();
            for (Tag t : tag.getList(dimension, Tag.TAG_INT_ARRAY)) {
                int[] a = ((net.minecraft.nbt.IntArrayTag) t).getAsIntArray();
                if (a.length == 6) {
                    map.put(key(a[0], a[1]), new Cell(a[2], a[3], a[4], a[5]));
                }
            }
            data.cells.put(dimension, map);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        cells.forEach((dimension, map) -> {
            ListTag list = new ListTag();
            map.forEach((k, c) -> list.add(new net.minecraft.nbt.IntArrayTag(new int[]{cellX(k), cellZ(k), c.count(), c.minY(), c.maxY(), c.color()})));
            tag.put(dimension, list);
        });
        return tag;
    }
}
