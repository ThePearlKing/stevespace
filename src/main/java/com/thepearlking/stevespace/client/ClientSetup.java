package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.client.gui.config.SpaceConfigScreen;
import com.thepearlking.stevespace.client.particle.ExhaustParticles;
import com.thepearlking.stevespace.client.sky.SkyEvents;
import com.thepearlking.stevespace.client.vessel.VesselCamera;
import com.thepearlking.stevespace.client.vessel.VesselEffects;
import com.thepearlking.stevespace.client.vessel.VesselRenderer;
import com.thepearlking.stevespace.debug.ClientTest;
import com.thepearlking.stevespace.registry.ModEntities;
import com.thepearlking.stevespace.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only wiring. Only ever loaded on the physical client. */
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.register(ClientUniverse.class);
        MinecraftForge.EVENT_BUS.register(SkyEvents.class);
        MinecraftForge.EVENT_BUS.register(FlightInput.class);
        MinecraftForge.EVENT_BUS.register(WorldEffects.class);
        MinecraftForge.EVENT_BUS.register(RadiationCounter.class);
        MinecraftForge.EVENT_BUS.register(VesselEffects.class);
        MinecraftForge.EVENT_BUS.register(VesselCamera.class);
        MinecraftForge.EVENT_BUS.register(RemoteClient.class);
        MinecraftForge.EVENT_BUS.register(PlasmaSound.class);
        MinecraftForge.EVENT_BUS.register(ClientWeather.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.planet.WorldCurve.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.sky.GiantSounds.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.vessel.LaunchTrails.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.nuke.NukeClient.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.planet.FarStructures.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.vessel.CabinWalk.class);
        com.thepearlking.stevespace.client.vessel.CabinWalk.install();
        MinecraftForge.EVENT_BUS.register(TelescopeView.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.telescope.TelescopeShips.class);
        MinecraftForge.EVENT_BUS.register(com.thepearlking.stevespace.client.space.ClientSpace.class);
        modBus.addListener(SkyEvents::onRegisterEffects);
        modBus.addListener(SpaceShaders::onRegisterShaders);
        modBus.addListener(FlightKeys::register);
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ClientSetup::onRegisterParticles);
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onBlockColors);
        MinecraftForge.registerConfigScreen(parent -> new SpaceConfigScreen(parent));
        if (ClientTest.enabled()) {
            MinecraftForge.EVENT_BUS.register(ClientTest.class);
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.VESSEL.get(), VesselRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(com.thepearlking.stevespace.registry.ModBlockEntities.FLIGHT_DISPLAY.get(),
                com.thepearlking.stevespace.client.FlightDisplayRenderer::new);
        event.registerBlockEntityRenderer(com.thepearlking.stevespace.registry.ModBlockEntities.MAGNETOMETER.get(),
                com.thepearlking.stevespace.client.ScienceRenderers.Magnetometer::new);
        event.registerBlockEntityRenderer(com.thepearlking.stevespace.registry.ModBlockEntities.PLASMA_RECEIVER.get(),
                com.thepearlking.stevespace.client.ScienceRenderers.Plasma::new);
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.EXHAUST_FLAME.get(), ExhaustParticles.FlameProvider::new);
        event.registerSpriteSet(ModParticles.EXHAUST_SMOKE.get(), ExhaustParticles.SmokeProvider::new);
    }

    /** Vanilla's HUD (crosshair, hotbar, chat...) steps aside while you look through a telescope. */
    @SuppressWarnings("unchecked")
    private static void hideWhileObserving(LayeredDraw draw) {
        try {
            for (java.lang.reflect.Field field : LayeredDraw.class.getDeclaredFields()) {
                if (java.util.List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    java.util.List<LayeredDraw.Layer> list = (java.util.List<LayeredDraw.Layer>) field.get(draw);
                    list.replaceAll(layer -> (g, delta) -> {
                        if (!TelescopeView.active()) {
                            layer.render(g, delta);
                        }
                    });
                }
            }
        } catch (ReflectiveOperationException e) {
            com.thepearlking.stevespace.StevesSpace.LOGGER.warn("Could not reach the HUD layers: {}", e.toString());
        }
    }

    /** The flag's cloth takes the colour of the dye used on it. */
    private static void onBlockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 1
                ? state.getValue(com.thepearlking.stevespace.block.FlagBlock.COLOR).getTextureDiffuseColor() : -1,
                com.thepearlking.stevespace.registry.ModBlocks.FLAG.get());
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(com.thepearlking.stevespace.registry.ModFluids.METHANE.get(),
                net.minecraft.client.renderer.RenderType.translucent());
        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(com.thepearlking.stevespace.registry.ModFluids.METHANE_FLOWING.get(),
                net.minecraft.client.renderer.RenderType.translucent());
        // Forge 52 has no HUD overlay event; the flight HUD joins vanilla's own HUD layers.
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(com.thepearlking.stevespace.registry.ModMenus.MACHINE.get(),
                    com.thepearlking.stevespace.client.gui.MachineScreen::new);
            LayeredDraw layers = ObfuscationReflectionHelper.getPrivateValue(Gui.class, Minecraft.getInstance().gui, "f_316662_");
            if (layers != null) {
                hideWhileObserving(layers);
                layers.add(FlightHud::render);
                layers.add(TelescopeView::render);
                layers.add(OxygenHud::render);
                layers.add(RemoteClient::render);
                layers.add(com.thepearlking.stevespace.client.nuke.NukeClient::renderOverlay);
            }
        });
    }
}
