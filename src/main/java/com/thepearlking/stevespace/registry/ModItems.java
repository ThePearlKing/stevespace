package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StevesSpace.MODID);

    /** A handheld radiation counter: hold it to hear and read the dose rate where you are. */
    public static final net.minecraftforge.registries.RegistryObject<Item> RADIATION_COUNTER = ITEMS.register("radiation_counter",
            () -> new Item(new Item.Properties().stacksTo(1)));

    /** Crushed ore: smelts to an ingot. */
    public static final net.minecraftforge.registries.RegistryObject<Item> IRON_DUST = ITEMS.register("iron_dust", () -> new Item(new Item.Properties()));
    public static final net.minecraftforge.registries.RegistryObject<Item> GOLD_DUST = ITEMS.register("gold_dust", () -> new Item(new Item.Properties()));
    public static final net.minecraftforge.registries.RegistryObject<Item> COPPER_DUST = ITEMS.register("copper_dust", () -> new Item(new Item.Properties()));

    private ModItems() {
    }
}
