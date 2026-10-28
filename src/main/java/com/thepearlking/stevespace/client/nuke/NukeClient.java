package com.thepearlking.stevespace.client.nuke;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.cloud.PuffRenderer;
import com.thepearlking.stevespace.network.NukeS2C;
import com.thepearlking.stevespace.nuke.NuclearAftermath;
import com.thepearlking.stevespace.nuke.NuclearEffects;
import com.thepearlking.stevespace.radio.Radio;
import com.thepearlking.stevespace.universe.DVec3;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's side of nuclear detonations: keeps the ones still visible, draws their clouds into
 * the world each frame, lights the screen with their flash (where you can see it from), and
 * starts their sound when it has had time to travel to you.
 */
public final class NukeClient {

    /** One detonation as this player knows it: the message, when it arrived (System.nanoTime), its shape seed. */
    public record Event(NukeS2C msg, long start, long seed) {

        public NuclearEffects.Regime regime() {
            return NuclearEffects.Regime.values()[msg.regime()];
        }

        public double kt() {
            return msg.kt();
        }

        public double x() {
            return msg.x();
        }

        public double y() {
            return msg.y();
        }

        public double z() {
            return msg.z();
        }

        public double groundY() {
            return msg.groundY();
        }

        public double age() {
            return (System.nanoTime() - start) / 1e9;
        }

        /**
         * Where it went off, in the solar system now: the debris goes on round the Sun with its
         * world, so it's kept at the same place relative to that world.
         */
        public DVec3 absolute() {
            DVec3 then = new DVec3(msg.absolute()[0], msg.absolute()[1], msg.absolute()[2]);
            var system = ClientUniverse.system();
            var body = system == null || msg.body().isEmpty() ? null : system.body(msg.body());
            if (body == null) {
                return then;
            }
            return then.sub(system.absolutePosition(body, msg.time())).add(system.absolutePosition(body, ClientUniverse.time()));
        }
    }

    static final List<Event> EVENTS = new ArrayList<>();

    /** Every detonation still showing, anywhere. */
    public static List<Event> events() {
        return EVENTS;
    }

    private NukeClient() {
    }

    public static void detonation(NukeS2C msg) {
        long seed = Double.doubleToLongBits(msg.x() * 31 + msg.z() * 17 + msg.time());
        Event e = new Event(msg, System.nanoTime(), seed);
        EVENTS.add(e);
        NuclearAftermath.CLIENT.add(new NuclearAftermath.Event(msg.dimension(), msg.x(), msg.y(), msg.z(), msg.kt(), msg.regime(),
                msg.body(), shellFor(msg), msg.time()));
        NukeSound.schedule(e);
    }

    private static double shellFor(NukeS2C msg) {
        var system = ClientUniverse.system();
        var body = system == null || msg.body().isEmpty() ? null : system.body(msg.body());
        if (body == null || msg.regime() != NuclearEffects.Regime.SPACE.ordinal()) {
            return 0;
        }
        DVec3 rel = new DVec3(msg.absolute()[0], msg.absolute()[1], msg.absolute()[2]).sub(system.absolutePosition(body, msg.time()));
        return NuclearAftermath.shellOf(body, rel);
    }

    /** Detonations in the world the player is in, still showing. */
    static List<Event> here() {
        Minecraft mc = Minecraft.getInstance();
        List<Event> list = new ArrayList<>();
        if (mc.level == null) {
            return list;
        }
        String dim = mc.level.dimension().location().toString();
        for (Event e : EVENTS) {
            if (e.msg().dimension().equals(dim)) {
                list.add(e);
            }
        }
        return list;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        EVENTS.removeIf(e -> e.age() > Math.max(NukeRenderer.lifetime(e), com.thepearlking.stevespace.client.sky.NukeSky.lifetime(e)));
        NukeSound.tick();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<Event> here = here();
        if (here.isEmpty() || mc.level == null) {
            return;
        }
        boolean humid = mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD; // Earth's moist air
        List<PuffRenderer.Puff> puffs = new ArrayList<>();
        long seed = mc.level.dimension().location().toString().hashCode() & 0xffffffL;
        for (Event e : here) {
            int from = puffs.size();
            NukeRenderer.puffs(e, e.age(), humid, puffs);
            drift(e, puffs, from, seed);
        }
        // The camera's turn, as the game itself builds it for the world.
        org.joml.Matrix4f view = new org.joml.Matrix4f().rotation(event.getCamera().rotation().conjugate(new org.joml.Quaternionf()));
        PuffRenderer.draw(puffs, view, event.getCamera(), light(event.getPartialTick()), (float) ((net.minecraft.Util.getMillis() / 1000.0) % 10000));
    }

