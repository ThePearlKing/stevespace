package com.thepearlking.stevespace.client.nuke;

import com.thepearlking.stevespace.nuke.NuclearEffects;
import com.thepearlking.stevespace.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A blast's sound reaches you when its pressure wave does: a while after the flash (sound is slow,
 * and scaled with the sizes), quieter and deeper the further away, and never through vacuum or
 * from high above the air. Close in: the shock's crack and the roar after it. Far off: rolling
 * thunder.
 */
final class NukeSound {

    private static final List<NukeClient.Event> WAITING = new ArrayList<>();

    private NukeSound() {
    }

    static void schedule(NukeClient.Event e) {
        boolean heard = switch (e.regime()) {
            case SURFACE, AIR -> true;
            case HIGH -> e.msg().heightKm() < 60; // a faint far-off rumble from the lower ones
            default -> false;                       // no air to carry it
        };
        if (heard) {
            WAITING.add(e);
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (WAITING.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        String dim = mc.level.dimension().location().toString();
        WAITING.removeIf(e -> {
            if (!e.msg().dimension().equals(dim)) {
                return e.age() > 600; // somewhere else: forget it after a while
            }
            double d = new Vec3(e.x(), e.y(), e.z()).distanceTo(eye);
            double r5 = NuclearEffects.blocks(NuclearEffects.blastKm(e.kt(), 5));
            double r1 = NuclearEffects.blocks(NuclearEffects.blastKm(e.kt(), 1));
            if (NukeRenderer.shockFront(e.age(), r5) < d) {
                return false; // not here yet
            }
            boolean near = d < r1 * 1.5 && e.regime() != NuclearEffects.Regime.HIGH;
            float volume = (float) Math.max(0.12, Math.min(1, r1 * 4 / d));
            if (e.regime() == NuclearEffects.Regime.HIGH) {
                volume *= 0.35f;
            }
            // Bigger blasts are deeper: their pressure wave is longer.
            float pitch = (float) Math.max(0.6, Math.min(1.15, 1.1 - 0.12 * Math.log10(e.kt() / 15)));
            if (com.thepearlking.stevespace.debug.ClientTest.enabled()) {
                com.thepearlking.stevespace.StevesSpace.LOGGER.info("[ClientTest] nuke sound {} at {} blocks after {} s, volume {} pitch {}",
                        near ? "near" : "far", (int) d, String.format("%.1f", e.age()), volume, pitch);
            }
            mc.getSoundManager().play(new SimpleSoundInstance((near ? ModSounds.NUKE_NEAR : ModSounds.NUKE_FAR).get().getLocation(),
                    SoundSource.WEATHER, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                    SoundInstance.Attenuation.NONE, 0, 0, 0, true));
            return true;
        });
    }
}
