package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Times a geyser's eruptions (each vent has its own rhythm) and does the plume and the push. */
public class GeyserBlockEntity extends BlockEntity {

    /** How high above the vent the plume lifts things. */
    private static final int REACH = 14;

    public GeyserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GEYSER.get(), pos, state);
    }

    /** True while this vent is erupting. */
    public static boolean erupting(Level level, BlockPos pos) {
        long h = pos.asLong() * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        int period = 360 + (int) ((h >>> 8) & 255);
        long phase = Math.floorMod(level.getGameTime() + (h >>> 20), period);
        return phase < 70;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GeyserBlockEntity geyser) {
        if (!erupting(level, pos) || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        int kind = state.getBlock() instanceof GeyserBlock g ? g.kind() : GeyserBlock.ICE;
        boolean dark = kind == GeyserBlock.DARK;
        if (level.isClientSide && kind == GeyserBlock.LAVA) {
            // A fountain of molten rock: glowing spatter flung up and out, dark fume above.
            var random = level.random;
            for (int i = 0; i < 3; i++) {
                level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, pos.getX() + 0.5 + random.nextGaussian() * 0.2,
                        pos.getY() + 1.2, pos.getZ() + 0.5 + random.nextGaussian() * 0.2,
                        random.nextGaussian() * 0.02, 0.25 + random.nextDouble() * 0.2, random.nextGaussian() * 0.02);
            }
            for (int i = 0; i < 2; i++) {
                level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0, 0);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + random.nextGaussian() * 0.12, pos.getY() + 1.1,
                        pos.getZ() + 0.5 + random.nextGaussian() * 0.12, random.nextGaussian() * 0.03, 0.4 + random.nextDouble() * 0.4,
                        random.nextGaussian() * 0.03);
            }
        } else if (level.isClientSide) {
            var random = level.random;
            for (int i = 0; i < 4; i++) {
                double x = pos.getX() + 0.5 + random.nextGaussian() * 0.15;
                double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.15;
                level.addAlwaysVisibleParticle(dark ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                        x, pos.getY() + 1.1, z, random.nextGaussian() * 0.01, 0.35 + random.nextDouble() * 0.25, random.nextGaussian() * 0.01);
            }
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                        random.nextGaussian() * 0.08, 0.9 + random.nextDouble() * 0.5, random.nextGaussian() * 0.08);
            }
        }
        // Lift whatever is in the plume (both sides, so the local player feels it at once).
        AABB plume = new AABB(pos.getX() - 0.2, pos.getY() + 1, pos.getZ() - 0.2, pos.getX() + 1.2, pos.getY() + 1 + REACH, pos.getZ() + 1.2);
        for (Entity entity : level.getEntities((Entity) null, plume, e -> !e.isSpectator() && !e.isPassenger())) {
            var v = entity.getDeltaMovement();
            if (v.y < 1.1) {
                entity.setDeltaMovement(v.x, 1.1, v.z);
                entity.hurtMarked = true;
                entity.resetFallDistance();
            }
            if (kind == GeyserBlock.LAVA && !level.isClientSide && !entity.fireImmune()) {
                entity.igniteForSeconds(4);
            }
        }
    }
}
