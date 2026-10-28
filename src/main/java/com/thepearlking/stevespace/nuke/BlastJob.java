package com.thepearlking.stevespace.nuke;

import com.thepearlking.stevespace.vessel.VesselAssembler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The ground damage of a blast (only with destruction on), done as the shock front passes, not
 * all at once: a crater where the fireball touched the ground; inside 20 psi every built thing
 * and tree knocked flat; inside 5 psi the weak stuff (glass, leaves, wood, wool, doors) gone;
 * windows broken out to 1 psi; fires where the flash could see, out to the burn distance.
 * Creatures caught by each ring are hurt as it reaches them.
 */
public final class BlastJob {

    /** Columns looked at per tick, so even a 50 megaton blast never stalls the server. */
    private static final int BUDGET = 2500;

    private static final List<BlastJob> JOBS = new ArrayList<>();

    private final ServerLevel level;
    private final Vec3 centre;
    private final double groundY;
    private final double crater;
    private final double r20;
    private final double r5;
    private final double r1;
    private final double burn;
    private final List<int[]> columns = new ArrayList<>();
    private int next;
    private final Set<UUID> hit = new HashSet<>();
    private final long start;
    private int fires;

    private BlastJob(ServerLevel level, Vec3 centre, double kt, NuclearEffects.Regime regime, double groundY) {
        this.level = level;
        this.centre = centre;
        this.groundY = groundY;
        this.crater = regime == NuclearEffects.Regime.SURFACE ? NuclearEffects.blocks(NuclearEffects.craterKm(kt)) : 0;
        this.r20 = NuclearEffects.blocks(NuclearEffects.blastKm(kt, 20));
        this.r5 = NuclearEffects.blocks(NuclearEffects.blastKm(kt, 5));
        this.r1 = NuclearEffects.blocks(NuclearEffects.blastKm(kt, 1));
        this.burn = regime == NuclearEffects.Regime.VACUUM ? 0 : NuclearEffects.blocks(NuclearEffects.burnKm(kt));
        this.start = level.getGameTime();
        int reach = (int) Math.ceil(r1);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz <= reach * reach) {
                    columns.add(new int[]{dx, dz});
                }
            }
        }
        columns.sort(java.util.Comparator.comparingInt(c -> c[0] * c[0] + c[1] * c[1]));
    }

    static void start(ServerLevel level, Vec3 centre, double kt, NuclearEffects.Regime regime, double groundY) {
        BlastJob job = new BlastJob(level, centre, kt, regime, groundY);
        job.flash();
        JOBS.add(job);
    }

    /** The shock front's distance from ground zero, blocks, this many seconds after (faster than sound close in). */
    private double front(double seconds) {
        // Time to reach r: the sound delay there, but about half that inside the 5 psi ring.
        double lo = 0;
        double hi = r1 * 2 + 10;
        for (int i = 0; i < 30; i++) {
            double r = (lo + hi) / 2;
            double reach = NuclearEffects.soundDelay(r) * (0.5 + 0.5 * Math.min(1, r / Math.max(1, r5)));
            if (reach < seconds) {
                lo = r;
            } else {
                hi = r;
            }
        }
        return lo;
    }

    /** The thermal flash: fires and burns wherever it can see, at once (it moves at the speed of light). */
    private void flash() {
        if (burn <= 0) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Entity e : level.getEntities((Entity) null, new AABB(centre, centre).inflate(burn), en -> en instanceof LivingEntity)) {
            double d = e.position().distanceTo(centre);
            if (d < burn && sees(e.getEyePosition())) {
                e.setRemainingFireTicks((int) (20 * 10 * (1 - d / burn)) + 40);
            }
        }
        int attempts = (int) Math.min(6000, burn * burn * 0.15);
        for (int i = 0; i < attempts; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * burn;
            if (random.nextDouble() > 1 - d / burn * 0.85) {
                continue; // fewer fires further out
            }
            int x = (int) Math.floor(centre.x + Math.cos(a) * d);
            int z = (int) Math.floor(centre.z + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos top = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(top.below());
            if (level.getBlockState(top).isAir() && below.isFlammable(level, top.below(), net.minecraft.core.Direction.UP)
                    && sees(Vec3.atCenterOf(top))) {
                level.setBlock(top, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
                fires++;
            }
        }
    }

    private boolean sees(Vec3 point) {
        var hit = level.clip(new ClipContext(centre, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContextHolder.NONE));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 4;
    }

    /** One tick: the rings the shock front has reached since the last one. */
    private boolean tick() {
        double seconds = (level.getGameTime() - start) / 20.0;
        double reached = front(seconds);
        int done = 0;
        while (next < columns.size() && done < BUDGET) {
            int[] c = columns.get(next);
            double d = Math.sqrt(c[0] * c[0] + c[1] * c[1]);
            if (d > reached) {
                break;
            }
            column((int) Math.floor(centre.x) + c[0], (int) Math.floor(centre.z) + c[1], d);
            next++;
            done++;
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(centre, centre).inflate(Math.min(reached, r1)), en -> en instanceof LivingEntity)) {
            double d = e.position().distanceTo(centre);
            if (d > reached || !hit.add(e.getUUID())) {
                continue;
            }
            float damage = d < r20 ? 1000 : d < r5 ? 24 : 4;
            e.hurt(level.damageSources().explosion(null, null), damage);
            Vec3 away = e.position().subtract(centre).normalize().scale(d < r5 ? 2.5 : 0.8);
            e.push(away.x, 0.4, away.z);
        }
        return next < columns.size();
    }

    private void column(int x, int z, double d) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        // The crater: a bowl, half as deep as it is wide.
        if (d < crater) {
            double depth = crater * 0.5 * Math.sqrt(1 - (d / crater) * (d / crater));
            int bottom = (int) Math.floor(groundY - depth);
            for (int y = top; y >= Math.max(level.getMinBuildHeight() + 1, bottom); y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (!level.getBlockState(p).is(Blocks.BEDROCK)) {
                    clear(p);
                }
            }
            return;
        }
        // Above the natural ground: built things and plants, by how hard the wave hits here.
        for (int y = top; y >= level.getMinBuildHeight(); y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) {
                continue;
            }
            boolean built = VesselAssembler.isVesselBlock(level, p, s);
            boolean plant = s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.REPLACEABLE);
            if (!built && !plant) {
                // Natural ground: the surface scorched close in, then done with this column.
                if (d < r20 && s.is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                break;
            }
            if (d < r20 || weak(s) && d < r5 || glass(s) && d < r1) {
                clear(p);
            }
        }
    }

    private static boolean weak(BlockState s) {
        return glass(s) || s.is(BlockTags.LEAVES) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.DOORS)
                || s.is(BlockTags.FENCES) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.REPLACEABLE) || s.is(BlockTags.CROPS) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)
                || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.BEDS);
    }

    private static boolean glass(BlockState s) {
        return s.is(net.minecraftforge.common.Tags.Blocks.GLASS_BLOCKS) || s.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES);
    }

    private void clear(BlockPos p) {
        level.removeBlockEntity(p);
        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        JOBS.removeIf(job -> !job.tick());
    }

    /** ClipContext needs a collision context with no entity behind it. */
    private static final class CollisionContextHolder {
        static final net.minecraft.world.phys.shapes.CollisionContext NONE = net.minecraft.world.phys.shapes.CollisionContext.empty();
    }
}
