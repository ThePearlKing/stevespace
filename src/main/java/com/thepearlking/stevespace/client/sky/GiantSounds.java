package com.thepearlking.stevespace.client.sky;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.space.ClientSpace;
import com.thepearlking.stevespace.registry.ModSounds;
import com.thepearlking.stevespace.universe.GiantAir;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Flying inside a giant planet: the air roaring past the hull (louder the thicker the air and the
 * faster you go), the cabin shaking in the buffeting, and down in the deep the hull groaning.
 */
public final class GiantSounds {

    private static Wind wind;

    private GiantSounds() {
    }

    /** How hard the air is pushing on the vessel now, 0 outside a giant. */
    private static double buffet;
    private static double stress;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        VesselEntity vessel = VesselEntity.shipOf(mc.player);
        var body = ClientUniverse.currentBody();
        if (vessel == null || !ClientSpace.mapModeActive() || !GiantAir.has(body)) {
            buffet = 0;
            stress = 0;
            return;
        }
        double altitude = ClientSpace.mapPosition().y - 63;
        double speed = vessel.velocity().length();
        double density = GiantAir.density(altitude);
        buffet = Math.min(1, Math.log10(1 + density * speed / 30) / 2.2);
        stress = GiantAir.stress(altitude);
        if (wind == null || wind.isStopped()) {
            wind = new Wind();
            mc.getSoundManager().play(wind);
        }
        wind.target = (float) (0.08 + 1.1 * buffet);
        wind.pitchTarget = (float) (0.75 + 0.35 * Math.min(1, speed / 1500));
        // The hull groans more and more often the deeper it goes.
        if (stress > 0 && mc.level.random.nextFloat() < 0.01f + 0.07f * stress) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), ModSounds.HULL_CREAK.get(),
                    SoundSource.NEUTRAL, (float) (0.5 + 0.7 * stress), (float) (1.05 - 0.35 * stress + mc.level.random.nextFloat() * 0.1), false);
        }
    }

    /** The cabin shakes in the buffeting (and creaks shudder it deep down). */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double shake = buffet * buffet * 0.9 + stress * 0.4;
        if (shake < 0.01) {
            return;
        }
        double t = ClientUniverse.time();
        event.setPitch(event.getPitch() + (float) (shake * (Math.sin(t * 23.1) * 0.6 + Math.sin(t * 37.7) * 0.4)));
        event.setRoll(event.getRoll() + (float) (shake * (Math.sin(t * 19.3) * 0.5 + Math.sin(t * 31.9) * 0.5)));
    }

    /** The wind loop, easing toward its level. */
    private static final class Wind extends AbstractTickableSoundInstance {

        float target;
        float pitchTarget = 0.8f;

        Wind() {
            super(ModSounds.GIANT_WIND.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            boolean inside = mc.level != null && ClientSpace.mapModeActive() && GiantAir.has(ClientUniverse.currentBody());
            float goal = inside ? target : 0;
            volume += (goal - volume) * 0.08f;
            pitch += (pitchTarget - pitch) * 0.05f;
            if (!inside && volume < 0.01f) {
                stop();
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
