package com.thepearlking.stevespace.client.space;

import com.thepearlking.stevespace.client.ClientUniverse;
import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.network.FleetS2C;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.StateVector;

import java.util.ArrayList;
import java.util.List;

/** The other ships shown on the map (yours, and other people's with radios), carried along their orbits. */
public final class ClientFleet {

    /** One ship: its orbit as elements, so it can be placed at any time. */
    public record Ship(java.util.UUID id, String name, CelestialBody body, boolean owned, OrbitalElements orbit, double mu) {

        public StateVector stateAt(double t) {
            return orbit.stateAt(t, mu);
        }
    }

    private static List<Ship> ships = List.of();
    /** Show your own ships, and other people's, on the map (the toggles in its corner). */
    public static boolean showOwn = true;
    public static boolean showOthers = true;

    private ClientFleet() {
    }

    public static void receive(FleetS2C msg) {
        var system = ClientUniverse.system();
        if (system == null) {
            return;
        }
        List<Ship> list = new ArrayList<>();
        for (FleetS2C.Ship s : msg.ships()) {
            CelestialBody body = system.body(s.body());
            if (body == null) {
                continue;
            }
            double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
            double[] v = s.state();
            StateVector state = new StateVector(new DVec3(v[0], v[1], v[2]), new DVec3(v[3], v[4], v[5]));
            list.add(new Ship(s.id(), s.name(), body, s.owned(), Kepler.elementsFromState(state, mu, msg.time()), mu));
        }
        ships = list;
    }

    /** The ships to draw now (as toggled). */
    public static List<Ship> shown() {
        List<Ship> out = new ArrayList<>();
        for (Ship s : ships) {
            if (s.owned() ? showOwn : showOthers) {
                out.add(s);
            }
        }
        return out;
    }
}
