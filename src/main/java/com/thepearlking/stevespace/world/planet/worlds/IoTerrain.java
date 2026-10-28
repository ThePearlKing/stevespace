package com.thepearlking.stevespace.world.planet.worlds;

import com.thepearlking.stevespace.world.planet.BodyTerrain;
import com.thepearlking.stevespace.world.planet.SphereFeatures;
import com.thepearlking.stevespace.world.planet.TerrainSample;
import com.thepearlking.stevespace.world.planet.noise.SphereNoise;
import com.thepearlking.stevespace.world.planet.noise.SpherePoint;

import java.util.List;

/**
 * Io, the most volcanic place in the solar system. Plains of sulphur in yellow, white and orange,
 * scarred everywhere by heat: hundreds of paterae (flat-floored volcanic pits), most of them
 * holding lava lakes skinned with cracked crust; rivers of lava winding out of them across the
 * plains; glowing fissures; low shield volcanoes with lava running down their flanks; fields of
 * small spatter cones; red and white rings where plumes fall back; tall lonely mountains that are
 * tilted blocks of crust. No impact craters at all: everything is resurfaced too fast.
 *
 * <p>Named giants: Loki Patera (a lava lake two hundred blocks across with an island in it),
 * Pele (a lava lake inside a huge red plume ring), Prometheus (a white ring and a long flow
 * field), Tvashtar (a curtain of fire along a fissure in the north) and Boösaule Montes, the
 * tallest mountain.
 *
 * <p>Sample fields: {@code temperature} = sulphur frost (0 yellow .. 1 white), {@code humidity}
 * = red sulphur (0..1), {@code river} = volcanic heat (0..1; above 0.9 molten, 0.85..0.9 glowing
 * crust), {@code seaIceDepth} = dark lava/ash cover (0..1), {@code under} = 1 where a vent
 * erupts (the top of a spatter cone, along a fissure).
 */
public final class IoTerrain extends BodyTerrain {

    public static final double RADIUS = 1822;
    private static final double BASE = 86;

    public static final Biome PLAINS = biome("io_plains", 0xC9B04A);
    public static final Biome FROST = biome("io_frost", 0xDCD4AE);
    public static final Biome PATERA = biome("io_patera", 0x4A3E34);
    public static final Biome MOUNTAINS = biome("io_mountains", 0x8A7454);
    public static final Biome POLAR = biome("io_polar", 0x8C6E54);

    /** Molten, glowing crust, and the crust plates on a lava lake. */
    private static final double MOLTEN = 0.97;
    private static final double GLOWING = 0.87;

    private static final double[] LOKI = unit(12, -50);
    private static final double[] PELE = unit(-19, 105);
    private static final double[] PROMETHEUS = unit(-2, 160);
    private static final double[] BOOSAULE = unit(-5, -95);

    private final SphereNoise swell = new SphereNoise(seed + 1, 5, 3, 0.5);
    private final SphereNoise rough = new SphereNoise(seed + 2, 160, 2, 0.5);
    private final SphereNoise frost = new SphereNoise(seed + 3, 6, 4, 0.55);
    private final SphereNoise flowNoise = new SphereNoise(seed + 4, 30, 3, 0.55);
    private final SphereNoise blockWarp = new SphereNoise(seed + 5, 40, 2, 0.5);
    private final SphereNoise riverNoise = new SphereNoise(seed + 6, 70, 2, 0.45);
    private final SphereNoise edge = new SphereNoise(seed + 7, 25, 3, 0.5);
    private final SphereNoise tint = new SphereNoise(seed + 8, 18, 3, 0.5);
    private final SphereFeatures.Spot[] paterae;
    private final SphereFeatures.Spot[] mountains;
    private final SphereFeatures.Spot[] shields;
    private final SphereFeatures.Arc[] fissures;
    private final SphereFeatures.Arc tvashtar;

