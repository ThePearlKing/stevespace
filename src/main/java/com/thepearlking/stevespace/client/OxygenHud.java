package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.network.OxygenS2C;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The suit's oxygen gauge, above the hotbar, wherever there is no air. */
public final class OxygenHud {

    private static OxygenS2C last;
    private static long received;

    private OxygenHud() {
    }

    public static void update(OxygenS2C message) {
        last = message;
        received = System.currentTimeMillis();
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (last == null || System.currentTimeMillis() - received > 2000 || mc.options.hideGui
                || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        int x = g.guiWidth() / 2 + 10;
        // Above the hunger bar and the breath bubbles (none of them show in creative).
        int y = mc.player.isCreative() ? g.guiHeight() - 52 : g.guiHeight() - 72;
        if (!last.hazard().isEmpty() && !mc.player.isCreative()) {
            g.drawString(mc.font, Component.translatable(last.hazard()), x, y - 12, 0xFFE07A3A, true);
        } else {
            var body = com.thepearlking.stevespace.client.ClientUniverse.currentBody();
            if (body != null && body.id().equals("mars")
                    && com.thepearlking.stevespace.universe.Weather.marsStorm(com.thepearlking.stevespace.client.ClientUniverse.time()) > 0.2) {
                g.drawString(mc.font, Component.translatable("hud.stevespace.dust_storm"), x, y - 12, 0xFFD9A066, true);
            }
        }
        if (!last.airless()) {
            return;
        }
        int w = 82;
        Component label;
        int color;
        float level;
        if (!last.sealed()) {
            label = Component.translatable("hud.stevespace.oxygen.no_suit");
            color = 0xFFE05A4A;
            level = 0;
        } else {
            label = last.unlimited() ? Component.translatable("hud.stevespace.oxygen.unlimited")
                    : Component.translatable("hud.stevespace.oxygen", Math.round(last.oxygen() * 100));
            level = last.unlimited() ? 1 : last.oxygen();
            color = level > 0.25f ? 0xFF8FC8E8 : 0xFFE6AD45;
        }
        g.fill(x, y + 10, x + w, y + 13, 0xA0101418);
        g.fill(x, y + 10, x + Math.round(w * Math.max(0, Math.min(1, level))), y + 13, color);
        g.drawString(mc.font, label, x, y, color, true);
    }
}
