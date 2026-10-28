package com.thepearlking.stevespace.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thepearlking.stevespace.StevesSpace;
import com.thepearlking.stevespace.client.telescope.SkyCatalog;
import com.thepearlking.stevespace.client.telescope.TelescopeScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Locale;

/**
 * Observing through a telescope. Using the telescope opens its target list; once a target is
 * picked the telescope slews to it, zooms in to frame it and keeps following it as the sky turns.
 * Scroll changes the zoom, the inventory key brings the list back, sneak steps away.
 */
public final class TelescopeView {

    private static final ResourceLocation LENS = ResourceLocation.fromNamespaceAndPath(StevesSpace.MODID, "telescope_lens");
    private static final double MIN_ZOOM = 1;
    private static final double MAX_ZOOM = 6000;

    private static BlockPos telescope;
    private static String target;
    /** Extra zoom chosen with the scroll wheel, on top of the framing zoom. */
    private static double zoomFactor = 1;
    private static double shownZoom = 1;
    private static float aimYaw;
    private static float aimPitch;
    private static long lastFrame;
    private static boolean onTarget;
    private static CameraType previousCamera;
    private static boolean lensMade;
    private static boolean sneakWasDown = true;
    private static int observedTicks;
    private static double slewTime;
    /** Zoom chosen by hand when steering the telescope yourself. */
    private static double manualZoom = 1;

    /** The target meaning "no target: steer it yourself". */
    public static final String MANUAL = "manual";

    private TelescopeView() {
    }

    public static boolean active() {
        return telescope != null && target != null;
    }

    /** Steering by hand rather than following a target. */
    public static boolean manual() {
        return active() && MANUAL.equals(target);
    }

    /** How much the view is magnified (1 when not looking through a telescope). */
    public static double magnification() {
        return active() ? shownZoom : 1;
    }

    /** Using the telescope block: show its target list. */
    public static void toggle(BlockPos pos) {
        Minecraft.getInstance().setScreen(new TelescopeScreen(pos.immutable(), target));
    }

    /** Point the telescope at a body and follow it. */
    public static void observe(BlockPos pos, String bodyId) {
        Minecraft mc = Minecraft.getInstance();
        if (!active()) {
            previousCamera = mc.options.getCameraType();
            shownZoom = 1;
            if (mc.player != null) {
                aimYaw = mc.player.getYRot();
                aimPitch = mc.player.getXRot();
            }
        }
        // Presses queued from before (vanilla never clears sneak clicks) must not end it at once.
        while (mc.options.keyShift.consumeClick()) {
            // discarded
        }
        while (FlightKeys.TELESCOPE_TARGETS.consumeClick()) {
            // discarded
        }
        if (MANUAL.equals(bodyId)) {
            manualZoom = Math.max(MIN_ZOOM, shownZoom);
        }
        telescope = pos.immutable();
        target = bodyId;
        sneakWasDown = true;
        observedTicks = 0;
        zoomFactor = 1;
        onTarget = false;
        slewTime = 0;
        lastFrame = 0;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    /** For the automated client test: zoom relative to the framing zoom. */
    public static void setZoom(double factor) {
        if (manual()) {
            manualZoom = factor;
        } else {
            zoomFactor = factor;
        }
    }

    /** For the automated client test: point the telescope (manual mode). */
    public static void setAim(float yaw, float pitch) {
        aimYaw = yaw;
        aimPitch = pitch;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    public static void stop() {
        if (telescope != null && previousCamera != null) {
            Minecraft.getInstance().options.setCameraType(previousCamera);
        }
        telescope = null;
        target = null;
    }

    /** The zoom that frames the target nicely in the lens. */
    private static double framingZoom(SkyCatalog.Entry e) {
        double fov = Minecraft.getInstance().options.fov().get();
        double diameter = Math.toDegrees(e.framingRadius() * 2);
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fov * 0.4 / Math.max(1e-9, diameter)));
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (active()) {
            event.setFOV(event.getFOV() / shownZoom);
        }
    }

