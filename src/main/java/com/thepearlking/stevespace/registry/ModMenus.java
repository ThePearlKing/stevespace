package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.energy.MachineMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, StevesSpace.MODID);

    public static final RegistryObject<MenuType<MachineMenu>> MACHINE = MENUS.register("machine",
            () -> IForgeMenuType.create(MachineMenu::new));

    private ModMenus() {
    }
}