    public IoTerrain() {
        super(RADIUS, 0x496F566F6C63616EL);
        paterae = SphereFeatures.spots(seed + 10, 220, 18, 75, 4, 9);
        mountains = SphereFeatures.spots(seed + 11, 60, 50, 160, 25, 110);
        shields = SphereFeatures.spots(seed + 12, 34, 70, 190, 12, 34);
        fissures = SphereFeatures.arcs(seed + 13, 70, 0.03, 0.16, 1.4, 2.6, 0, 1);
        double[] c = unit(62, 20);
        double[] d = unit(62, 40);
        double nx = c[1] * d[2] - c[2] * d[1];
        double ny = c[2] * d[0] - c[0] * d[2];
        double nz = c[0] * d[1] - c[1] * d[0];
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        tvashtar = new SphereFeatures.Arc(nx / len, ny / len, nz / len, c[0], c[1], c[2], 0.07, 3.5, 1);
    }

    @Override
    public List<Biome> biomes() {
        return List.of(PLAINS, FROST, PATERA, MOUNTAINS, POLAR);
    }

    /** What is at one point, built up feature by feature. */
    private static final class Ground {
        double height;
        double red;
        double white;
        double heat;
        double dark;
        double vent;
        Biome biome;

        void molten(double carve) {
            if (heat < MOLTEN) {
                height -= carve;
            }
            heat = Math.max(heat, MOLTEN + 0.02);
        }
    }

    private final ThreadLocal<Ground> ground = ThreadLocal.withInitial(Ground::new);

    @Override
    protected void sample(SpherePoint p, double latitude, TerrainSample out) {
        Ground g = ground.get();
        g.heat = 0;
        g.vent = 0;
        // Layered plains: sheets of lava and sulphur stacked up, ending in low scarps.
        double layers = swell.sample(p) * 4 + flowNoise.sample(p.x * 0.5, p.y * 0.5, p.z * 0.5) * 6;
        double step = Math.floor(layers / 2.2) * 2.2;
        double terrace = step + smoothstep(0.75, 1.0, (layers - step) / 2.2) * 2.2;
        g.height = BASE + terrace + rough.sample(p) * 0.5;
        g.white = clamp01(frost.sample(p) * 1.8 + 0.3);
        double polar = smoothstep(Math.toRadians(52), Math.toRadians(68), Math.abs(latitude) + swell.sample(p) * 0.15);
        // Orange and tan stains across the yellow, and dark old flows half buried in sulphur.
        g.red = Math.max(polar * 0.35, smoothstep(0.25, 0.6, tint.sample(p)) * 0.45);
        g.dark = Math.max(polar * 0.3, smoothstep(0.35, 0.6, -tint.sample(p.y, p.z, p.x)) * 0.45);
        g.biome = g.white > 0.6 ? FROST : PLAINS;

        mountains(p, g);
        shields(p, g);
        paterae(p, g);
        fissures(p, g);
        cones(p, g);
        giants(p, g);

        if (polar > 0.5 && g.biome != PATERA && g.biome != MOUNTAINS) {
            g.biome = POLAR;
        }
        out.height = g.height;
        out.temperature = g.white;
        out.humidity = g.red;
        out.river = g.heat;
        out.seaIceDepth = g.dark;
        out.under = g.vent;
        out.biome = g.biome;
    }

    /** Mountains: tilted slabs of crust with cliffs on one side and a long slope on the other. */
    private void mountains(SpherePoint p, Ground g) {
        for (SphereFeatures.Spot m : mountains) {
            if (!m.near(p, RADIUS, 1.2)) {
                continue;
            }
            double d = m.distance(p, RADIUS) * (1 + blockWarp.sample(p) * 0.35);
            if (d < 1) {
                double tilt = 0.55 + 0.45 * ((p.x - m.x()) * 13 + (p.z - m.z()) * 7) * RADIUS / m.radius() / 15;
                double mesa = 1 - smoothstep(0.8, 1.0, d);
                g.height += mesa * m.value() * Math.max(0.25, Math.min(1, tilt));
                if (mesa > 0.4) {
                    g.biome = MOUNTAINS;
                }
            }
        }
    }

