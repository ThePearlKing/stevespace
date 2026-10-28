package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.registry.ModItems;
import com.thepearlking.stevespace.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/** Held in either hand: clicks with each count and shows the dose rate on the action bar. In an item frame it keeps clicking. */
public final class RadiationCounter {

    private static final double FRAME_RANGE = 16;
    private static int tick;

    private RadiationCounter() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) {
            return;
        }
        var counter = ModItems.RADIATION_COUNTER.get();
        // Counters hung in item frames keep counting where they are.
        for (ItemFrame frame : mc.level.getEntitiesOfClass(ItemFrame.class, mc.player.getBoundingBox().inflate(FRAME_RANGE),
                f -> f.getItem().is(counter))) {
            BlockPos at = frame.blockPosition();
            var place = Instruments.at(mc.level, at);
            if (place == null) {
                continue;
            }
            double dose = Instruments.doseAt(mc.level, at, place) * (mc.level.canSeeSky(at.above()) ? 1 : 0.1);
            for (int i = clicks(dose, mc.player.getRandom()); i > 0; i--) {
                mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.GEIGER.get(), SoundSource.BLOCKS, 0.5f,
                        0.9f + mc.player.getRandom().nextFloat() * 0.2f, mc.player.getRandom(), frame.getX(), frame.getY(), frame.getZ()));
            }
        }
        if (!mc.player.getMainHandItem().is(counter) && !mc.player.getOffhandItem().is(counter)) {
            return;
        }
        var place = Instruments.at(mc.level, mc.player.blockPosition());
        if (place == null) {
            return;
        }
        double dose = Instruments.doseAt(mc.level, mc.player.blockPosition(), place) * (Instruments.shielded() ? 0.1 : 1);
        for (int i = clicks(dose, mc.player.getRandom()); i > 0; i--) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GEIGER.get(), 0.9f + mc.player.getRandom().nextFloat() * 0.2f, 0.5f));
        }
        if (++tick % 10 == 0) {
            String rate = dose >= 1000 ? String.format(Locale.ROOT, "%,.1f mSv/h", dose / 1000) : String.format(Locale.ROOT, "%.2f µSv/h", dose);
            mc.player.displayClientMessage(Component.translatable("stevespace.radiation.reading", rate), true);
        }
    }

    /** Clicks this tick at a dose rate (µSv/h): Poisson, a few dozen a minute on Earth, a roar on Io. */
    private static int clicks(double dose, RandomSource random) {
        double perSecond = Math.min(60, 0.15 + Math.sqrt(dose) * 1.3);
        double expected = perSecond / 20;
        int clicks = 0;
        double p = Math.exp(-expected);
        double r = random.nextDouble();
        double sum = p;
        while (r > sum && clicks < 6) {
            clicks++;
            p *= expected / clicks;
            sum += p;
        }
        return clicks;
    }
}
