package com.thepearlking.stevespace.block;

import com.thepearlking.stevespace.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible entity a player rides while sitting in a seat block on the ground. Removes itself
 * when its rider gets up or the seat is broken.
 */
public class SeatEntity extends Entity {

    /** Height of the cushion surface above the block's bottom. */
    public static final double SEAT_HEIGHT = 7 / 16.0;

    private boolean allowDismount;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SeatEntity create(Level level, BlockPos seat) {
        SeatEntity entity = new SeatEntity(ModEntities.SEAT.get(), level);
        entity.setPos(seat.getX() + 0.5, seat.getY() + SEAT_HEIGHT, seat.getZ() + 0.5);
        return entity;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        boolean seatGone = !(level().getBlockState(blockPosition()).getBlock() instanceof SeatBlock);
        if (getPassengers().isEmpty() || seatGone) {
            allowDismount = true;
            ejectPassengers();
            discard();
        }
    }

    /** Pilot seats keep you seated on sneak (it is the throttle key); this is the way out. */
    public void requestDismount(Entity passenger) {
        allowDismount = true;
        passenger.stopRiding();
        allowDismount = false;
    }

    public boolean allowsDismount() {
        return allowDismount || isRemoved();
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Vec3 attach = passenger.getVehicleAttachmentPoint(this);
        move.accept(passenger, getX() - attach.x, getY() - attach.y, getZ() - attach.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(getX(), blockPosition().getY() + 1.0, getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