    /** Every frame: turn toward the target (smoothly), then hold it in the middle of the view. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null) {
            return;
        }
        long now = System.nanoTime();
        if (manual()) {
            double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
            lastFrame = now;
            // The mouse turned the player since last frame: turn the telescope by that, slowed by the zoom.
            float dyaw = wrap(mc.player.getYRot() - aimYaw);
            float dpitch = mc.player.getXRot() - aimPitch;
            aimYaw = wrap(aimYaw + (float) (dyaw / shownZoom));
            aimPitch = Math.max(-90, Math.min(90, aimPitch + (float) (dpitch / shownZoom)));
            // A ship near the middle: the mount follows it, so a fast one doesn't slip out of view.
            SkyCatalog.Entry ship = shipInView(mc);
            if (ship != null && dt > 0) {
                float yaw = (float) Math.toDegrees(Math.atan2(-ship.direction().x(), ship.direction().z()));
                float pitch = (float) -Math.toDegrees(Math.asin(ship.direction().y()));
                double k = 1 - Math.exp(-dt * 10);
                aimYaw = wrap(aimYaw + (float) (wrap(yaw - aimYaw) * k));
                aimPitch += (pitch - aimPitch) * (float) k;
            }
            double z = dt == 0 ? 1 : 1 - Math.exp(-dt * 6);
            shownZoom = Math.exp(Math.log(shownZoom) + (Math.log(manualZoom) - Math.log(shownZoom)) * z);
            event.setYaw(aimYaw);
            event.setPitch(aimPitch);
            mc.player.setYRot(aimYaw);
            mc.player.setXRot(aimPitch);
            mc.player.yRotO = aimYaw;
            mc.player.xRotO = aimPitch;
            return;
        }
        SkyCatalog.Entry e = SkyCatalog.of(mc.player.getEyePosition(), target);
        if (e == null) {
            return;
        }
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        float yaw = (float) Math.toDegrees(Math.atan2(-e.direction().x(), e.direction().z()));
        float pitch = (float) -Math.toDegrees(Math.asin(e.direction().y()));
        // Slew: quick at first, settling gently (a mount's motors, not a snap).
        double k = 1 - Math.exp(-dt * 3.5);
        float dyaw = wrap(yaw - aimYaw);
        aimYaw = wrap(aimYaw + (float) (dyaw * k));
        aimPitch += (pitch - aimPitch) * (float) k;
        double error = Math.max(Math.abs(wrap(yaw - aimYaw)), Math.abs(pitch - aimPitch));
        slewTime += dt;
        // Close enough, or a fast mover (a ship in low orbit) the mount keeps lagging behind: lock on.
        if (error < 0.5 || dt == 0 && error < 1e-3 || slewTime > 1.5 && error < 10) {
            onTarget = true;
        }
        if (onTarget) {
            aimYaw = yaw;
            aimPitch = pitch;
        }
        // Zoom in once it's pointing there; zoom changes are eased in log space.
        double wanted = onTarget ? Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, framingZoom(e) * zoomFactor)) : 1;
        double z = 1 - Math.exp(-dt * 2.5);
        shownZoom = Math.exp(Math.log(shownZoom) + (Math.log(wanted) - Math.log(shownZoom)) * z);
        if (dt == 0) {
            shownZoom = Math.max(MIN_ZOOM, shownZoom);
        }
        event.setYaw(aimYaw);
        event.setPitch(aimPitch);
        mc.player.setYRot(aimYaw);
        mc.player.setXRot(aimPitch);
        mc.player.yRotO = aimYaw;
        mc.player.xRotO = aimPitch;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360;
        if (d > 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }

    /** Standing at the eyepiece: no walking off while observing. */
    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (active()) {
            var input = event.getInput();
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (active() && Minecraft.getInstance().screen == null) {
            if (manual()) {
                manualZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, manualZoom * Math.pow(1.3, event.getDeltaY())));
            } else {
                zoomFactor = Math.max(0.05, Math.min(20, zoomFactor * Math.pow(1.3, event.getDeltaY())));
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active()) {
            return;
        }
        if (mc.player == null || mc.player.distanceToSqr(telescope.getCenter()) > 9
                || !(mc.player.level().getBlockState(telescope).getBlock() instanceof com.thepearlking.stevespace.block.TelescopeBlock)) {
            stop();
            return;
        }
        // Leave on a new press of sneak (its click counter holds presses from long before).
        while (mc.options.keyShift.consumeClick()) {
            // not used
        }
        boolean sneak = mc.options.keyShift.isDown() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                mc.getWindow().getWindow(), mc.options.keyShift.getKey().getValue());
        boolean pressed = sneak && !sneakWasDown && observedTicks > 3;
        sneakWasDown = sneak;
        observedTicks++;
        if (pressed) {
            stop();
            return;
        }
        while (FlightKeys.TELESCOPE_TARGETS.consumeClick()) {
            mc.setScreen(new TelescopeScreen(telescope, target));
        }
        SkyCatalog.Entry e = manual() ? null : SkyCatalog.of(mc.player.getEyePosition(), target);
        if (e != null && !e.aboveHorizon() && (e.blockedBy() != null || e.elevation() < -1)) {
            mc.gui.setOverlayMessage(Component.translatable("hud.stevespace.telescope.below", e.name()), false);
            stop();
        }
    }

    /** The ship nearest the middle of the lens, if one is in it. */
    private static SkyCatalog.Entry shipInView(Minecraft mc) {
        var sky = com.thepearlking.stevespace.client.ClientUniverse.telescopeSky(mc.player.getEyePosition());
        if (sky == null) {
            return null;
        }
        double yaw = Math.toRadians(aimYaw);
        double pitch = Math.toRadians(aimPitch);
        com.thepearlking.stevespace.universe.DVec3 aim = new com.thepearlking.stevespace.universe.DVec3(
                -Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        double reach = Math.toRadians(mc.options.fov().get() / shownZoom) * 0.35;
        SkyCatalog.Entry best = null;
        double bestAngle = reach;
        for (var ship : com.thepearlking.stevespace.client.telescope.TelescopeShips.ships()) {
            SkyCatalog.Entry e = SkyCatalog.ship(sky, ship);
            if (e == null || e.distance() < ship.radius() * 3) {
                continue;
            }
            double angle = Math.acos(Math.max(-1, Math.min(1, e.direction().dot(aim))));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = e;
            }
        }
        return best;
    }

    /** The lens mask: clear in the middle, a soft dark edge, black outside. */
    private static void makeLens() {
        int size = 512;
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / size * 2 - 1;
                double dy = (y + 0.5) / size * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double edge = smoothstep(0.93, 0.995, r);
                double vignette = 0.35 * smoothstep(0.55, 0.95, r);
                int alpha = (int) Math.round(255 * Math.min(1, edge + vignette * (1 - edge)));
                image.setPixelRGBA(x, y, alpha << 24); // ABGR: black with this alpha
            }
        }
        Minecraft.getInstance().getTextureManager().register(LENS, new DynamicTexture(image));
        lensMade = true;
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** A HUD layer: the lens, and what you're looking at. */
    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui || mc.player == null) {
            return;
        }
        if (!lensMade) {
            makeLens();
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int size = Math.min(w, h) - 8;
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        g.fill(0, 0, w, y, 0xFF000000);
        g.fill(0, y + size, w, h, 0xFF000000);
        g.fill(0, y, x, y + size, 0xFF000000);
        g.fill(x + size, y, w, y + size, 0xFF000000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(LENS, x, y, 0, 0, size, size, size, size);
        RenderSystem.disableBlend();

        SkyCatalog.Entry e = SkyCatalog.of(mc.player.getEyePosition(), target);
        int text = 0xFFD7E3DA;
        int label = 0xFF93A59A;
        int green = 0xFF66D98F;
        int tx = 10;
        int ty = 10;
        if (manual()) {
            g.drawString(mc.font, Component.translatable("hud.stevespace.telescope.manual"), tx, ty, green, false);
            ty += 12;
            double azimuth = ((aimYaw + 180) % 360 + 360) % 360;
            g.drawString(mc.font, String.format(Locale.ROOT, "%.1f° %s  (%.1f°)", -aimPitch, SkyCatalog.compass(azimuth), azimuth), tx, ty, label, false);
            ty += 12;
            // Whatever ship is near the middle of the view: named if it has a radio.
            SkyCatalog.Entry ship = shipInView(mc);
            if (ship != null) {
                String name = ship.ship().radio() ? ship.name()
                        : Component.translatable("hud.stevespace.telescope.unknown_craft").getString();
                g.drawString(mc.font, name, tx, ty, text, false);
                ty += 10;
                g.drawString(mc.font, SkyCatalog.distance(ship.distance()) + "  " + SkyCatalog.size(ship.angularRadius()), tx, ty, label, false);
            }
        } else if (e != null) {
            g.drawString(mc.font, e.name(), tx, ty, green, false);
            ty += 12;
            g.drawString(mc.font, SkyCatalog.distance(e.distance()), tx, ty, text, false);
            ty += 10;
            g.drawString(mc.font, SkyCatalog.size(e.angularRadius()) + "  " + Math.round(e.phase() * 100) + "%", tx, ty, text, false);
            ty += 10;
            g.drawString(mc.font, String.format(Locale.ROOT, "%.0f° %s", Math.max(0, e.elevation()), SkyCatalog.compass(e.azimuth())), tx, ty, label, false);
        }
        String zoomText = String.format(Locale.ROOT, "×%,.0f", shownZoom);
        g.drawString(mc.font, zoomText, w - 10 - mc.font.width(zoomText), 10, text, false);
        Component keys = Component.translatable("hud.stevespace.telescope.keys",
                FlightKeys.TELESCOPE_TARGETS.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage());
        g.drawString(mc.font, keys, w - 10 - mc.font.width(keys), h - 18, label, false);
    }
}
