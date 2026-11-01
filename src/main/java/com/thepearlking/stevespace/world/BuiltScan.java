package com.thepearlking.stevespace.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds what's been built by looking at the blocks themselves, so it doesn't matter how they got
 * there (by hand, /fill, WorldEdit, Axiom...): each chunk remembers how high its ground was when
 * it was made, and everything solid standing above that counts as built. Feeds {@link Landmarks}.
 */
public final class BuiltScan {

    private static final String TAG = "stevespace_natural";
    /** Changed chunks looked at per pass (every five seconds). */
    private static final int PER_PASS = 400;

    /** Per world: chunk → the natural ground's top block height in each of its 256 columns. */
    private static final Map<String, Map<Long, int[]>> NATURAL = new ConcurrentHashMap<>();
    /** Chunks made before this was added: their ground is estimated, and trees on them don't count. */
    private static final Map<String, Map<Long, Boolean>> ESTIMATED = new ConcurrentHashMap<>();
    private static final Map<String, Map<Long, LevelChunk>> LOADED = new ConcurrentHashMap<>();
    private static final ArrayDeque<LevelChunk> QUEUE = new ArrayDeque<>();

    private BuiltScan() {
    }

    private static String id(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static boolean counts(Object level) {
        return level instanceof ServerLevel server && !SpaceDimension.isSpace(server);
    }

    @SubscribeEvent
    public static void onChunkData(ChunkDataEvent.Load event) {
        if (!counts(event.getLevel()) || !event.getData().contains(TAG)) {
            return;
        }
        int[] heights = event.getData().getIntArray(TAG);
        if (heights.length == 256) {
            NATURAL.computeIfAbsent(id((ServerLevel) event.getLevel()), k -> new ConcurrentHashMap<>())
                    .put(event.getChunk().getPos().toLong(), heights);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!counts(event.getLevel()) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        String dim = id(level);
        long key = chunk.getPos().toLong();
        LOADED.computeIfAbsent(dim, k -> new ConcurrentHashMap<>()).put(key, chunk);
        Map<Long, int[]> natural = NATURAL.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        if (natural.containsKey(key)) {
            QUEUE.add(chunk);
            return;
        }
        int[] heights = new int[256];
        if (event.isNewChunk()) {
            // Just made: its ground (trees included) is the natural ground.
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    heights[x * 16 + z] = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                }
            }
            natural.put(key, heights);
            return;
        }
        // Made before this existed: ask the world generator how high the ground was (sampled every
        // 4 blocks and blended in between).
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        int[][] grid = new int[5][5];
        try {
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    grid[i][j] = generator.getBaseHeight(x0 + i * 4, z0 + j * 4, Heightmap.Types.WORLD_SURFACE_WG, level, random) - 1;
                }
            }
        } catch (RuntimeException e) {
            return; // a generator that can't say: this chunk isn't looked at
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int i = x / 4;
                int j = z / 4;
                double fx = (x % 4) / 4.0;
                double fz = (z % 4) / 4.0;
                double h = grid[i][j] * (1 - fx) * (1 - fz) + grid[i + 1][j] * fx * (1 - fz)
                        + grid[i][j + 1] * (1 - fx) * fz + grid[i + 1][j + 1] * fx * fz;
                heights[x * 16 + z] = (int) Math.round(h);
            }
        }
        natural.put(key, heights);
        ESTIMATED.computeIfAbsent(dim, k -> new ConcurrentHashMap<>()).put(key, true);
        QUEUE.add(chunk); // (things may have been built on it already)
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!counts(event.getLevel()) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        if (chunk.isUnsaved()) {
            scan(level, chunk);
        }
        Map<Long, LevelChunk> loaded = LOADED.get(id(level));
        if (loaded != null) {
            loaded.remove(chunk.getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onChunkSave(ChunkDataEvent.Save event) {
        if (!counts(event.getLevel())) {
            return;
        }
        String dim = id((ServerLevel) event.getLevel());
        long key = event.getChunk().getPos().toLong();
        Map<Long, int[]> natural = NATURAL.get(dim);
        int[] heights = natural == null ? null : natural.get(key);
        if (heights == null) {
            return;
        }
        Map<Long, Boolean> estimated = ESTIMATED.get(dim);
        // An estimated ground isn't saved: next time it's estimated again (same answer), still
        // knowing to leave trees out.
        if (estimated == null || !estimated.containsKey(key)) {
            event.getData().putIntArray(TAG, heights);
        }
        Map<Long, LevelChunk> loaded = LOADED.get(dim);
        if (loaded == null || !loaded.containsKey(key)) {
            natural.remove(key); // unloading: done with it
            if (estimated != null) {
                estimated.remove(key);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        var server = event.getServer();
        // Chunks waiting since they loaded: a few each tick.
        for (int n = 0; n < 8 && !QUEUE.isEmpty(); n++) {
            LevelChunk chunk = QUEUE.poll();
            if (chunk.getLevel() instanceof ServerLevel level && LOADED.getOrDefault(id(level), Map.of()).get(chunk.getPos().toLong()) == chunk) {
                scan(level, chunk);
            }
        }
        if (server.getTickCount() % 100 != 0) {
            return;
        }
        int budget = PER_PASS;
        for (ServerLevel level : server.getAllLevels()) {
            Map<Long, LevelChunk> loaded = LOADED.get(id(level));
            if (loaded == null) {
                continue;
            }
            for (LevelChunk chunk : loaded.values()) {
                if (budget <= 0) {
                    return;
                }
                if (chunk.isUnsaved()) {
                    scan(level, chunk);
                    budget--;
                }
            }
        }
    }

    /** Counts what stands above the natural ground in a chunk's four 8 x 8 cells. */
    static void scan(ServerLevel level, LevelChunk chunk) {
        String dim = id(level);
        long key = chunk.getPos().toLong();
        Map<Long, int[]> natural = NATURAL.get(dim);
        int[] heights = natural == null ? null : natural.get(key);
        if (heights == null) {
            return;
        }
        Map<Long, Boolean> estimatedMap = ESTIMATED.get(dim);
        boolean estimated = estimatedMap != null && estimatedMap.containsKey(key);
        // The top of the highest section with anything in it.
        LevelChunkSection[] sections = chunk.getSections();
        int top = Integer.MIN_VALUE;
        for (int s = sections.length - 1; s >= 0; s--) {
            if (!sections[s].hasOnlyAir()) {
                top = level.getSectionYFromSectionIndex(s) * 16 + 15;
                break;
            }
        }
        int[] count = new int[4];
        int[] minY = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] maxY = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] color = new int[4];
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int c = (x / 8) * 2 + (z / 8);
                for (int y = heights[x * 16 + z] + 1; y <= top; y++) {
                    pos.set(x0 + x, y, z0 + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir() || state.getBlock() instanceof LiquidBlock
                            || state.getCollisionShape(chunk, pos).isEmpty()) {
                        continue; // (grass, flowers, torches, water: not a building)
                    }
                    if (estimated && (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
                        continue;
                    }
                    count[c]++;
                    minY[c] = Math.min(minY[c], y);
                    if (y >= maxY[c]) {
                        maxY[c] = y;
                        int col = state.getMapColor(chunk, pos).col;
                        color[c] = col == 0 ? 0x9A9A9A : col;
                    }
                }
            }
        }
        Landmarks data = Landmarks.get(level.getServer());
        int cx0 = Math.floorDiv(x0, Landmarks.CELL);
        int cz0 = Math.floorDiv(z0, Landmarks.CELL);
        for (int c = 0; c < 4; c++) {
            data.setCell(dim, cx0 + c / 2, cz0 + c % 2,
                    count[c] == 0 ? null : new Landmarks.Cell(count[c], minY[c], maxY[c], color[c]));
        }
    }
}
