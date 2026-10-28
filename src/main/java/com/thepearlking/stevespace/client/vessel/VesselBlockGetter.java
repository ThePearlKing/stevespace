package com.thepearlking.stevespace.client.vessel;

import com.thepearlking.stevespace.vessel.VesselStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * Lets Minecraft's block renderer read a vessel's blocks as if they were a tiny world, so the
 * mesh gets correct face culling and ambient occlusion between the vessel's own blocks.
 * Light is baked as full skylight; the lightmap then darkens it with the time of day.
 */
final class VesselBlockGetter implements BlockAndTintGetter {

    private final VesselStructure structure;
    private final Biome biome;

    VesselBlockGetter(VesselStructure structure, Biome biome) {
        this.structure = structure;
        this.biome = biome;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = structure.get(pos);
        return state == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getShade(direction, shade) : 1f;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return Minecraft.getInstance().level.getLightEngine();
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return layer == LightLayer.SKY ? 15 : 0;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15 - darkening;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return resolver.getColor(biome, pos.getX(), pos.getZ());
    }

    @Override
    public int getHeight() {
        return 4096;
    }

    @Override
    public int getMinBuildHeight() {
        return -2048;
    }
}
