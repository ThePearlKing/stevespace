package com.thepearlking.stevespace.nuke;

/**
 * What a nuclear explosion does, from the standard weapons-effects scaling laws (Glasstone and
 * Dolan, "The Effects of Nuclear Weapons"), worked out in real kilometres and seconds, then drawn
 * at {@link #SIZE} times the world's scale (the world is 1 block = 1 km; the user chose 5x so a
 * blast reads at a glance). Times are real: a fireball glows for seconds, a mushroom cloud takes
 * minutes to climb.
 */
public final class NuclearEffects {

    /** Drawn this many times bigger than true scale (sizes only, not times). */
    public static final double SIZE = 5;
    /** Speed of sound near the ground, blocks per second (a block is a metre to anyone standing there). */
    public static final double SOUND = 343;

    /** The yields on the device's dial, kilotons: Hiroshima, Starfish Prime, Tsar Bomba. */
    public static final double[] YIELDS = {15, 1450, 50_000};

    private NuclearEffects() {
    }

    /** Where it went off decides almost everything. */
    public enum Regime {
        /** On or near the ground in air: fireball, shock, mushroom cloud. */
        SURFACE,
        /** In the air, above the ground: the same, the fireball clear of the ground. */
        AIR,
        /** 30-100 km up: a huge, short-lived fireball, no mushroom, a red glow. */
        HIGH,
        /** Above the air (Starfish Prime): flash, debris shell, artificial aurora, radiation belt, EMP. */
        SPACE,
        /** On a world with no air: a flash, a plasma ball, debris thrown out, silence. */
        VACUUM
    }

    public static Regime regime(double heightKm, boolean air, double fireballKm) {
        if (!air) {
            return heightKm > 50 ? Regime.SPACE : Regime.VACUUM;
        }
        if (heightKm > 100) {
            return Regime.SPACE;
        }
        if (heightKm > 30) {
            return Regime.HIGH;
        }
        // Touching the ground if the fireball reaches it.
        return heightKm < fireballKm ? Regime.SURFACE : Regime.AIR;
    }

    /** Fireball radius at its largest, km (air burst at low altitude). */
    public static double fireballKm(double kt) {
        return 0.06 * Math.pow(kt, 0.4);
    }

    /** Distance at which the blast wave gives {@code psi} overpressure (optimum height air burst), km. */
    public static double blastKm(double kt, double psi) {
        // 1 kt reference distances: 20 psi 0.22 km, 5 psi 0.47 km, 1 psi 1.2 km.
        double ref = psi >= 20 ? 0.22 : psi >= 5 ? 0.47 : 1.2;
        return ref * Math.cbrt(kt);
    }

    /** Distance for third-degree burns (clear day), km. */
    public static double burnKm(double kt) {
        return 0.65 * Math.pow(kt, 0.41);
    }

    /** Crater radius of a surface burst in dry soil, km. */
    public static double craterKm(double kt) {
        return 0.026 * Math.cbrt(kt) * 1.2;
    }

    private static final double[][] CLOUD = {
            // kt, top height km, cap diameter km (from test films and the stabilized-cloud charts)
            {1, 4.5, 1.5}, {15, 10, 5}, {100, 14, 10}, {1000, 21, 25}, {10_000, 37, 55}, {50_000, 64, 95}};

    private static double cloud(double kt, int column) {
        double lk = Math.log10(Math.max(1, kt));
        for (int i = 1; i < CLOUD.length; i++) {
            double l0 = Math.log10(CLOUD[i - 1][0]);
            double l1 = Math.log10(CLOUD[i][0]);
            if (lk <= l1 || i == CLOUD.length - 1) {
                double f = Math.max(0, Math.min(1, (lk - l0) / (l1 - l0)));
                return CLOUD[i - 1][column] + (CLOUD[i][column] - CLOUD[i - 1][column]) * f;
            }
        }
        return CLOUD[CLOUD.length - 1][column];
    }

    /** Height of the mushroom cloud's top when it stops climbing, km. */
    public static double cloudTopKm(double kt) {
        return cloud(kt, 1);
    }

    /** Width of the cloud's cap when it stops climbing, km. */
    public static double cloudCapKm(double kt) {
        return cloud(kt, 2);
    }

    /** How long the fireball glows brightly (its thermal pulse), seconds. */
    public static double glowSeconds(double kt) {
        return 0.4 * Math.pow(kt, 0.42) + 0.6;
    }

    /** Time constant of the cloud's climb (it is near the top after about three of these), seconds. */
    public static double riseSeconds(double kt) {
        return 25 + 12 * Math.pow(kt, 0.2);
    }

    /** Drawn size, blocks, for a real size in km. */
    public static double blocks(double km) {
        return km * SIZE;
    }

    /** Seconds for the sound to reach {@code blocks} away. */
    public static double soundDelay(double blocks) {
        return blocks / SOUND;
    }
}
