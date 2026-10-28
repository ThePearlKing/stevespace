package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StevesSpace.MODID);

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f).noSummon().clientTrackingRange(8).build("seat"));

    public static final RegistryObject<EntityType<VesselEntity>> VESSEL = ENTITIES.register("vessel",
            () -> EntityType.Builder.<VesselEntity>of(VesselEntity::new, MobCategory.MISC)
                    .sized(1f, 1f).noSummon().fireImmune().clientTrackingRange(32).updateInterval(1).build("vessel"));

    private ModEntities() {
    }
}
