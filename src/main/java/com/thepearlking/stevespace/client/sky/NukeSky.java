package com.thepearlking.stevespace.client.sky;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.SpaceShaders;
import com.thepearlking.stevespace.client.nuke.NukeClient;
import com.thepearlking.stevespace.nuke.NuclearEffects;
import com.thepearlking.stevespace.radio.Radio;
import com.thepearlking.stevespace.universe.DVec3;

/**
 * A burst above the air, seen in the sky (from the ground below, from orbit, from anywhere with a
 * clear line to it): as Starfish Prime looked from Hawaii, 1,450 km away. A flash that turns
 * night into day for a moment, then a ball of glowing debris swelling across the sky, white,
 * then yellow-green, then a deep red as it cools, and a red glow spreading out from it through
 * the upper air for minutes. (The aurora it sets off along the field lines is drawn with the
 * other auroras.)
 */
public final class NukeSky {

    private NukeSky() {
    }

    public static double lifetime(NukeClient.Event e) {
        return e.regime() == NuclearEffects.Regime.SPACE ? 15 * 60 : 0;
    }

    /** The flash's strength for the player (0..1), from how far it is and whether it can be seen at all. */
    public static double flashFor(NukeClient.Event e, double t) {
        DVec3 eye = eye();
        if (eye == null) {
            return 0;
        }
        DVec3 at = e.absolute();
        if (!Radio.lineOfSight(ClientUniverse.system(), eye, at, ClientUniverse.time())) {
            return 0;
        }
        double km = Math.max(1, at.sub(eye).length());
        return Math.min(1, Math.pow(1800 / km, 1.3)) * Math.min(1, e.kt() / 1000 + 0.2);
    }

    private static DVec3 eye() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        var state = SkyState.compute(mc.gameRenderer.getMainCamera(), mc.level, 1);
        return state == null ? null : state.sky().position();
    }

    /** Drawn with the sky, after the planets. */
    static void render(PoseStack pose, SkyState s) {
        DVec3 eye = s.sky().position();
        for (NukeClient.Event e : NukeClient.events()) {
            if (e.regime() != NuclearEffects.Regime.SPACE) {
                continue;
            }
            DVec3 at = e.absolute();
            if (!Radio.lineOfSight(s.system(), eye, at, s.time())) {
                continue;
            }
            double t = e.age();
            double km = Math.max(1, at.sub(eye).length());
            DVec3 dir = s.sky().toLocal(at.sub(eye)).normalize();
            double scale = Math.cbrt(e.kt() / 1450);
            RenderSystem.setShader(SpaceShaders::skyGlow);
            RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            // The first instant: a point of unbearable light.
            if (t < 3) {
                float a = (float) Math.exp(-t / 0.4);
                SkyRenderer.billboard(pose, dir, (float) Math.atan(400 * scale / km) + 0.02f, 1f, 1f, 0.97f, a);
            }
            // The debris ball, swelling: white, yellow-green, then deep red, fading over minutes.
            double radius = Math.min(1500, 1100 * Math.pow(t, 0.6)) * scale;
            float[] c = shellColour(t);
            float shell = (float) (Math.exp(-t / 150) * 0.9);
            if (shell > 0.01f) {
                // The shell itself, with a soft glow of thinner gas round it.
                float size = (float) Math.min(1.4, Math.atan(radius / km));
                SkyRenderer.billboard(pose, dir, size * 1.35f, c[0], c[1], c[2], shell * 0.18f);
                RenderSystem.setShaderTexture(0, SkyTextures.SHELL);
                SkyRenderer.billboard(pose, dir, size, c[0], c[1], c[2], shell * 0.85f);
                RenderSystem.setShaderTexture(0, SkyTextures.GLOW);
            }
            // The red glow of the upper air round it, spreading and slowly fading.
            double glowKm = Math.min(3000, 600 + 18 * t) * scale;
            float red = (float) (Math.min(1, t / 20) * Math.exp(-t / 420) * 0.35);
            if (red > 0.01f) {
                SkyRenderer.billboard(pose, dir, (float) Math.min(1.5, Math.atan(glowKm / km) * 1.8), 0.9f, 0.16f, 0.14f, red);
            }
        }
        RenderSystem.defaultBlendFunc();
    }

    private static float[] shellColour(double t) {
        double[][] stops = {{0, 1, 1, 0.95}, {4, 1, 0.95, 0.7}, {25, 0.65, 1, 0.55}, {90, 0.95, 0.3, 0.22}, {400, 0.6, 0.08, 0.08}};
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i][0]) {
                double k = (t - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
                return new float[]{(float) (stops[i - 1][1] + (stops[i][1] - stops[i - 1][1]) * k),
                        (float) (stops[i - 1][2] + (stops[i][2] - stops[i - 1][2]) * k),
                        (float) (stops[i - 1][3] + (stops[i][3] - stops[i - 1][3]) * k)};
            }
        }
        return new float[]{0.6f, 0.08f, 0.08f};
    }
}