    /**
     * Shield volcanoes: broad low cones with a lava-filled caldera on top (on the live ones) and
     * dark flows streaming down every side, some of them still molten.
     */
    private void shields(SpherePoint p, Ground g) {
        for (int i = 0; i < shields.length; i++) {
            SphereFeatures.Spot s = shields[i];
            if (!s.near(p, RADIUS, 1.05)) {
                continue;
            }
            double d = s.distance(p, RADIUS) * (1 + edge.sample(p) * 0.12);
            if (d >= 1) {
                continue;
            }
            double rise = Math.pow(1 - d, 1.7) * s.value();
            boolean live = i % 3 != 2;
            double theta = around(p, s);
            double streaks = Math.abs(Math.sin(theta * 6 + flowNoise.sample(p) * 5));
            if (d < 0.11) {
                // The caldera: a sunken pit, a lava lake in a live one.
                double wall = 1 - smoothstep(0.08, 0.11, d);
                g.height += rise - wall * 7;
                g.biome = PATERA;
                g.dark = Math.max(g.dark, wall);
                if (live && d < 0.085) {
                    lake(p, g, 6, 0.085 - d);
                }
                continue;
            }
            g.height += rise;
            g.dark = Math.max(g.dark, smoothstep(0.55, 0.15, streaks) * (1 - d) * 1.4);
            if (live && d < 0.8 && g.biome != MOUNTAINS) {
                double width = 0.05 * (1 - d / 0.8);
                if (streaks < width) {
                    g.molten(1);
                } else if (streaks < width * 2.2) {
                    g.heat = Math.max(g.heat, GLOWING);
                }
            }
        }
    }

    /**
     * Paterae: a sunken flat floor with steep walls. The live ones (most of them) hold a lava
     * lake and pour rivers of lava out over the plains; the biggest throw plumes whose fallout
     * makes a wide red ring, or a white one of sulphur dioxide frost.
     */
    private void paterae(SpherePoint p, Ground g) {
        for (int i = 0; i < paterae.length; i++) {
            SphereFeatures.Spot v = paterae[i];
            boolean plume = i % 9 == 0 || i % 9 == 4;
            if (!v.near(p, RADIUS, plume ? 7 : 4.6)) {
                continue;
            }
            double d = v.distance(p, RADIUS) * (1 + blockWarp.sample(p) * 0.2);
            boolean live = i % 3 != 0;
            if (d < 1.05) {
                double wall = 1 - smoothstep(0.88, 1.02, d);
                g.height -= wall * v.value();
                g.biome = PATERA;
                g.dark = Math.max(g.dark, wall);
                if (live && d < 0.86) {
                    lake(p, g, 9, (0.86 - d) * v.radius() / 7);
                }
            } else if (d < 4.5) {
                // Dark flows spilling out over the plains, with lava still running down the middle.
                double flow = smoothstep(0.25, 0.45, flowNoise.sample(p)) * (1 - smoothstep(1.2, 2.6, d));
                g.dark = Math.max(g.dark, flow);
                g.height += flow * 0.6;
                if (live) {
                    river(p, g, new double[]{v.x(), v.y(), v.z()}, 2 + i % 5, d * v.radius(), 1 - (d - 1.05) / 3.45, i);
                }
            }
            if (i % 9 == 0) {
                g.red = Math.max(g.red, Math.exp(-Math.pow((d - 5) / 1.1, 2)) * 0.9);
            } else if (i % 9 == 4) {
                g.white = Math.max(g.white, Math.exp(-Math.pow((d - 4) / 1.4, 2)));
            }
        }
    }

