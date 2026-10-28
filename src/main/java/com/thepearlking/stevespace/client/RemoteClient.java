package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.network.RemoteFunctionC2S;
import com.thepearlking.stevespace.network.RemoteStateS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;

/**
 * The client side of flying by remote control: the link panel at the top of the screen (channel,
 * craft, signal and light delay, the function keys), number keys 1-5 firing the craft's
 * functions, and the view: a camera following the craft from behind, free to look around (the
 * pilot isn't really sitting in it, so no hand and no cabin).
 */
public final class RemoteClient {

    private static RemoteStateS2C state = RemoteStateS2C.off();

    private RemoteClient() {
    }

    public static void update(RemoteStateS2C message) {
        state = message;
    }

    /** True while flying something by remote control. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return state.active() && mc.player != null && mc.player.isPassenger();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClientTick(TickEvent.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.screen != null) {
            return;
        }
        // Number keys fire functions (taken before the hotbar sees them).
        for (int i = 0; i < 5; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                if (state.functions()[i] > 0) {
                    SpaceNetwork.sendToServer(new RemoteFunctionC2S(i + 1));
                } else {
                    mc.gui.setOverlayMessage(Component.translatable("hud.stevespace.remote.no_function", i + 1), false);
                }
            }
        }
    }

    /** The pilot's eye may be inside the craft's blocks: no "inside a block" screen over the view. */
    @SubscribeEvent
    public static void onBlockOverlay(net.minecraftforge.client.event.RenderBlockScreenEffectEvent event) {
        if (active()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (active()) {
            event.setCanceled(true);
        }
    }

    /** Behind the craft, far enough to see all of it, whichever camera mode is set. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active()) {
            return;
        }
        VesselEntity ship = VesselEntity.shipOf(mc.player);
        double radius = ship != null && ship.stats() != null ? ship.stats().radius() : 4;
        float back = (float) (radius * 2.4 + 6);
        if (!mc.options.getCameraType().isFirstPerson()) {
            back -= 4; // vanilla already pulled it back that far
        }
        try {
            MOVE.invoke(event.getCamera(), -back, 0f, 0f);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Camera.move unavailable", e);
        }
    }

    /** The link panel (with the flight readout below it, from the flight HUD as usual). */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        int w = 240;
        int x = g.guiWidth() / 2 - w / 2;
        int y = 4;
        g.fill(x, y, x + w, y + 34, 0xC00B0F0D);
        g.renderOutline(x, y, w, 34, 0xFF3B4247);
        String head = Component.translatable("hud.stevespace.remote.title", state.channel(), state.name()).getString();
        g.drawString(font, head, x + 6, y + 4, 0xFF66D98F, false);
        String link;
        int linkColor;
        if (!state.signal()) {
            link = Component.translatable("hud.stevespace.remote.no_signal", state.blocker()).getString();
            linkColor = 0xFFE05A4E;
        } else if (state.delay() < 0.05) {
            link = Component.translatable("hud.stevespace.remote.signal").getString();
            linkColor = 0xFFB8C4BC;
        } else {
            link = Component.translatable("hud.stevespace.remote.delay", String.format(java.util.Locale.ROOT, "%.1f", state.delay())).getString();
            linkColor = 0xFFE0A43C;
        }
        g.drawString(font, link, x + 6, y + 14, linkColor, false);
        StringBuilder keys = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (state.functions()[i] > 0) {
                keys.append('[').append(i + 1).append("] ");
            }
        }
        String hint = (keys.length() > 0 ? Component.translatable("hud.stevespace.remote.functions", keys.toString().trim()).getString() + "  " : "")
                + Component.translatable("hud.stevespace.remote.leave", FlightKeys.LEAVE_SEAT.getTranslatedKeyMessage()).getString();
        g.drawString(font, hint, x + 6, y + 24, 0xFF5E6862, false);
    }

    private static final Method MOVE = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90568_",
            float.class, float.class, float.class);
}
