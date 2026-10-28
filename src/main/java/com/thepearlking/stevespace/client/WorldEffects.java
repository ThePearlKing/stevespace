package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.Weather;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * What a world's air does to you on foot (applied to the local player, whose movement the client
 * owns): Mars' storm winds push you east; Titan's thick air lets you fall slowly.
 */
public final class WorldEffects {

    private WorldEffects() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.player != mc.player) {
            return;
        }
        boolean moving = !mc.player.isPassenger() && !mc.player.getAbilities().flying && !mc.player.isSpectator();
        CelestialBody body = ClientUniverse.currentBody();
        if (body == null || com.thepearlking.stevespace.world.SpaceDimension.isSpace(mc.player.level())) {
            return;
        }
        var v = mc.player.getDeltaMovement();
        switch (body.id()) {
            case "mars" -> {
                double wind = Weather.marsWind(ClientUniverse.time());
                if (moving && wind > 0.5 && !mc.player.isInWater()) {
                    mc.player.setDeltaMovement(v.x + wind * 0.0025, v.y, v.z);
                }
                // Dust blowing past.
                var random = mc.player.getRandom();
                var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.78f, 0.56f, 0.38f), 0.45f);
                for (int i = 0; i < wind * 2; i++) {
                    double x = mc.player.getX() - 12 + random.nextDouble() * 6;
                    double y = mc.player.getY() - 1 + random.nextDouble() * 5;
                    double z = mc.player.getZ() + (random.nextDouble() - 0.5) * 24;
                    mc.level.addParticle(dust, x, y, z, wind * 0.4, 0, 0); // dust slows its start by ten
                }
            }
            case "titan" -> {
                // Four times Earth's air pressure and a seventh of its gravity: you drift down.
                if (moving && v.y < -0.05 && !mc.player.isInLiquid()) {
                    mc.player.setDeltaMovement(v.x * 0.98, v.y * 0.9, v.z * 0.98);
                    mc.player.resetFallDistance();
                }
            }
            default -> {
            }
        }
    }
}
