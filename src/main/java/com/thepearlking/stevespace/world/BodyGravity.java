package com.thepearlking.stevespace.world;

import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Each body's own surface gravity for everything that walks, jumps and falls on it: on the Moon
 * you weigh a sixth as much, jump six times as high and land six times as softly. Out in space
 * (a spacewalk) nothing weighs anything.
 */
public final class BodyGravity {

    private static final ResourceLocation GRAVITY = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "body_gravity");
    private static final ResourceLocation FALL = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "body_fall_damage");

    private BodyGravity() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        CelestialBody body = ServerUniverse.bodyOf(event.getLevel());
        double ratio = 1;
        if (SpaceDimension.isSpace(event.getLevel())) {
            ratio = 0; // weightless out on a spacewalk (and no falls to get hurt by)
        } else if (body != null && body.isLandable()) {
            ratio = body.surfaceGravity() / com.thepearlking.stevespace.universe.SolarSystem.EARTH_GRAVITY;
        }
        apply(living.getAttribute(Attributes.GRAVITY), GRAVITY, ratio);
        // A fall hurts in proportion to the impact energy, which goes with gravity.
        apply(living.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL, ratio);
    }

    /** Sets someone's gravity (and fall damage) to {@code ratio} of Earth's. */
    public static void set(LivingEntity living, double ratio) {
        apply(living.getAttribute(Attributes.GRAVITY), GRAVITY, ratio);
        apply(living.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), FALL, ratio);
    }

    private static void apply(AttributeInstance attribute, ResourceLocation id, double ratio) {
        if (attribute == null) {
            return;
        }
        if (Math.abs(ratio - 1) < 1e-6) {
            attribute.removeModifier(id);
        } else {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, ratio - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
