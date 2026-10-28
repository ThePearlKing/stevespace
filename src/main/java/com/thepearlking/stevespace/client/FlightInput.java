package com.thepearlking.stevespace.client;

import com.thepearlking.stevespace.block.PilotSeatBlock;
import com.thepearlking.stevespace.block.SeatEntity;
import com.thepearlking.stevespace.network.LeaveSeatC2S;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.network.VesselControlC2S;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Reads the pilot's keys every tick and sends them to the server. The throttle is a setting
 * that stays where you leave it (like a real throttle lever), not a key you hold.
 */
public final class FlightInput {

    /** Throttle change per tick while the up/down key is held (about two seconds for 0 → 100%). */
    private static final float THROTTLE_STEP = 0.025f;

    private static float throttle;
    /** RCS mode: the movement keys push the ship about (W/S/A/D, Shift/Ctrl) instead of steering. */
    private static boolean rcsMode;
    private static net.minecraft.world.entity.Entity lastVehicle;
    private static boolean wasPiloting;

    private FlightInput() {
    }

    /** True while sitting in a pilot seat on the ground or flying a vessel. */
    public static boolean isPiloting() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (player.getVehicle() instanceof VesselEntity vessel) {
            return vessel.pilot() == player; // the pilot's seat flies; the rest ride along (or walk about)
        }
        return player.getVehicle() instanceof SeatEntity seat
                && player.level().getBlockState(seat.blockPosition()).getBlock() instanceof PilotSeatBlock;
    }

    public static float throttle() {
        return throttle;
    }

    public static boolean rcsMode() {
        return rcsMode;
    }

    public static void setThrottle(float value) {
        throttle = value;
    }

    private static double lastWarp = 1;

    /** Says so when the autopilot speeds time up (or brings it back to normal). */
    private static void watchWarp(Minecraft mc, LocalPlayer player) {
        double warp = ClientUniverse.warp();
        if (Math.abs(warp - lastWarp) > 1e-6 && player != null && onAutopilot(player)) {
            // With what it's waiting for: "→ Pluto · 213d 04h" and the like.
            String why = VesselEntity.shipOf(player) instanceof VesselEntity v ? v.autopilotDetail() : "";
            mc.gui.setOverlayMessage(warp > 1
                    ? net.minecraft.network.chat.Component.translatable("hud.stevespace.autopilot.warping", String.format(java.util.Locale.ROOT, "%,d", (long) warp))
                            .append(why.isEmpty() ? "" : " \u00B7 " + why)
                    : net.minecraft.network.chat.Component.translatable("hud.stevespace.autopilot.warp_normal"), false);
        }
        lastWarp = warp;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        watchWarp(mc, player);
        if (player != null && mc.screen == null) {
            while (FlightKeys.WARP_FASTER.consumeClick()) {
                SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.WarpRequestC2S(1));
            }
            while (FlightKeys.WARP_SLOWER.consumeClick()) {
                SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.WarpRequestC2S(-1));
            }
            while (FlightKeys.LEAVE_SEAT.consumeClick()) {
                if (player.isPassenger()) {
                    SpaceNetwork.sendToServer(new LeaveSeatC2S());
                }
            }
            while (FlightKeys.RADIO.consumeClick()) {
                SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.RadioC2S(""));
            }
            while (FlightKeys.MAP.consumeClick()) {
                if (ClientUniverse.isSynced()) {
                    mc.setScreen(new com.thepearlking.stevespace.client.gui.MapScreen());
                }
            }
        }
        if (player != null && mc.screen == null && weightless(player)) {
            spacewalk(mc, player);
        }
        if (player == null || !isPiloting()) {
            throttle = 0;
            wasPiloting = false;
            lastVehicle = player == null ? null : player.getVehicle();
            return;
        }
        if (!wasPiloting && VesselEntity.shipOf(player) instanceof VesselEntity v) {
            // Back in the pilot's seat: the throttle lever is where it was left (if the autopilot is
            // flying, it has the engines; the lever stays at zero until you take over).
            throttle = onAutopilot(player) ? 0 : (float) Math.max(0, Math.min(1, v.commandedThrottle()));
        }
        wasPiloting = true;
        if (player.getVehicle() != lastVehicle) {
            // Vanilla just said "Press Shift to dismount" — here Shift is the throttle. (Not when
            // the same vessel just crossed into or out of space: that's still the same seat.)
            boolean sameFlight = lastVehicle instanceof VesselEntity && player.getVehicle() instanceof VesselEntity;
            lastVehicle = player.getVehicle();
            if (sameFlight) {
                mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
                return;
            }
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("hud.stevespace.seated",
                    FlightKeys.LEAVE_SEAT.getTranslatedKeyMessage()), false);
        }
        if (flightKeys(mc) && onAutopilot(player)) {
            // The autopilot is flying: a first touch of any control only asks; a second takes over.
            if (anyControlPressed(mc)) {
                requestTakeover(mc, player);
            }
            while (FlightKeys.AUTOPILOT.consumeClick()) {
                mc.setScreen(new com.thepearlking.stevespace.client.gui.AutopilotScreen());
            }
            SpaceNetwork.sendToServer(new VesselControlC2S(0, 0, 0, 0));
            return;
        }
        rememberHeld(mc);
        if (flightKeys(mc)) {
            while (FlightKeys.RCS_MODE.consumeClick()) {
                rcsMode = !rcsMode;
                boolean fitted = VesselEntity.shipOf(player) instanceof VesselEntity v ? !v.stats().rcs().isEmpty()
                        : player.getVehicle() instanceof SeatEntity;
                mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(!rcsMode ? "hud.stevespace.rcs.off"
                        : fitted ? "hud.stevespace.rcs.on" : "hud.stevespace.rcs.none"), false);
            }
            // In RCS mode Shift and Ctrl push the ship up and down instead of moving the throttle.
            if (!rcsMode && held(mc, FlightKeys.THROTTLE_UP)) {
                throttle = Math.min(1, throttle + THROTTLE_STEP);
            }
            if (!rcsMode && held(mc, FlightKeys.THROTTLE_DOWN)) {
                throttle = Math.max(0, throttle - THROTTLE_STEP);
            }
            while (FlightKeys.THROTTLE_FULL.consumeClick()) {
                throttle = 1;
            }
            while (FlightKeys.THROTTLE_CUT.consumeClick()) {
                throttle = 0;
            }
            while (FlightKeys.AUTOPILOT.consumeClick()) {
                mc.setScreen(new com.thepearlking.stevespace.client.gui.AutopilotScreen());
            }
        }
        boolean keys = flightKeys(mc);
        float yaw = keys ? axis(held(mc, FlightKeys.YAW_RIGHT), held(mc, FlightKeys.YAW_LEFT)) : 0;
        float arrows = keys ? axis(held(mc, FlightKeys.PITCH_UP), held(mc, FlightKeys.PITCH_DOWN)) : 0;
        if (rcsMode) {
            // The arrow keys still turn the ship; the movement keys push it.
            float forward = keys ? axis(held(mc, mc.options.keyUp), held(mc, mc.options.keyDown)) : 0;
            float right = keys ? axis(held(mc, mc.options.keyRight), held(mc, mc.options.keyLeft)) : 0;
            float up = keys ? axis(held(mc, FlightKeys.THROTTLE_UP), held(mc, FlightKeys.THROTTLE_DOWN)) : 0;
            SpaceNetwork.sendToServer(new VesselControlC2S(throttle, arrows, 0, yaw, forward, right, up));
            return;
        }
        float pitch = keys ? Math.max(-1, Math.min(1, axis(held(mc, mc.options.keyUp), held(mc, mc.options.keyDown)) + arrows)) : 0;
        float roll = keys ? axis(held(mc, mc.options.keyRight), held(mc, mc.options.keyLeft)) : 0;
        SpaceNetwork.sendToServer(new VesselControlC2S(throttle, pitch, roll, yaw));
    }

    /** Flying keys work with nothing open, and over the map (you can fly from it). */
    private static boolean flightKeys(Minecraft mc) {
        return mc.screen == null || mc.screen instanceof com.thepearlking.stevespace.client.gui.MapScreen;
    }

    /** Throttle to zero (with the autopilot flying, this is a control touch like any other). */
    public static void cutThrottle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && onAutopilot(mc.player)) {
            requestTakeover(mc, mc.player);
            return;
        }
        throttle = 0;
    }

    /** Full throttle from the map (likewise asks first while the autopilot flies). */
    public static void fullThrottle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && onAutopilot(mc.player)) {
            requestTakeover(mc, mc.player);
            return;
        }
        throttle = 1;
    }

    // ---------------------------------------------------------------------------------------
    // Taking over from the autopilot: nothing stops it by accident. The pilot touches a control,
    // is told the ship is on autopilot, and only a second touch within two seconds takes over.
    // ---------------------------------------------------------------------------------------

    private static final long TAKEOVER_WINDOW_MS = 2000;
    private static long askedAt = -TAKEOVER_WINDOW_MS;
    private static final java.util.Map<net.minecraft.client.KeyMapping, Boolean> WAS_HELD = new java.util.HashMap<>();

    static boolean onAutopilot(LocalPlayer player) {
        return VesselEntity.shipOf(player) instanceof VesselEntity v && !v.autopilotStatus().isEmpty();
    }

    private static net.minecraft.client.KeyMapping[] controls(Minecraft mc) {
        return new net.minecraft.client.KeyMapping[]{FlightKeys.THROTTLE_UP, FlightKeys.THROTTLE_DOWN, FlightKeys.THROTTLE_FULL,
                FlightKeys.THROTTLE_CUT, FlightKeys.YAW_LEFT, FlightKeys.YAW_RIGHT, FlightKeys.PITCH_UP, FlightKeys.PITCH_DOWN,
                mc.options.keyUp, mc.options.keyDown,
                mc.options.keyLeft, mc.options.keyRight};
    }

    /** True on the tick any flight control goes down (a new press, not one still held). */
    private static boolean anyControlPressed(Minecraft mc) {
        boolean pressed = false;
        for (var key : controls(mc)) {
            boolean now = held(mc, key);
            if (now && !WAS_HELD.getOrDefault(key, false)) {
                pressed = true;
            }
            WAS_HELD.put(key, now);
            while (key.consumeClick()) {
                pressed = true;
            }
        }
        return pressed;
    }

    private static void rememberHeld(Minecraft mc) {
        for (var key : controls(mc)) {
            WAS_HELD.put(key, held(mc, key));
        }
    }

    private static void requestTakeover(Minecraft mc, LocalPlayer player) {
        long now = net.minecraft.Util.getMillis();
        if (now - askedAt <= TAKEOVER_WINDOW_MS) {
            askedAt = -TAKEOVER_WINDOW_MS;
            SpaceNetwork.sendToServer(new com.thepearlking.stevespace.network.AutopilotC2S(
                    com.thepearlking.stevespace.vessel.Autopilot.Program.OFF));
            // Hand over smoothly: the lever starts where the autopilot had the engines.
            if (VesselEntity.shipOf(player) instanceof VesselEntity v) {
                throttle = (float) Math.max(0, Math.min(1, v.throttle()));
            }
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("hud.stevespace.autopilot.taken_over"), false);
        } else {
            askedAt = now;
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("hud.stevespace.autopilot.press_again"), false);
        }
    }

    /**
     * Whether a flight key is held. Read from the keyboard itself: Forge ignores a binding while
     * a modifier is down, and the default throttle keys (Shift, Ctrl) are modifiers.
     */
    private static boolean held(Minecraft mc, net.minecraft.client.KeyMapping key) {
        if (key.isDown()) {
            return true;
        }
        var input = key.getKey();
        return input.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && input.getValue() > 0
                && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), input.getValue());
    }

    /** Velocity at the start of this tick, while floating weightless (null otherwise). */
    private static net.minecraft.world.phys.Vec3 drift;

    /** True zero g: in space, not seated, and no gravity generator pulling. */
    private static boolean weightless(LocalPlayer player) {
        var gravity = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);
        return com.thepearlking.stevespace.world.SpaceDimension.isSpace(player.level()) && !player.isPassenger()
                && !player.getAbilities().flying && !player.isSpectator() && gravity != null && gravity.getValue() < 1e-4;
    }

    @SubscribeEvent
    public static void onPlayerTickStart(TickEvent.PlayerTickEvent.Pre event) {
        // Player ticks also fire on the integrated server's thread (and for other players):
        // only the local player on the client thread may touch this.
        if (event.player != Minecraft.getInstance().player || !event.player.level().isClientSide) {
            return;
        }
        drift = weightless((LocalPlayer) event.player) ? event.player.getDeltaMovement() : null;
    }

    /**
     * No air, no drag: whatever Minecraft slowed down this tick keeps its speed, unless you hit
     * something. The walk keys fire the suit's thrusters along your view instead of walking.
     */
    @SubscribeEvent
    public static void onPlayerTickEnd(TickEvent.PlayerTickEvent.Post event) {
        var start = drift;
        if (start == null || event.player != Minecraft.getInstance().player || !event.player.level().isClientSide
                || !(event.player instanceof LocalPlayer player) || !weightless(player)) {
            return;
        }
        var now = player.getDeltaMovement();
        double x = player.horizontalCollision ? now.x : start.x;
        double y = player.verticalCollision ? now.y : start.y;
        double z = player.horizontalCollision ? now.z : start.z;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            float forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            float strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
            if (forward != 0 || strafe != 0) {
                var look = player.getLookAngle();
                var left = new net.minecraft.world.phys.Vec3(look.z, 0, -look.x).normalize();
                var push = look.scale(forward).add(left.scale(strafe)).normalize().scale(0.006);
                x += push.x;
                y += push.y;
                z += push.z;
            }
        }
        double speed = Math.sqrt(x * x + y * y + z * z);
        if (speed > 0.6) {
            x *= 0.6 / speed;
            y *= 0.6 / speed;
            z *= 0.6 / speed;
        }
        player.setDeltaMovement(x, y, z);
    }

    /** Weightless: jump and sneak push you up and down (suit thrusters), gently. */
    private static void spacewalk(Minecraft mc, LocalPlayer player) {
        double push = 0.012;
        var v = player.getDeltaMovement();
        double up = (mc.options.keyJump.isDown() ? push : 0) - (mc.options.keyShift.isDown() ? push : 0);
        if (up != 0 && Math.abs(v.y + up) < 0.5) {
            player.setDeltaMovement(v.x, v.y + up, v.z);
        }
    }

    private static float axis(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }
}
