package com.thepearlking.stevespace.registry;

import com.thepearlking.stevespace.StevesSpace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, StevesSpace.MODID);

    /** A running rocket engine (looped while it fires). */
    public static final RegistryObject<SoundEvent> ENGINE = SOUNDS.register("engine",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "engine")));

    /** A radio message coming in: squelch and the end-of-message beep. */
    public static final RegistryObject<SoundEvent> RADIO = SOUNDS.register("radio",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "radio")));

    /** What a plasma wave receiver hears, one loop per kind of place. */
    private static final java.util.Map<String, RegistryObject<SoundEvent>> PLASMA = new java.util.LinkedHashMap<>();

    static {
        for (String name : new String[]{"plasma_earth", "plasma_jupiter", "plasma_ganymede", "plasma_saturn", "plasma_solar_wind",
                "plasma_mercury", "plasma_venus", "plasma_moon", "plasma_mars", "plasma_io", "plasma_europa", "plasma_callisto",
                "plasma_titan", "plasma_enceladus", "plasma_uranus", "plasma_neptune", "plasma_triton", "plasma_pluto", "plasma_charon",
                "plasma_sun", "plasma_acheron"}) {
            PLASMA.put(name, SOUNDS.register(name,
                    () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, name))));
        }
    }

    /** A nuclear blast heard close (the shock's crack, then the roar) and far (rolling thunder). */
    public static final RegistryObject<SoundEvent> NUKE_NEAR = SOUNDS.register("nuke_near",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "nuke_near")));
    public static final RegistryObject<SoundEvent> NUKE_FAR = SOUNDS.register("nuke_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "nuke_far")));

    /** A radiation counter's click. */
    public static final RegistryObject<SoundEvent> GEIGER = SOUNDS.register("geiger",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "geiger")));

    /** Thick air roaring past the hull inside a giant planet (looped), and the hull groaning deep down. */
    public static final RegistryObject<SoundEvent> GIANT_WIND = SOUNDS.register("giant_wind",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "giant_wind")));
    public static final RegistryObject<SoundEvent> HULL_CREAK = SOUNDS.register("hull_creak",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "hull_creak")));

    public static SoundEvent plasma(String name) {
        var sound = PLASMA.get(name);
        return (sound == null ? PLASMA.get("plasma_solar_wind") : sound).get();
    }

    private ModSounds() {
    }
}
