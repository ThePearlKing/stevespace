package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, StevesSpace.MODID);

    /** Bright, short-lived flame right behind a nozzle. */
    public static final RegistryObject<SimpleParticleType> EXHAUST_FLAME = PARTICLES.register("exhaust_flame",
            () -> new SimpleParticleType(true));
    /** Grey exhaust smoke that billows and lingers. */
    public static final RegistryObject<SimpleParticleType> EXHAUST_SMOKE = PARTICLES.register("exhaust_smoke",
            () -> new SimpleParticleType(true));

    private ModParticles() {
    }
}
