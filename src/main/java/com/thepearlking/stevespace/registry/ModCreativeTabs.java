package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StevesSpace.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.stevespace"))
            .icon(() -> new ItemStack(ModBlocks.ROCKET_ENGINE.get()))
            .displayItems((params, output) -> ModItems.ITEMS.getEntries().stream()
                    .filter(item -> !isWorldItem(item.get()))
                    .forEach(item -> output.accept(item.get())))
            .build());

    /** The ground and raw materials of the other worlds, in their own tab. */
    public static final RegistryObject<CreativeModeTab> WORLDS = TABS.register("worlds", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.stevespace.worlds"))
            .icon(() -> new ItemStack(WorldBlocks.get("venus_basalt")))
            .withTabsBefore(MAIN.getKey())
            .displayItems((params, output) -> {
                WorldBlocks.blocks().forEach(block -> output.accept(block.get()));
                WorldBlocks.items().forEach(item -> output.accept(item.get()));
            })
            .build());

    private static boolean isWorldItem(net.minecraft.world.item.Item item) {
        return WorldBlocks.blocks().stream().anyMatch(b -> b.get().asItem() == item)
                || WorldBlocks.items().stream().anyMatch(i -> i.get() == item);
    }

    private ModCreativeTabs() {
    }
}
