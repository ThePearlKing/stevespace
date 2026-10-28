package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.config.SpaceConfig;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.StateVector;
import com.thepearlking.stevespace.world.ParkedShips;

import java.util.UUID;

/**
 * The ship being docked with, found again whenever it's needed: flying (or woken up from being
 * parked, then it keeps its id or at least its cell), or still parked in its cell in orbit.
 */
record DockTarget(VesselEntity vessel, ParkedShips.Parked parked, CelestialBody body, String name) {

    static DockTarget find(VesselEntity self, UUID id, int cell) {
        if (self.level().getServer() == null) {
            return null;
        }
        VesselEntity byCell = null;
        for (VesselEntity v : VesselEntity.serverVessels()) {
            if (v == self || v.isRemoved() || v.orbital() == null) {
                continue;
            }
            if (id != null && v.getUUID().equals(id)) {
                return new DockTarget(v, null, v.orbital().body(), v.getName().getString());
            }
            if (cell >= 0 && v.orbital().cell() == cell && v.orbital().anchor() == null) {
                byCell = v;
            }
        }
        if (byCell != null) {
            return new DockTarget(byCell, null, byCell.orbital().body(), byCell.getName().getString());
        }
        ParkedShips parkedShips = ParkedShips.get(self.level().getServer());
        for (ParkedShips.Parked p : parkedShips.all()) {
            if (id != null && id.equals(p.id()) || id == null && p.cell() == cell) {
                CelestialBody body = ServerUniverse.system().body(p.body());
                return body == null ? null : new DockTarget(null, p, body, p.name());
            }
        }
        return null;
    }

    /** Where it is at time {@code t} (relative to its body). */
    StateVector state(double t) {
        if (vessel != null) {
            OrbitalFlight o = vessel.orbital();
            StateVector s = o.state();
            if (Math.abs(t - o.time()) < 2) {
                return new StateVector(s.position().add(s.velocity().scale(t - o.time())), s.velocity());
            }
            double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
            return Kepler.elementsFromState(s, mu, o.time()).stateAt(t, mu);
        }
        double mu = body.mu() * SpaceConfig.GRAVITY_SCALE.get();
        return Kepler.elementsFromState(parked.state(), mu, parked.time()).stateAt(t, mu);
    }
}