    /**
     * Once it has risen, the cloud is the wind's: each part carried along at its own height (the
     * wind veers and strengthens with height), so the stem leans and the cap slides off downwind.
     */
    private static void drift(Event e, List<PuffRenderer.Puff> puffs, int from, long seed) {
        double t = e.age();
        double settle = NuclearEffects.glowSeconds(e.kt()) * 2;
        if (t <= settle) {
            return;
        }
        double carried = t - settle;
        double ground = Double.isNaN(e.groundY()) ? e.y() : e.groundY();
        double perKm = NuclearEffects.blocks(1);
        for (int i = from; i < puffs.size(); i++) {
            PuffRenderer.Puff p = puffs.get(i);
            double km = Math.max(0, (p.y() - ground) / perKm);
            double[] w = com.thepearlking.stevespace.client.cloud.Winds.at(km, seed);
            // m/s to blocks per second at the burst's scale.
            double dx = w[0] / 1000 * perKm * carried;
            double dz = w[1] / 1000 * perKm * carried;
            puffs.set(i, new PuffRenderer.Puff(p.x() + dx, p.y(), p.z() + dz, p.radius(), p.r(), p.g(), p.b(), p.a(),
                    p.glowR(), p.glowG(), p.glowB(), p.seed()));
        }
    }

    /** The sky's light on the clouds: the Sun (warm and weak when low, gone at night), the sky's blue fill, the haze. */
    public static PuffRenderer.Light light(float partial) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f sun = new Vector3f(0.3f, 0.8f, 0.2f).normalize();
        try {
            var state = com.thepearlking.stevespace.client.sky.SkyState.compute(mc.gameRenderer.getMainCamera(), mc.level, partial);
            if (state != null) {
                sun = new Vector3f((float) state.sunDir().x(), (float) state.sunDir().y(), (float) state.sunDir().z());
            }
        } catch (RuntimeException ignored) {
            // no sky yet: a default high sun
        }
        float up = sun.y;
        float day = (float) Math.max(0, Math.min(1, (up + 0.08) / 0.25));
        float warm = (float) Math.max(0, Math.min(1, 1 - up / 0.35));
        Vector3f sunColour = new Vector3f(1f, 0.95f - 0.35f * warm, 0.88f - 0.6f * warm).mul(day * 0.75f);
        Vector3f ambient = new Vector3f(0.52f, 0.57f, 0.66f).mul(0.06f + 0.6f * day);
        float[] fog = com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor();
        Vector3f haze = new Vector3f(fog[0], fog[1], fog[2]);
        return new PuffRenderer.Light(sun, sunColour, ambient, haze, 2600f);
    }

    // ---------------------------------------------------------------------------------------
    // The flash
    // ---------------------------------------------------------------------------------------

    /** How bright the flash is for this player right now (0..1). */
    static float flash() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0;
        }
        float total = 0;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        for (Event e : EVENTS) {
            double t = e.age();
            if (t > 60) {
                continue;
            }
            double glow = NuclearEffects.glowSeconds(e.kt());
            double pulse = Math.exp(-t / 0.12) + 0.55 * Math.exp(-t / (glow * 0.6));
            if (e.regime() == NuclearEffects.Regime.SPACE) {
                total += (float) (com.thepearlking.stevespace.client.sky.NukeSky.flashFor(e, t) * pulse);
                continue;
            }
            if (!e.msg().dimension().equals(mc.level.dimension().location().toString())) {
                continue;
            }
            Vec3 at = new Vec3(e.x(), e.y(), e.z());
            double d = Math.max(1, at.distanceTo(eye));
            double burn = NuclearEffects.blocks(NuclearEffects.burnKm(e.kt()));
            double k = Math.min(1, 2.2 * burn / d);
            var hit = mc.level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            boolean seen = hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(at) < 2;
            total += (float) (k * pulse * (seen ? 1 : 0.3));
        }
        return Math.min(0.97f, total);
    }

    /** The flash, over everything (a HUD layer). */
    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        float f = flash();
        if (f < 0.004f) {
            return;
        }
        int a = (int) (f * 255);
        // White at its height, warming as it fades.
        int r = 255;
        int gg = (int) (255 - 30 * (1 - f));
        int b = (int) (250 - 90 * (1 - f));
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | (r << 16) | (gg << 8) | b);
    }

    /** True if nothing (no planet or moon) stands between two points in the solar system. */
    static boolean clear(DVec3 a, DVec3 b) {
        return Radio.lineOfSight(ClientUniverse.system(), a, b, ClientUniverse.time());
    }
}
