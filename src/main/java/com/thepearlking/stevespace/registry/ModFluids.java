package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * Liquid methane: the seas, lakes and rivers of Titan. Thinner than water (you sink a little
 * faster), clear amber in Titan's light, and good rocket propellant by the bucket.
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, StevesSpace.MODID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, StevesSpace.MODID);

    public static final RegistryObject<FluidType> METHANE_TYPE = TYPES.register("liquid_methane", () -> new FluidType(
            FluidType.Properties.create().descriptionId("block.stevespace.liquid_methane").density(420).viscosity(200)
                    .temperature(94).motionScale(0.012).canExtinguish(true).canSwim(true).canDrown(true)
                    .canConvertToSource(true).supportsBoating(true)) {
        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return ResourceLocation.withDefaultNamespace("block/water_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return ResourceLocation.withDefaultNamespace("block/water_flow");
                }

                @Override
                public ResourceLocation getOverlayTexture() {
                    return ResourceLocation.withDefaultNamespace("block/water_overlay");
                }

                @Override
                public int getTintColor() {
                    return 0xD0806040;
                }

                @Override
                public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                               float darkenWorldAmount, Vector3f fluidFogColor) {
                    return new Vector3f(0.22f, 0.15f, 0.07f);
                }
            });
        }
    });

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(METHANE_TYPE, ModFluids.METHANE, ModFluids.METHANE_FLOWING)
                .block(ModFluids.METHANE_BLOCK).bucket(ModFluids.METHANE_BUCKET).slopeFindDistance(5).tickRate(4);
    }

    public static final RegistryObject<FlowingFluid> METHANE = FLUIDS.register("liquid_methane",
            () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> METHANE_FLOWING = FLUIDS.register("flowing_liquid_methane",
            () -> new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<LiquidBlock> METHANE_BLOCK = ModBlocks.BLOCKS.register("liquid_methane",
            () -> new LiquidBlock(METHANE, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).replaceable()
                    .noCollission().strength(100f).pushReaction(PushReaction.DESTROY).noLootTable().liquid()));
    public static final RegistryObject<Item> METHANE_BUCKET = ModItems.ITEMS.register("liquid_methane_bucket",
            () -> new BucketItem(METHANE, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private ModFluids() {
    }

    public static void register(net.minecraftforge.eventbus.api.IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}
