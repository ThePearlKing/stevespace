package com.thepearlking.stevespace.client.nuke;

import com.thepearlking.stevespace.client.cloud.PuffRenderer.Puff;
import com.thepearlking.stevespace.nuke.NuclearEffects;

import java.util.List;
import java.util.Random;

/**
 * What a detonation looks like at a given moment, as puffs: worked out from the time since it
 * went off (nothing simulated, so every frame is exact and any number can be watched at once).
 *
 * <p>In air, near the ground: the fireball blooms in a fraction of a second, white-hot, cooling
 * through yellow and orange to a dull red as it climbs; on Earth a white condensation shell
 * flashes round it as the shock passes; a skirt of dust races out along the ground with the
 * shock front; the fireball turns inside out into a rolling vortex ring, glowing inside at first,
 * climbing for minutes toward the height its yield reaches; a stem of dust and debris is drawn
 * up beneath it; at the top it spreads flat and slowly drifts apart.
 *
 * <p>With no air (the Moon): a flash, a ball of plasma that is gone in a moment, and a dome of
 * debris thrown out on long ballistic arcs, no billowing at all. High in the air: a vast, short
 * lived glowing ball, nothing on the ground.
 */
public final class NukeRenderer {

    private NukeRenderer() {
    }

    /** All the puffs of one detonation, {@code t} seconds after it went off. {@code humid}: Earth's air. */
    public static void puffs(NukeClient.Event e, double t, boolean humid, List<Puff> out) {
        switch (e.regime()) {
            case SURFACE, AIR -> ground(e, t, humid, out);
            case VACUUM -> vacuum(e, t, out);
            case HIGH -> high(e, t, out);
            default -> {
            }
        }
    }

    /** How long a detonation stays visible, seconds. */
    public static double lifetime(NukeClient.Event e) {
        return switch (e.regime()) {
            case SURFACE, AIR -> NuclearEffects.riseSeconds(e.kt()) * 6 + 20 * 60;
            case VACUUM -> 120;
            case HIGH -> 180;
            default -> 0;
        };
    }

    // ---------------------------------------------------------------------------------------
    // Colour of hot gas
    // ---------------------------------------------------------------------------------------

    /** The fireball's glow colour and strength as it cools, {@code f} = time / glow time. */
    static float[] heat(double f) {
        // White-hot, then yellow, orange, a dull red, then nothing.
        double[][] stops = {{0, 1, 1, 0.97}, {0.15, 1, 0.92, 0.7}, {0.45, 1, 0.62, 0.25}, {1.0, 0.85, 0.32, 0.1},
                {2.5, 0.45, 0.12, 0.05}, {5, 0, 0, 0}};
        for (int i = 1; i < stops.length; i++) {
            if (f <= stops[i][0]) {
                double k = (f - stops[i - 1][0]) / (stops[i][0] - stops[i - 1][0]);
                return new float[]{(float) lerp(stops[i - 1][1], stops[i][1], k), (float) lerp(stops[i - 1][2], stops[i][2], k),
                        (float) lerp(stops[i - 1][3], stops[i][3], k)};
            }
        }
        return new float[]{0, 0, 0};
    }

    private static double lerp(double a, double b, double k) {
        return a + (b - a) * Math.max(0, Math.min(1, k));
    }

    private static double smooth(double a, double b, double x) {
        double k = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return k * k * (3 - 2 * k);
    }

    // ---------------------------------------------------------------------------------------
    // In air near the ground: fireball, shock, mushroom cloud
    // ---------------------------------------------------------------------------------------