    /**
     * Channels of lava running outward from a vent across the plains, winding as they go: {@code
     * strength} 1 at the source fading to 0 at their ends, {@code count} channels round the
     * centre, {@code from} blocks from it. Glowing crust banks and dark ground either side.
     */
    private void river(SpherePoint p, Ground g, double[] centre, int count, double from, double strength, long salt) {
        if (strength <= 0 || g.biome == MOUNTAINS) {
            return; // the mountains stand above the flows
        }
        double theta = around(p, centre[0], centre[1], centre[2]) + salt;
        double wave = Math.abs(Math.sin(theta * count / 2 + flowNoise.sample(p) * 3 + riverNoise.sample(p) * 0.6));
        // In blocks from the channel's middle line (the channels fan out, the lines stay thin).
        double off = Math.asin(Math.min(1, wave)) / (count / 2.0) * Math.max(from, 1);
        double width = 0.8 + 1.4 * strength;
        if (off < width) {
            g.molten(1);
        } else if (off < width + 1.5) {
            g.heat = Math.max(g.heat, GLOWING);
            g.dark = 1;
        } else if (off < width + 6 * strength) {
            g.dark = Math.max(g.dark, 0.7);
        }
    }

    /**
     * A lava lake's surface: plates of dark crust drifting on it, split by glowing cracks, and
     * open molten lava along the shore ({@code shore} = blocks in from the edge).
     */
    private void lake(SpherePoint p, Ground g, double plate, double shore) {
        g.dark = 1;
        double[] c = cells(p, plate, 77);
        boolean crack = c[1] - c[0] < 0.9;
        if (shore < 1.6 || crack || c[2] < 0.12) {
            g.molten(0);
        } else {
            g.heat = Math.max(g.heat, GLOWING);
        }
    }

    /** Fissures: long straight cracks across the plains glowing with lava, venting here and there. */
    private void fissures(SpherePoint p, Ground g) {
        for (SphereFeatures.Arc f : fissures) {
            fissure(p, g, f);
        }
        fissure(p, g, tvashtar);
    }

    private void fissure(SpherePoint p, Ground g, SphereFeatures.Arc f) {
        if (g.biome == MOUNTAINS) {
            return;
        }
        double off = p.x * f.nx() + p.y * f.ny() + p.z * f.nz();
        if (Math.abs(off) * RADIUS > f.halfWidth() * 6) {
            return;
        }
        double a = f.across(p, RADIUS);
        if (a > 6) {
            return;
        }
        double taper = 1 - Math.pow(f.alongFraction(p), 3);
        g.dark = Math.max(g.dark, smoothstep(6, 2, a) * taper);
        if (a < taper) {
            g.molten(1);
            g.vent = 1; // the column builder puts a vent here and there along it
        } else if (a < taper * 2.2) {
            g.heat = Math.max(g.heat, GLOWING);
        }
    }

    /** Spatter cones: small steep mounds of cinders round a vent, in clusters across the plains. */
    private void cones(SpherePoint p, Ground g) {
        double[] c = cells(p, 70, 31);
        if (c[2] > 0.3 || g.biome == MOUNTAINS || g.heat > 0) {
            return;
        }
        double size = 6 + c[2] * 20;
        double d = c[0] / size;
        if (d < 1) {
            double cone = (1 - d) * (1 - d) * (4 + c[2] * 14);
            g.height += cone - (d < 0.18 ? (0.18 - d) * 14 : 0);
            g.dark = Math.max(g.dark, 1 - d * 0.6);
            g.red = Math.max(g.red, smoothstep(1.4, 0.6, d) * 0.5);
            if (d < 0.1) {
                g.vent = 1;
            }
        } else if (d < 2.2) {
            g.dark = Math.max(g.dark, smoothstep(2.2, 1, d) * 0.6); // the ash round it
        }
    }

