package com.thepearlking.stevespace.suit;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.List;

/**
 * The space suit: helmet, suit, trousers and boots. Helmet and suit together keep you breathing
 * where there is no air (the suit carries the oxygen); the rest keeps you warm and protected.
 */
public final class SpaceSuit {

    /** Not in the material registry (it is built after items in 1.21); only its layers and stats matter. */
    private static final Holder<ArmorMaterial> MATERIAL = Holder.direct(new ArmorMaterial(
            new EnumMap<>(java.util.Map.of(ArmorItem.Type.HELMET, 2, ArmorItem.Type.CHESTPLATE, 4,
                    ArmorItem.Type.LEGGINGS, 3, ArmorItem.Type.BOOTS, 2, ArmorItem.Type.BODY, 4)),
            10, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.WHITE_WOOL),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "space_suit"))), 0f, 0f));

    public static final RegistryObject<Item> HELMET = piece("space_suit_helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> SUIT = piece("space_suit", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> TROUSERS = piece("space_suit_trousers", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> BOOTS = piece("space_suit_boots", ArmorItem.Type.BOOTS);
    /** The special suit bodies, one per kind of hazard; any of them seals like the plain suit. */
    public static final RegistryObject<Item> HEAT_SUIT = body("heat_shielded_space_suit", "heat_suit", SoundEvents.ARMOR_EQUIP_GOLD, 5);
    public static final RegistryObject<Item> INSULATED_SUIT = body("insulated_space_suit", "insulated_suit", SoundEvents.ARMOR_EQUIP_LEATHER, 4);
    public static final RegistryObject<Item> RADIATION_SUIT = body("radiation_shielded_space_suit", "radiation_suit", SoundEvents.ARMOR_EQUIP_IRON, 6);

    private SpaceSuit() {
    }

    /** Called while the mod declares its items. */
    public static void init() {
    }

    private static RegistryObject<Item> body(String id, String layer, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, int defense) {
        Holder<ArmorMaterial> material = Holder.direct(new ArmorMaterial(
                new EnumMap<>(java.util.Map.of(ArmorItem.Type.HELMET, 2, ArmorItem.Type.CHESTPLATE, defense,
                        ArmorItem.Type.LEGGINGS, 3, ArmorItem.Type.BOOTS, 2, ArmorItem.Type.BODY, defense)),
                10, sound, () -> Ingredient.of(Items.WHITE_WOOL),
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, layer))), 0.5f, 0f));
        return ModItems.ITEMS.register(id, () -> new ArmorItem(material, ArmorItem.Type.CHESTPLATE,
                new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(25))));
    }

    private static RegistryObject<Item> piece(String id, ArmorItem.Type type) {
        return ModItems.ITEMS.register(id, () -> new ArmorItem(MATERIAL, type, new Item.Properties().durability(type.getDurability(20))));
    }

    /** Helmet and suit on (any suit body): breathing from the suit's tanks. */
    public static boolean sealed(LivingEntity entity) {
        var chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(HELMET.get())
                && (chest.is(SUIT.get()) || chest.is(HEAT_SUIT.get()) || chest.is(INSULATED_SUIT.get()) || chest.is(RADIATION_SUIT.get()));
    }

    /** Sealed, with this suit body on. */
    public static boolean wearing(LivingEntity entity, RegistryObject<Item> body) {
        return sealed(entity) && entity.getItemBySlot(EquipmentSlot.CHEST).is(body.get());
    }
}