    private static void ground(NukeClient.Event e, double t, boolean humid, List<Puff> out) {
        double kt = e.kt();
        double fireball = NuclearEffects.blocks(NuclearEffects.fireballKm(kt));
        double top = NuclearEffects.blocks(NuclearEffects.cloudTopKm(kt));
        double cap = NuclearEffects.blocks(NuclearEffects.cloudCapKm(kt));
        double glow = NuclearEffects.glowSeconds(kt);
        double tau = NuclearEffects.riseSeconds(kt);
        double ground = Double.isNaN(e.groundY()) ? e.y() - fireball : e.groundY();
        double burst = Math.max(e.y(), ground + fireball * (e.regime() == com.thepearlking.stevespace.nuke.NuclearEffects.Regime.SURFACE ? 0.7 : 1));
        double life = lifetime(e);
        double fade = 1 - smooth(life * 0.6, life, t); // the whole thing thins out at the end
        // Each part keeps its own random numbers, so one finishing never reshuffles the others.
        Random rnd;

        // Where the cloud's centre (the vortex ring) is: climbing fast at first, then levelling off.
        double ringTop = ground + top * 0.8;
        double rise = 1 - Math.exp(-Math.max(0, t - glow * 0.3) / tau);
        double centreY = burst + (ringTop - burst) * rise;
        // The ring grows from the fireball's size to the cap's, and keeps spreading at the top.
        double grow = 1 - Math.exp(-t / (tau * 1.2));
        double spread = 1 + 0.35 * smooth(tau * 4, tau * 10, t);
        double ringRadius = (fireball * 0.6 + (cap * 0.32 - fireball * 0.6) * grow) * spread;
        double tube = Math.max(fireball * 0.55, ringRadius * 0.55) / Math.sqrt(spread);
        float[] h = heat(t / glow);
        float glowStrength = (float) (Math.exp(-t / (glow * 1.3)) * 1.2 + 0.12 * Math.exp(-t / (glow * 8)));

        rnd = new Random(e.seed() * 31L + 1);
        // 1. The fireball itself, while it still burns: a churning ball of puffs.
        double fbLife = glow * 3;
        if (t < fbLife) {
            double bloom = Math.sqrt(1 - Math.exp(-t / (0.02 * glow + 0.015)));
            double r = fireball * bloom * (1 + 0.15 * t / glow);
            double fbY = burst + (centreY - burst) * 0.7;
            float alpha = (float) (1 - smooth(fbLife * 0.5, fbLife, t));
            int n = 46;
            for (int i = 0; i < n; i++) {
                double[] d = sphere(rnd);
                double depth = Math.cbrt(rnd.nextDouble()) * 0.62;
                double churn = Math.sin(t * 1.7 + i) * 0.06;
                double px = e.x() + d[0] * r * (depth + churn);
                double py = fbY + d[1] * r * (depth + churn) * 0.9;
                double pz = e.z() + d[2] * r * (depth + churn);
                // Mottled: some cells hotter than others, flickering as the ball churns.
                float g = glowStrength * (float) ((1.25 - depth * 0.5) * (0.7 + 0.3 * Math.sin(t * 3.1 + i * 1.7)));
                out.add(new Puff(px, py, pz, (float) (r * (0.5 + 0.25 * rnd.nextDouble())), 0.42f, 0.24f, 0.16f, alpha,
                        Math.min(1, h[0] * g), Math.min(1, h[1] * g), Math.min(1, h[2] * g), rnd.nextInt()));
            }
        }

        rnd = new Random(e.seed() * 31L + 2);
        // 2. The condensation shell (humid air only): a white dome that flashes up as the shock
        //    passes and is gone in a second or two.
        if (humid && t > 0.1 && t < 4 + glow) {
            double shellR = fireball * (1.6 + 2.2 * Math.min(1, t / (1.5 + glow * 0.3)));
            float alpha = (float) (smooth(0.1, 0.4, t) * (1 - smooth(0.6 + glow * 0.06, 1.5 + glow * 0.15, t)) * 0.07);
            for (int i = 0; i < 90; i++) {
                double[] d = sphere(rnd);
                if (d[1] < -0.25) {
                    continue; // a dome: the ground takes the bottom
                }
                out.add(new Puff(e.x() + d[0] * shellR, burst + d[1] * shellR * 0.85, e.z() + d[2] * shellR,
                        (float) (shellR * 0.34), 0.95f, 0.96f, 0.98f, alpha, 0, 0, 0, rnd.nextInt()));
            }
        }

        rnd = new Random(e.seed() * 31L + 3);
        // 3. The dust skirt racing out with the shock along the ground, then settling.
        double r5 = NuclearEffects.blocks(NuclearEffects.blastKm(kt, 5));
        double shock = shockFront(t, r5);
        if (t > 0.3 && burst - ground < fireball * 4) {
            float alpha = (float) (smooth(0.3, 2, t) * (1 - smooth(40, 150, t)) * 0.7 * fade);
            double reach = Math.min(shock, r5 * 1.2);
            for (int i = 0; i < 160; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double along = 0.55 + 0.45 * rnd.nextDouble();
                double rr = reach * along;
                double height = 2 + rr * 0.09 * (0.5 + rnd.nextDouble()) + Math.min(t, 60) * 0.02 * fireball;
                out.add(new Puff(e.x() + Math.cos(a) * rr, ground + height, e.z() + Math.sin(a) * rr,
                        (float) (Math.max(2.5, rr * 0.1) * (0.7 + 0.6 * rnd.nextDouble())), 0.66f, 0.58f, 0.48f, alpha, 0, 0, 0,
                        rnd.nextInt()));
            }
        }

        rnd = new Random(e.seed() * 31L + 4);
        // 4. The mushroom cap: a vortex ring rolling up and out, glowing inside at first.
        if (t > glow * 0.5) {
            float alpha = (float) (smooth(glow * 0.5, glow * 2, t) * fade);
            double roll = t * 0.55 / (1 + t / (tau * 2));
            int n = 460;
            for (int i = 0; i < n; i++) {
                double phi = (i + rnd.nextDouble() * 0.8) / n * Math.PI * 2;
                double psi = rnd.nextDouble() * Math.PI * 2 + roll * (0.7 + 0.6 * rnd.nextDouble());
                double tw = tube * (0.55 + 0.45 * Math.sqrt(rnd.nextDouble()));
                double rr = ringRadius + Math.cos(psi) * tw;
                double py = centreY + Math.sin(psi) * tw * 0.75;
                // Sunlit grey-white on top, dusty brown underneath and in the middle.
                double top01 = 0.5 + 0.5 * Math.sin(psi);
                // Reddish brown at first (nitrogen dioxide), whitening as water condenses in it.
                double white = smooth(glow * 2, glow * 2 + 50, t);
                float cr = (float) lerp(lerp(0.34, 0.48, top01), lerp(0.48, 0.9, top01), white);
                float cg = (float) lerp(lerp(0.24, 0.33, top01), lerp(0.42, 0.87, top01), white);
                float cb = (float) lerp(lerp(0.19, 0.26, top01), lerp(0.37, 0.84, top01), white);
                float g = glowStrength * (float) (0.55 + 0.45 * (1 - top01));
                out.add(new Puff(e.x() + Math.cos(phi) * rr, py, e.z() + Math.sin(phi) * rr,
                        (float) (tube * (0.28 + 0.22 * rnd.nextDouble())), cr, cg, cb, alpha,
                        Math.min(1, h[0] * g * 0.8f), Math.min(1, h[1] * g * 0.8f), Math.min(1, h[2] * g * 0.8f), rnd.nextInt()));
            }
            // The domed top over the ring's hole.
            for (int i = 0; i < 70; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(rnd.nextDouble()) * ringRadius * 0.9;
                double py = centreY + tube * (0.35 + 0.5 * (1 - d / ringRadius)) + rnd.nextDouble() * tube * 0.25;
                double white = smooth(glow * 2, glow * 2 + 50, t);
                out.add(new Puff(e.x() + Math.cos(a) * d, py, e.z() + Math.sin(a) * d,
                        (float) (tube * (0.45 + 0.25 * rnd.nextDouble())), (float) lerp(0.56, 0.92, white), (float) lerp(0.34, 0.9, white),
                        (float) lerp(0.26, 0.87, white), alpha,
                        Math.min(1, h[0] * glowStrength * 0.4f), Math.min(1, h[1] * glowStrength * 0.4f),
                        Math.min(1, h[2] * glowStrength * 0.4f), rnd.nextInt()));
            }
        }

        rnd = new Random(e.seed() * 31L + 5);
        // 5. The stem: dust and debris drawn up from the ground into the cap.
        if (burst - ground < fireball * 6 && t > glow * 0.8) {
            double bottom = ground;
            double stemTop = centreY - tube * 0.6;
            double length = stemTop - bottom;
            if (length > 1) {
                double stemR = Math.max(fireball * 0.35, ringRadius * 0.28) * (1 - 0.3 * smooth(tau * 4, tau * 12, t));
                float alpha = (float) (smooth(glow * 0.8, glow * 4, t) * (1 - smooth(tau * 3, tau * 7, t)) * fade * 0.9);
                int n = 95;
                double climb = t * 0.06;
                for (int i = 0; i < n; i++) {
                    double u = (rnd.nextDouble() + climb * (0.6 + 0.4 * rnd.nextDouble())) % 1.0;
                    double py = bottom + u * length;
                    // Wider at the foot (the dust it lifts) and where it meets the cap.
                    double widen = 1 + 0.9 * Math.exp(-u * 6) + 0.6 * Math.exp(-(1 - u) * 5);
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(rnd.nextDouble()) * stemR * 0.5 * widen;
                    float cr = (float) lerp(0.62, 0.74, u);
                    float cg = (float) lerp(0.54, 0.68, u);
                    float cb = (float) lerp(0.44, 0.62, u);
                    out.add(new Puff(e.x() + Math.cos(a) * d, py, e.z() + Math.sin(a) * d,
                            (float) (stemR * 0.55 * widen * (0.7 + 0.5 * rnd.nextDouble())), cr, cg, cb, alpha, 0, 0, 0, rnd.nextInt()));
                }
            }
        }
    }

