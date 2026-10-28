package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.block.ScienceBlocks;
import com.thepearlking.stevespace.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The sound a plasma wave receiver plays while it is switched on and you are near it: a blend of
 * loops (the solar wind, a planet's magnetosphere, a world's own) whose loudness follows how much
 * each place has to do with where the receiver is, easing over a couple of seconds, never cutting.
 * <p>
 * It is live, not a tape: each loop is picked up where the world's clock says it is, so switching
 * the receiver off and on again finds it further along, and every receiver hears the same thing at
 * the same moment. The strength wanders slowly too, the way the real signal swells and fades.
 */
public final class PlasmaSound extends AbstractTickableSoundInstance {

    private static final float LOUDNESS = 0.8f;
    /** Change in loudness per tick while easing toward the target (about two seconds end to end). */
    private static final float EASE = 0.025f;

    private final ScienceBlocks.PlasmaReceiverEntity receiver;
    private final String name;
    private float target;
    private float level = 0.001f;
    /** The world clock (seconds) when this was started, and when that was (System.nanoTime). */
    private final double clock;
    private final long started = System.nanoTime();

    private PlasmaSound(ScienceBlocks.PlasmaReceiverEntity receiver, String name) {
        super(ModSounds.plasma(name), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.receiver = receiver;
        this.name = name;
        this.clock = receiver.getLevel() == null ? 0 : receiver.getLevel().getGameTime() / 20.0;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.x = receiver.getBlockPos().getX() + 0.5;
        this.y = receiver.getBlockPos().getY() + 0.5;
        this.z = receiver.getBlockPos().getZ() + 0.5;
    }

    @Override
    public void tick() {
        if (receiver.isRemoved() || !receiver.getBlockState().hasProperty(ScienceBlocks.PlasmaReceiver.LISTENING)
                || !receiver.getBlockState().getValue(ScienceBlocks.PlasmaReceiver.LISTENING)) {
            stop();
            return;
        }
        float wanted = target * LOUDNESS;
        level += Math.max(-EASE, Math.min(EASE, wanted - level));
        double t = now();
        volume = level * swell(t);
        pitch = 1 + 0.012f * (float) wander(t, 37.0, name.hashCode() * 7L);
        if (target <= 0 && level <= 0.002f) {
            stop();
        }
    }

    /** Where the world's clock is now, seconds. */
    private double now() {
        return clock + (System.nanoTime() - started) / 1e9;
    }

    /** The signal's strength right now: slow swells and fades over tens of seconds (about 0.65 to 1.15). */
    private float swell(double t) {
        long seed = name.hashCode();
        double w = 0.6 * wander(t, 23.0, seed) + 0.4 * wander(t, 9.0, seed * 31 + 5);
        return (float) (0.9 + 0.25 * w);
    }

    /** Smooth noise in -1..1 that changes over about this many seconds; the same for everyone at the same time. */
    private static double wander(double t, double period, long seed) {
        double x = t / period;
        long i = (long) Math.floor(x);
        double f = x - i;
        f = f * f * (3 - 2 * f);
        return lerp(hash(i, seed), hash(i + 1, seed), f);
    }

    private static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    private static double hash(long i, long seed) {
        long h = i * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) / (double) (1L << 53)) * 2 - 1;
    }

    private static Field sourceField;

    /** A loop has just started on the sound thread: move it to where the clock says it has got to. */
    @SubscribeEvent
    public static void onPlaySource(PlaySoundSourceEvent event) {
        if (!(event.getSound() instanceof PlasmaSound sound)) {
            return;
        }
        try {
            if (sourceField == null) {
                sourceField = ObfuscationReflectionHelper.findField(com.mojang.blaze3d.audio.Channel.class, "f_83642_");
            }
            int source = sourceField.getInt(event.getChannel());
            int buffer = AL10.alGetSourcei(source, AL10.AL_BUFFER);
            int bytes = AL10.alGetBufferi(buffer, AL10.AL_SIZE);
            int frame = AL10.alGetBufferi(buffer, AL10.AL_CHANNELS) * AL10.alGetBufferi(buffer, AL10.AL_BITS) / 8;
            int rate = AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY);
            if (bytes <= 0 || frame <= 0 || rate <= 0) {
                return;
            }
            double length = bytes / (double) frame / rate;
            AL10.alSourcef(source, AL11.AL_SEC_OFFSET, (float) (sound.now() % length));
            if (com.thepearlking.stevespace.debug.ClientTest.enabled()) {
                com.thepearlking.stevespace.StevesSpace.LOGGER.info("[ClientTest] plasma {} joined at {} s of {} s (now at {})", sound.name,
                        String.format("%.2f", sound.now() % length), String.format("%.2f", length),
                        String.format("%.2f", AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET)));
            }
        } catch (RuntimeException | IllegalAccessException ignored) {
            // can't reach the source: it just starts from the top
        }
    }

    /** The loops one receiver is playing, by sound name. */
    private static final class Layers {
        final Map<String, PlasmaSound> playing = new HashMap<>();
    }

    /** Starts, keeps or re-balances the receiver's sounds. */
    public static void tick(ScienceBlocks.PlasmaReceiverEntity be) {
        Minecraft mc = Minecraft.getInstance();
        boolean on = be.getBlockState().getValue(ScienceBlocks.PlasmaReceiver.LISTENING);
        Layers layers = be.playing instanceof Layers l ? l : null;
        if (!on || mc.player == null || mc.player.distanceToSqr(be.getBlockPos().getCenter()) > 24 * 24) {
            if (layers != null) {
                layers.playing.values().forEach(s -> mc.getSoundManager().stop(s));
                be.playing = null;
            }
            return;
        }
        if (be.getLevel() == null || be.getLevel().getGameTime() % 10 != 0 && layers != null) {
            return;
        }
        if (layers == null) {
            layers = new Layers();
            be.playing = layers;
        }
        var place = Instruments.at(be.getLevel(), be.getBlockPos());
        Map<String, Double> mix = place == null ? Map.of("plasma_solar_wind", 1.0) : Instruments.soundMix(place);
        // Fade out what's no longer part of the blend; drop the ones that have finished.
        for (Iterator<Map.Entry<String, PlasmaSound>> it = layers.playing.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, PlasmaSound> e = it.next();
            if (!mc.getSoundManager().isActive(e.getValue())) {
                it.remove();
                continue;
            }
            e.getValue().target = (float) (double) mix.getOrDefault(e.getKey(), 0.0);
        }
        for (Map.Entry<String, Double> e : mix.entrySet()) {
            if (e.getValue() < 0.01 || layers.playing.containsKey(e.getKey())) {
                continue;
            }
            PlasmaSound sound = new PlasmaSound(be, e.getKey());
            sound.target = (float) (double) e.getValue();
            layers.playing.put(e.getKey(), sound);
            mc.getSoundManager().play(sound);
        }
    }
}
