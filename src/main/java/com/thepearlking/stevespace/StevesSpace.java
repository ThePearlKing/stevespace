package com.thepearlking.stevespace;

import com.mojang.logging.LogUtils;
import com.thepearlking.stevespace.client.ClientSetup;
import com.thepearlking.stevespace.config.ClientConfig;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.world.gen.earth.EarthWorldgen;
import com.thepearlking.stevespace.debug.SelfTest;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.registry.ModBlockEntities;
import com.thepearlking.stevespace.registry.ModBlocks;
import com.thepearlking.stevespace.registry.ModCreativeTabs;
import com.thepearlking.stevespace.registry.ModEntities;
import com.thepearlking.stevespace.registry.ModItems;
import com.thepearlking.stevespace.registry.ModParticles;
import com.thepearlking.stevespace.registry.ModWorldGen;
import com.thepearlking.stevespace.vessel.VesselServer;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.world.PlanetSpawn;
import com.thepearlking.stevespace.world.WorldWrap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Steve's Space Program: the overworld becomes a real round Earth inside a real solar system.
 * This class only wires the mod together; each system lives in its own package.
 */
@Mod(StevesSpace.MODID)
public final class StevesSpace {

    public static final String MODID = "stevespace";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StevesSpace(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, SpaceConfig.SPEC);
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        ModWorldGen.CHUNK_GENERATORS.register(modBus);
        ModWorldGen.BIOME_SOURCES.register(modBus);
        ModWorldGen.DENSITY_FUNCTIONS.register(modBus);
        com.thepearlking.stevespace.suit.SpaceSuit.init();
        com.thepearlking.stevespace.registry.ModFluids.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        com.thepearlking.stevespace.registry.ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        com.thepearlking.stevespace.registry.ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        SpaceNetwork.register();

        MinecraftForge.EVENT_BUS.register(EarthWorldgen.class);
        MinecraftForge.EVENT_BUS.register(PlanetSpawn.class);
        MinecraftForge.EVENT_BUS.register(WorldWrap.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.world.WorldSeam.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.world.BodyGravity.class);
        MinecraftForge.EVENT_BUS.register(ServerUniverse.class);
        MinecraftForge.EVENT_BUS.register(VesselServer.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.suit.Oxygen.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.radio.Radio.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.remote.RemoteControl.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.nuke.BlastJob.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.vessel.LaunchBeacon.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.nuke.NuclearAftermath.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.suit.Habitats.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.vessel.SpaceCrew.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.command.SpaceCommands.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.world.Landmarks.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.world.WeatherSystems.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.world.AcheronEvent.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.vessel.PlacedBlocks.class);
        if (SelfTest.enabled()) {
            MinecraftForge.EVENT_BUS.register(SelfTest.class);
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
    }
}