    /** How far the shock has run along the ground, blocks (faster than sound close in). */
    static double shockFront(double t, double r5) {
        double lo = 0;
        double hi = Math.max(r5 * 8, t * NuclearEffects.SOUND * 1.1) + 50;
        for (int i = 0; i < 28; i++) {
            double r = (lo + hi) / 2;
            double reach = NuclearEffects.soundDelay(r) * (0.5 + 0.5 * Math.min(1, r / Math.max(1, r5)));
            if (reach < t) {
                lo = r;
            } else {
                hi = r;
            }
        }
        return lo;
    }

    // ---------------------------------------------------------------------------------------
    // No air: plasma ball and ballistic debris
    // ---------------------------------------------------------------------------------------

    private static void vacuum(NukeClient.Event e, double t, List<Puff> out) {
        double kt = e.kt();
        double fireball = NuclearEffects.blocks(NuclearEffects.fireballKm(kt)) * 0.6; // no air to hold it in
        Random rnd = new Random(e.seed());
        double ground = Double.isNaN(e.groundY()) ? e.y() : e.groundY();
        if (t < 3) {
            // Vaporised rock glowing white-hot, swelling and fading in a few seconds (no air to hold the heat in).
            float[] h = heat(t / 1.6);
            double r = fireball * Math.sqrt(Math.min(1, t / 0.05)) * (1 + t * 1.2);
            float alpha = (float) (1 - smooth(0.8, 3, t));
            for (int i = 0; i < 30; i++) {
                double[] d = sphere(rnd);
                double depth = Math.cbrt(rnd.nextDouble()) * 0.6;
                out.add(new Puff(e.x() + d[0] * r * depth, e.y() + d[1] * r * depth, e.z() + d[2] * r * depth,
                        (float) (r * 0.45), 0.2f, 0.2f, 0.2f, alpha, h[0], h[1], h[2], rnd.nextInt()));
            }
        }
        rnd = new Random(e.seed() * 31L + 7);
        // Debris thrown out on ballistic arcs under the world's weak gravity, then lying where it falls.
        double g = 1.62 / 1000 * NuclearEffects.SIZE; // the Moon's pull, in drawn blocks/s²
        double speed = fireball * 1.5;
        for (int i = 0; i < 260; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double up = 0.15 + 0.8 * rnd.nextDouble();
            double v = speed * (0.3 + 0.7 * rnd.nextDouble());
            double vh = v * Math.sqrt(1 - up * up);
            double vy = v * up;
            double flight = 2 * vy / g;
            double tt = Math.min(t, flight);
            double y = e.y() + vy * tt - 0.5 * g * tt * tt;
            if (y < ground) {
                y = ground;
            }
            double d = vh * tt;
            float alpha = (float) (smooth(0.05, 0.4, t) * (1 - smooth(60, 120, t)) * 0.8);
            // Lit by the Sun, a curtain of grey rock dust spreading outward (no air: nothing billows).
            out.add(new Puff(e.x() + Math.cos(a) * d, y, e.z() + Math.sin(a) * d, (float) (Math.max(1.2, fireball * 0.35) * (0.6 + rnd.nextDouble())),
                    0.62f, 0.61f, 0.6f, alpha, 0, 0, 0, rnd.nextInt()));
        }
    }

