package com.thepearlking.stevespace.client.gui;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.client.space.ClientSpace;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.vessel.VesselEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * A small navball: the sky (blue) and ground (brown) around the ship as seen along its engines'
 * push, with the usual markers — prograde and retrograde (yellow), normal (purple), radial
 * (cyan) and the planned maneuver (blue).
 */
final class Navball {

    private static final int SKY = 0x3B6EA0;
    private static final int GROUND = 0x8A5A30;

    /** Directions that matter, in the vessel's world axes (any may be null). */
    record Frame(DVec3 up, DVec3 north, DVec3 prograde, DVec3 normal, DVec3 radial, DVec3 maneuver) {
    }

    private Navball() {
    }

    /** Works out the frame for the vessel the player is in; {@code maneuver} is inertial (or null). */
    static Frame frame(VesselEntity vessel, DVec3 maneuver) {
        double[] axes = ClientSpace.axes();
        StateVector state = ClientSpace.orbitActive() ? ClientSpace.stateAt(ClientUniverse.time()) : null;
        if (axes != null && state != null && ClientSpace.body() != null) {
            DVec3 x = new DVec3(axes[0], axes[1], axes[2]);
            DVec3 y = new DVec3(axes[3], axes[4], axes[5]);
            DVec3 z = new DVec3(axes[6], axes[7], axes[8]);
            java.util.function.Function<DVec3, DVec3> toWorld = d -> new DVec3(d.dot(x), d.dot(y), d.dot(z));
            DVec3 r = state.position().normalize();
            DVec3 spin = ClientSpace.body().spinAxis();
            DVec3 north = spin.sub(r.scale(spin.dot(r)));
            north = north.length() < 1e-6 ? new DVec3(0, 0, 1) : north.normalize();
            DVec3 pro = state.velocity().normalize();
            DVec3 nor = state.position().cross(state.velocity()).normalize();
            DVec3 rad = nor.cross(pro).normalize();
            return new Frame(toWorld.apply(r), toWorld.apply(north), toWorld.apply(pro), toWorld.apply(nor), toWorld.apply(rad),
                    maneuver == null ? null : toWorld.apply(maneuver));
        }
        // Over a surface (low down, or high in "map mode"): east, up, south; prograde over the ground.
        Vector3f v = vessel.velocity();
        DVec3 velocity = new DVec3(v.x, v.y, v.z);
        return new Frame(new DVec3(0, 1, 0), new DVec3(0, 0, -1), velocity.length() > 1 ? velocity.normalize() : null, null, null, null);
    }

