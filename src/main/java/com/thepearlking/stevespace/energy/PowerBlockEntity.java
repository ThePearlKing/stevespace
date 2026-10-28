package com.thepearlking.stevespace.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A block that holds Forge Energy: a generator, a battery or a machine. Exposes the standard
 * energy capability on every side, so other mods' cables and machines work with it too.
 */
public abstract class PowerBlockEntity extends BlockEntity {

    /** What the block does with power. */
    public enum Role { SOURCE, STORE, SINK }

    /** Energy storage the block itself can fill and drain past the transfer limits. */
    public static final class Buffer extends EnergyStorage {

        Buffer(int capacity, int maxReceive, int maxExtract) {
            super(capacity, maxReceive, maxExtract);
        }

        public void add(int amount) {
            energy = Math.min(capacity, energy + Math.max(0, amount));
        }

        /** Takes {@code amount} if it is all there; returns whether it was. */
        public boolean use(int amount) {
            if (energy < amount) {
                return false;
            }
            energy -= amount;
            return true;
        }

        public void set(int amount) {
            energy = Math.max(0, Math.min(capacity, amount));
        }
    }

    protected final Buffer buffer;
    private final LazyOptional<IEnergyStorage> capability;

    protected PowerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int maxIn, int maxOut) {
        super(type, pos, state);
        this.buffer = new Buffer(capacity, maxIn, maxOut);
        this.capability = LazyOptional.of(() -> buffer);
    }

    public abstract Role role();

    public Buffer buffer() {
        return buffer;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return capability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capability.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", buffer.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.set(tag.getInt("energy"));
    }
}