    // ---------------------------------------------------------------------------------------
    // High in the air: a vast short-lived glowing ball
    // ---------------------------------------------------------------------------------------

    private static void high(NukeClient.Event e, double t, List<Puff> out) {
        double kt = e.kt();
        double r = NuclearEffects.blocks(NuclearEffects.fireballKm(kt)) * (2 + 3 * (1 - Math.exp(-t / 3)));
        double glow = NuclearEffects.glowSeconds(kt) * 3;
        float[] h = heat(t / glow);
        float alpha = (float) ((1 - smooth(glow, glow * 6, t)) * 0.6);
        Random rnd = new Random(e.seed());
        for (int i = 0; i < 50; i++) {
            double[] d = sphere(rnd);
            double depth = Math.cbrt(rnd.nextDouble()) * 0.7;
            out.add(new Puff(e.x() + d[0] * r * depth, e.y() + d[1] * r * depth, e.z() + d[2] * r * depth,
                    (float) (r * 0.4), 0.3f, 0.12f, 0.1f, alpha, h[0], h[1], h[2], rnd.nextInt()));
        }
    }

    private static double[] sphere(Random rnd) {
        double z = rnd.nextDouble() * 2 - 1;
        double a = rnd.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - z * z);
        return new double[]{s * Math.cos(a), z, s * Math.sin(a)};
    }
}
