package com.thepearlking.stevespace.client.vessel;

import com.thepearlking.stevespace.registry.ModSounds;
import com.thepearlking.stevespace.vessel.VesselEntity;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * The roar of a vessel's engines: follows the vessel, swells and fades with the throttle, and
 * is only a muffled rumble through the hull when there's no air to carry it.
 */
public final class VesselEngineSound extends AbstractTickableSoundInstance {

    private final VesselEntity vessel;
    private float level;

    public VesselEngineSound(VesselEntity vessel) {
        super(ModSounds.ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.vessel = vessel;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = vessel.getX();
        this.y = vessel.getY();
        this.z = vessel.getZ();
    }

    public VesselEntity vessel() {
        return vessel;
    }

    @Override
    public void tick() {
        if (vessel.isRemoved()) {
            stop();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean aboard = mc.player != null && mc.player.getVehicle() == vessel;
        // In space you only hear it through the structure, and only from inside.
        boolean vacuum = SpaceDimension.isSpace(vessel.level()) && vessel.velocity().lengthSquared() == 0;
        float target = vessel.throttle() * (vacuum ? (aboard ? 0.3f : 0f) : 1f);
        level += (target - level) * 0.15f;
        volume = Math.max(0.001f, level * 1.4f);
        pitch = 0.85f + 0.25f * vessel.throttle();
        x = vessel.getX();
        y = vessel.getY();
        z = vessel.getZ();
        if (target <= 0 && level < 0.01f) {
            stop();
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
