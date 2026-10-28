package com.thepearlking.stevespace.client.space;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.network.TargetS2C;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.StateVector;

/** The vessel the player has chosen as a target: where it is now, carried along its orbit. */
public final class ClientTarget {

    private static TargetS2C last;

    private ClientTarget() {
    }

    public static void receive(TargetS2C message) {
        last = message.present() ? message : null;
    }

    public static boolean active() {
        return last != null;
    }

    public static String name() {
        return last == null ? "" : last.name();
    }

    public static CelestialBody body() {
        return last == null ? null : ClientUniverse.system().body(last.body());
    }

    /** The target's state relative to its body at universe time {@code t}. */
    public static StateVector stateAt(double t) {
        CelestialBody body = body();
        if (body == null) {
            return null;
        }
        double[] s = last.state();
        StateVector state = new StateVector(new DVec3(s[0], s[1], s[2]), new DVec3(s[3], s[4], s[5]));
        double scale;
        try {
            scale = SpaceConfig.GRAVITY_SCALE.get();
        } catch (IllegalStateException e) {
            scale = 1;
        }
        double mu = body.mu() * scale;
        return Kepler.elementsFromState(state, mu, last.time()).stateAt(t, mu);
    }
}