    /** The named places. */
    private void giants(SpherePoint p, Ground g) {
        // Loki Patera: a lake of lava two hundred blocks across, a cold island in the middle.
        double loki = arc(p, LOKI) * RADIUS;
        if (loki < 230) {
            double r = loki / 170 + edge.sample(p) * 0.08;
            if (r < 1.05) {
                double wall = 1 - smoothstep(0.92, 1.04, r);
                g.height = Math.min(g.height, BASE + 4) - wall * 6;
                g.biome = PATERA;
                double island = loki / 48 + edge.sample(p.z, p.x, p.y) * 0.25;
                if (island < 1) {
                    g.height += (1 - smoothstep(0.75, 1, island)) * 9;
                    g.dark = 1;
                    g.heat = 0;
                } else if (r < 0.95) {
                    g.heat = 0;
                    lake(p, g, 12, (0.95 - r) * 170);
                }
            } else {
                g.dark = Math.max(g.dark, smoothstep(1.35, 1.05, r));
            }
        }
        // Pele: a lava lake glowing at the heart of a red ring six hundred blocks wide.
        double pele = arc(p, PELE) * RADIUS;
        if (pele < 760) {
            double ring = Math.exp(-Math.pow((pele + edge.sample(p) * 70 - 580) / 90, 2));
            g.red = Math.max(g.red, ring);
            g.dark = Math.max(g.dark, smoothstep(220, 60, pele) * 0.8);
            if (pele < 52) {
                double wall = 1 - smoothstep(42, 52, pele);
                g.height -= wall * 8;
                g.biome = PATERA;
                if (pele < 44) {
                    g.heat = 0;
                    lake(p, g, 7, 44 - pele);
                }
            }
        }
        // Prometheus: a white plume ring, and a long field of flows with lava still running.
        double prom = arc(p, PROMETHEUS) * RADIUS;
        if (prom < 420) {
            g.white = Math.max(g.white, Math.exp(-Math.pow((prom + edge.sample(p) * 40 - 300) / 60, 2)));
            if (prom < 200) {
                g.dark = Math.max(g.dark, smoothstep(200, 120, prom));
                river(p, g, PROMETHEUS, 9, prom, 1 - prom / 200, 3);
            }
        }
        // Boösaule Montes: the tallest mountain, a great tilted slab.
        double boo = arc(p, BOOSAULE) * RADIUS / 150 * (1 + blockWarp.sample(p) * 0.25);
        if (boo < 1) {
            double tilt = 0.45 + 0.55 * smoothstep(-1, 1, (p.x - BOOSAULE[0]) * RADIUS / 150 * 1.5);
            g.height += (1 - smoothstep(0.75, 1, boo)) * 135 * tilt;
            g.biome = MOUNTAINS;
            g.heat = 0;
            g.dark = 0;
        }
    }

    /** The angle round a spot's centre (radians), for patterns that radiate from it. */
    private static double around(SpherePoint p, SphereFeatures.Spot s) {
        return around(p, s.x(), s.y(), s.z());
    }

    private static double around(SpherePoint p, double sx, double sy, double sz) {
        // East and north at the centre.
        double ex = -sz;
        double ez = sx;
        double el = Math.sqrt(ex * ex + ez * ez) + 1e-9;
        ex /= el;
        ez /= el;
        double nx = sy * ez;
        double ny = sz * ex - sx * ez;
        double nz = -sy * ex;
        return Math.atan2(p.x * nx + p.y * ny + p.z * nz, p.x * ex + p.z * ez);
    }

    @Override
    public int surfaceColor(TerrainSample s) {
        int base = mix(PLAINS.mapColor(), FROST.mapColor(), s.temperature);
        if (s.biome == MOUNTAINS) {
            base = MOUNTAINS.mapColor();
        }
        base = mix(base, 0xA2543A, s.humidity);
        base = mix(base, 0x3E3630, s.seaIceDepth);
        // From far off a lava lake is dark crust; only the open lava glows.
        base = mix(base, 0x2C221E, smoothstep(0.84, 0.88, s.river));
        base = mix(base, 0xC8601E, smoothstep(0.9, 0.98, s.river) * 0.85);
        return base;
    }
}
