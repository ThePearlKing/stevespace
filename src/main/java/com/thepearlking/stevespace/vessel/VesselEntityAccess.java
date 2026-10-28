package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.StateVector;

/** Test access to vessel internals that game code doesn't expose. */
public final class VesselEntityAccess {

    private VesselEntityAccess() {
    }

    public static void setState(VesselEntity vessel, StateVector state) {
        vessel.orbital().setState(state);
    }

    public static void setOwner(VesselEntity vessel, java.util.UUID owner) {
        vessel.setOwner(owner);
    }
}
