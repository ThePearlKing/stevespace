package com.thepearlking.stevespace.client.planet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ships, bases and anything else built from the mod's parts stay in sight past the render
 * distance. Whenever the client has a chunk, it notes where such blocks are; once that chunk is
 * gone (too far away), they are drawn as plain shaded blocks on the distant ground, with the
 * same curve, light and haze, so a landed rocket doesn't vanish while the hills behind it stay.
 * Only remembered on this client, from what it has seen; nothing is saved.
 */
public final class FarStructures {

    private static final int MAX_PER_CHUNK = 8192;

    private record Seen(long[] positions, int[] colors) {
    }

    /** Dimension → chunk → what was there when last seen. */
    private static final Map<String, Map<Long, Seen>> SEEN = new HashMap<>();
    private static Set<Block> shipBlocks;
    private static int version;

    private static VertexBuffer buffer;
    private static boolean empty = true;
    private static int builtVersion = -1;
    private static long builtShown;
    private static double builtX = Double.NaN;
    private static double builtZ = Double.NaN;
    private static String builtDimension;

    private FarStructures() {
    }

    /** The mod's own building parts (not its natural world blocks: those are tagged not_vessel). */
    private static boolean isShipBlock(BlockState state) {
        if (shipBlocks == null) {
            Set<Block> set = new HashSet<>();
            for (Block b : BuiltInRegistries.BLOCK) {
                if (BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(StevesSpace.MODID)
                        && b.defaultBlockState().getFluidState().isEmpty()) {
                    set.add(b);
                }
            }
            shipBlocks = set;
        }
        return shipBlocks.contains(state.getBlock()) && !state.is(com.thepearlking.stevespace.vessel.VesselAssembler.NOT_VESSEL);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            remember(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        // What it looks like as it goes out of range is what stays drawn.
        if (event.getLevel().isClientSide() && event.getChunk() instanceof LevelChunk chunk) {
            remember(chunk);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SEEN.clear();
        version++;
    }

    private static void remember(LevelChunk chunk) {
        String dimension = chunk.getLevel().dimension().location().toString();
        ChunkPos cp = chunk.getPos();
        java.util.List<Long> positions = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length && positions.size() < MAX_PER_CHUNK; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(FarStructures::isShipBlock)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (isShipBlock(state)) {
                            pos.set(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z);
                            positions.add(pos.asLong());
                            int c = state.getMapColor(chunk.getLevel(), pos).col;
                            colors.add(c == 0 ? 0x9A9A9A : c);
                        }
                    }
                }
            }
        }
        Map<Long, Seen> map = SEEN.computeIfAbsent(dimension, d -> new HashMap<>());
        Seen before = map.get(cp.toLong());
        if (positions.isEmpty()) {
            if (before != null) {
                map.remove(cp.toLong());
                version++;
            }
            return;
        }
        long[] p = positions.stream().mapToLong(Long::longValue).toArray();
        int[] c = colors.stream().mapToInt(Integer::intValue).toArray();
        if (before == null || !java.util.Arrays.equals(before.positions, p) || !java.util.Arrays.equals(before.colors, c)) {
            map.put(cp.toLong(), new Seen(p, c));
            version++;
        }
    }

    /**
     * Draws what's remembered in chunks the client no longer has, with the far terrain's shader
     * and uniforms already set (positions relative to the far terrain's centre, heights from sea level).
     */
    static void draw(PoseStack pose, Matrix4f projection, ShaderInstance shader, double centreX, double centreZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        String dimension = mc.level.dimension().location().toString();
        Map<Long, Seen> map = SEEN.get(dimension);
        if (map == null || map.isEmpty()) {
            return;
        }
        // Which remembered chunks are out of reach right now.
        long shown = 17;
        java.util.List<Seen> far = new java.util.ArrayList<>();
        for (Map.Entry<Long, Seen> e : map.entrySet()) {
            ChunkPos cp = new ChunkPos(e.getKey());
            if (!mc.level.getChunkSource().hasChunk(cp.x, cp.z)) {
                far.add(e.getValue());
                shown = shown * 31 + e.getKey();
            }
        }
        if (far.isEmpty()) {
            return;
        }
        if (builtVersion != version || builtShown != shown || centreX != builtX || centreZ != builtZ || !dimension.equals(builtDimension)) {
            build(far, centreX, centreZ);
            builtVersion = version;
            builtShown = shown;
            builtX = centreX;
            builtZ = centreZ;
            builtDimension = dimension;
        }
        if (empty) {
            return;
        }
        RenderSystem.disableCull();
        buffer.bind();
        buffer.drawWithShader(pose.last().pose(), projection, shader);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    private static void build(java.util.List<Seen> far, double cx, double cz) {
        Set<Long> occupied = new HashSet<>();
        for (Seen s : far) {
            for (long p : s.positions) {
                occupied.add(p);
            }
        }
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        boolean any = false;
        for (Seen s : far) {
            for (int i = 0; i < s.positions.length; i++) {
                BlockPos p = BlockPos.of(s.positions[i]);
                int c = s.colors[i];
                float r = ((c >> 16) & 255) / 255f;
                float g = ((c >> 8) & 255) / 255f;
                float b = (c & 255) / 255f;
                float x = (float) (p.getX() - cx);
                float y = p.getY() - 63;
                float z = (float) (p.getZ() - cz);
                for (Direction d : Direction.values()) {
                    if (occupied.contains(p.relative(d).asLong())) {
                        continue; // hidden against its neighbour
                    }
                    face(builder, x, y, z, d, r, g, b);
                    any = true;
                }
            }
        }
        MeshData mesh = builder.build();
        empty = !any || mesh == null;
        if (empty) {
            if (mesh != null) {
                mesh.close();
            }
            return;
        }
        if (buffer == null) {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
    }

    /** One face of the unit cube at (x, y, z) facing {@code d}. */
    private static void face(BufferBuilder b, float x, float y, float z, Direction d, float r, float g, float bl) {
        float[][] corners = switch (d) {
            case UP -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case DOWN -> new float[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case NORTH -> new float[][]{{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
            case SOUTH -> new float[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
            case WEST -> new float[][]{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
            case EAST -> new float[][]{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
        };
        for (float[] c : corners) {
            b.addVertex(x + c[0], y + c[1], z + c[2]).setColor(r, g, bl, 1f).setNormal(d.getStepX(), d.getStepY(), d.getStepZ());
        }
    }
}