    static void draw(GuiGraphics g, Font font, VesselEntity vessel, Frame frame, int cx, int cy, int radius, float partial) {
        Quaternionf q = vessel.renderOrientation(partial);
        org.joml.Vector3d thrust = new org.joml.Vector3d(vessel.stats().thrustVector());
        if (thrust.lengthSquared() < 1e-9) {
            thrust.set(0, 1, 0);
        }
        thrust.normalize();
        DVec3 f = turn(q, thrust.x, thrust.y, thrust.z);
        Direction facing = vessel.stats().seats().isEmpty() ? Direction.NORTH : vessel.stats().seats().get(0).facing();
        DVec3 down = turn(q, facing.getStepX(), 0, facing.getStepZ());
        down = down.sub(f.scale(down.dot(f)));
        if (down.length() < 1e-4) {
            down = turn(q, 0, 0, 1).sub(f.scale(turn(q, 0, 0, 1).dot(f)));
        }
        DVec3 up = down.normalize().scale(-1);
        DVec3 right = f.cross(up).normalize();
        DVec3 worldUp = frame.up();

        // The ball itself, two pixels at a time.
        int cell = 2;
        for (int py = -radius; py < radius; py += cell) {
            for (int px = -radius; px < radius; px += cell) {
                double u = (px + cell / 2.0) / radius;
                double w = -(py + cell / 2.0) / radius;
                double rr = u * u + w * w;
                if (rr > 1) {
                    continue;
                }
                double depth = Math.sqrt(1 - rr);
                DVec3 d = right.scale(u).add(up.scale(w)).add(f.scale(depth));
                double el = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d.dot(worldUp)))));
                int base = el >= 0 ? SKY : GROUND;
                double shade = 0.62 + 0.38 * depth;
                double band = Math.abs(el) % 30;
                if (Math.abs(el) < 0.9) {
                    base = 0xF2F2F2;
                    shade = 1;
                } else if (band < 0.7 || band > 29.3) {
                    shade *= 1.25;
                }
                g.fill(cx + px, cy + py, cx + px + cell, cy + py + cell, 0xFF000000 | scale(base, shade));
            }
        }
        // Rim.
        for (int i = 0; i < 180; i++) {
            double a = i * 2 * Math.PI / 180;
            int x = cx + (int) Math.round(Math.cos(a) * (radius + 1));
            int y = cy + (int) Math.round(Math.sin(a) * (radius + 1));
            g.fill(x - 1, y - 1, x + 1, y + 1, 0xFF20262A);
        }
        // Markers.
        marker(g, f, right, up, frame.prograde(), cx, cy, radius, 0xFFE8D24A, false);
        marker(g, f, right, up, frame.prograde() == null ? null : frame.prograde().scale(-1), cx, cy, radius, 0xFFE8D24A, true);
        marker(g, f, right, up, frame.normal(), cx, cy, radius, 0xFFC56BE8, false);
        marker(g, f, right, up, frame.normal() == null ? null : frame.normal().scale(-1), cx, cy, radius, 0xFFC56BE8, true);
        marker(g, f, right, up, frame.radial(), cx, cy, radius, 0xFF5FD8E0, false);
        marker(g, f, right, up, frame.radial() == null ? null : frame.radial().scale(-1), cx, cy, radius, 0xFF5FD8E0, true);
        if (frame.maneuver() != null) {
            DVec3 m = frame.maneuver();
            double z = m.dot(f);
            if (z > 0) {
                int x = cx + (int) Math.round(m.dot(right) * radius);
                int y = cy - (int) Math.round(m.dot(up) * radius);
                for (int k = 0; k <= 4; k++) {
                    g.fill(x - k, y - 4 + k, x + k + 1, y - 3 + k, 0xFF4C8DFF);
                    g.fill(x - k, y + 4 - k, x + k + 1, y + 5 - k, 0xFF4C8DFF);
                }
            }
        }
        // The nose: where the engines push.
        int orange = 0xFFF0A030;
        g.fill(cx - 12, cy, cx - 4, cy + 2, orange);
        g.fill(cx + 4, cy, cx + 12, cy + 2, orange);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, orange);
        // Heading and pitch.
        DVec3 east = frame.north().cross(worldUp).normalize();
        double heading = (Math.toDegrees(Math.atan2(f.dot(east), f.dot(frame.north()))) + 360) % 360;
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, f.dot(worldUp)))));
        String text = String.format(Locale.ROOT, "HDG %03.0f  PIT %+.0f°", heading, pitch);
        g.drawString(font, text, cx - font.width(text) / 2, cy - radius - 12, 0xFFD7E3DA, true);
    }

    /** A marker: a ring with a dot (toward), or a cross (away); only on the near side. */
    private static void marker(GuiGraphics g, DVec3 f, DVec3 right, DVec3 up, DVec3 m, int cx, int cy, int radius, int color, boolean cross) {
        if (m == null || m.dot(f) <= 0.02) {
            return;
        }
        int x = cx + (int) Math.round(m.dot(right) * radius);
        int y = cy - (int) Math.round(m.dot(up) * radius);
        if (cross) {
            for (int k = -3; k <= 3; k++) {
                g.fill(x + k, y + k, x + k + 1, y + k + 1, color);
                g.fill(x + k, y - k, x + k + 1, y - k + 1, color);
            }
        } else {
            g.renderOutline(x - 3, y - 3, 7, 7, color);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static DVec3 turn(Quaternionf q, double x, double y, double z) {
        Vector3f v = q.transform(new Vector3f((float) x, (float) y, (float) z));
        return new DVec3(v.x, v.y, v.z);
    }

    private static int scale(int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * k);
        int b = (int) Math.min(255, (rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
