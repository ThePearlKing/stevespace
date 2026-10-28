package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** How much propellant (tonnes) a tank holds while it sits in the world. */
public class PropellantTankBlockEntity extends BlockEntity {

    private double propellant;

    public PropellantTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROPELLANT_TANK.get(), pos, state);
    }

    public double propellant() {
        return propellant;
    }

    public void setPropellant(double tonnes) {
        propellant = Math.max(0, Math.min(PropellantTankBlock.CAPACITY, tonnes));
        setChanged();
    }

    /** Adds up to {@code tonnes}; returns how much actually fit. */
    public double fill(double tonnes) {
        double room = PropellantTankBlock.CAPACITY - propellant;
        double added = Math.max(0, Math.min(room, tonnes));
        setPropellant(propellant + added);
        return added;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        propellant = tag.getDouble("propellant");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("propellant", propellant);
    }
}
