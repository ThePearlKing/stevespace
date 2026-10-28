package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.Kepler;
import com.thepearlking.stevespace.universe.OrbitalElements;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.StateVector;
import net.minecraft.nbt.CompoundTag;

/**
 * A vessel's motion in space, relative to the body whose gravity dominates (patched conics).
 *
 * <p>While coasting it follows its Kepler orbit exactly ("on rails"), so time warp can skip ahead
 * any amount without drift. While the engines burn the motion is integrated step by step. When
 * the vessel leaves a body's sphere of influence (or enters a moon's) the reference body changes.
 *
 * <p>The axes of the vessel's cell in the space dimension ({@code axisX/Y/Z}, ecliptic-frame unit
 * vectors) are the local east, up and south below the vessel, the same as over the surface: the
 * planet is always "down". They turn slowly as the vessel goes round, and {@link #advance}
 * reports that turn so the vessel's attitude can be kept still in space.
 */
public final class OrbitalFlight {

    /** Integration step while burning, in seconds. */
    private static final double BURN_STEP = 0.05;
    /** While coasting, sphere-of-influence changes are checked at least this often (seconds). */
    private static final double SOI_CHECK = 30;
    /** Step while the orbit dips into the atmosphere (seconds). */
    private static final double FLOOR_CHECK = 1;

    private CelestialBody body;
    private StateVector state;
    private double time;
    private OrbitalElements coast;
    private DVec3 axisX;
    private DVec3 axisY;
    private DVec3 axisZ;
    private int cell;
    /** The vessel whose cell this one shares while they are close together (null when alone). */
    private java.util.UUID anchor;

    public OrbitalFlight(SolarSystem system, CelestialBody body, StateVector state, double time, int cell) {
        this.body = body;
        this.state = state;
        this.time = time;
        this.cell = cell;
        updateAxes(system);
    }

    /** Local east, up and south below the vessel (the planet's spin axis gives north). */
    private void updateAxes(SolarSystem system) {
        DVec3[] axes = localAxes(system, body, state, time);
        axisX = axes[0];
        axisY = axes[1];
        axisZ = axes[2];
    }

    /** East, up and south under a point in orbit: the axes of a cell that keeps the planet below. */
    public static DVec3[] localAxes(SolarSystem system, CelestialBody body, StateVector state, double time) {
        DVec3 up = state.position().normalize();
        DVec3 spin = system.frameAt(body, time).north();
        DVec3 east = spin.cross(up);
        if (east.length() < 1e-9) {
            east = new DVec3(1, 0, 0).sub(up.scale(up.x())); // straight over a pole: any horizontal will do
        }
        east = east.normalize();
        DVec3 north = up.cross(east);
        return new DVec3[]{east, up, north.scale(-1)};
    }

    public CelestialBody body() {
        return body;
    }

    public StateVector state() {
        return state;
    }

    public double time() {
        return time;
    }

    public int cell() {
        return cell;
    }

    public java.util.UUID anchor() {
        return anchor;
    }

    /** Sharing another vessel's cell (rendezvous), or back in a cell of its own ({@code anchor} null). */
    void setCell(int cell, java.util.UUID anchor) {
        this.cell = cell;
        this.anchor = anchor;
    }

    /** Replaces the state (docking and undocking change it). */
    void setState(StateVector state) {
        this.state = state;
        this.coast = null;
    }

    public DVec3 axisX() {
        return axisX;
    }

    public DVec3 axisY() {
        return axisY;
    }

    public DVec3 axisZ() {
        return axisZ;
    }

    /** Distance from the body's surface (blocks). */
    public double altitude() {
        return state.position().length() - body.radius();
    }

    /** A direction given in the cell's axes, in the ecliptic frame. */
    public DVec3 toInertial(double x, double y, double z) {
        return axisX.scale(x).add(axisY.scale(y)).add(axisZ.scale(z));
    }

    public double mu(double gravityScale) {
        return body.mu() * gravityScale;
    }

    /** The current orbit's elements (for the HUD and the map). */
    public OrbitalElements elements(double gravityScale) {
        return Kepler.elementsFromState(state, mu(gravityScale), time);
    }

    /**
     * Moves the vessel forward to universe time {@code t}. {@code thrust} is the engines'
     * acceleration in the ecliptic frame (blocks/s²), or null while coasting. A coast stops early
     * on reaching {@code floorRadius} (the top of the atmosphere), so however fast time is
     * warping the vessel never skips through the air.
     *
     * @return the rotation from the old cell axes to the new ones (apply it, inverted, to anything
     *         that should stay still in space)
     */
    public org.joml.Matrix3d advance(SolarSystem system, double t, DVec3 thrust, double gravityScale, double floorRadius) {
        // The solar system is rebuilt when the day length changes: use its current body object.
        CelestialBody current = system.body(body.id());
        if (current != null && current != body) {
            body = current;
            coast = null;
        }
        DVec3 oldX = axisX;
        DVec3 oldY = axisY;
        DVec3 oldZ = axisZ;
        double dt = t - time;
        if (dt > 0) {
            if (thrust == null) {
                coastTo(system, t, gravityScale, floorRadius);
            } else {
                coast = null;
                burn(system, t, thrust, gravityScale);
            }
            updateAxes(system);
        }
        // Row i, column j: new axis i · old axis j. Takes old-cell coordinates to new-cell ones.
        return new org.joml.Matrix3d(
                axisX.dot(oldX), axisY.dot(oldX), axisZ.dot(oldX),
                axisX.dot(oldY), axisY.dot(oldY), axisZ.dot(oldY),
                axisX.dot(oldZ), axisY.dot(oldZ), axisZ.dot(oldZ));
    }

    private void coastTo(SolarSystem system, double t, double gravityScale, double floorRadius) {
        if (coast == null) {
            coast = Kepler.elementsFromState(state, mu(gravityScale), time);
        }
        // Small steps while the orbit dips into the atmosphere, so the crossing is found.
        double step = coast.periapsis() < floorRadius ? FLOOR_CHECK : SOI_CHECK;
        double start = time;
        int checks = (int) Math.min(20000, Math.max(1, Math.ceil((t - start) / step)));
        for (int i = 1; i <= checks; i++) {
            double ti = start + (t - start) * i / checks;
            if (coast == null) {
                coast = Kepler.elementsFromState(state, mu(gravityScale), time);
            }
            StateVector next = coast.stateAt(ti, mu(gravityScale));
            if (next.position().length() < floorRadius && state.position().length() >= floorRadius) {
                // Stop at the edge of the air; the rest of the way is flown at normal speed.
                state = next;
                time = ti;
                return;
            }
            state = next;
            time = ti;
            if (updateSphereOfInfluence(system, ti)) {
                coast = null; // new reference body: new orbit from the converted state
            }
        }
    }

    private void burn(SolarSystem system, double t, DVec3 thrust, double gravityScale) {
        double mu = mu(gravityScale);
        while (time < t - 1e-9) {
            double h = Math.min(BURN_STEP, t - time);
            state = rk4(state, h, mu, thrust);
            time += h;
        }
        updateSphereOfInfluence(system, time);
    }

    /** One fourth-order Runge-Kutta step under the body's gravity plus a constant thrust. */
    private static StateVector rk4(StateVector s, double h, double mu, DVec3 thrust) {
        DVec3 r = s.position();
        DVec3 v = s.velocity();
        DVec3 a1 = gravity(r, mu).add(thrust);
        DVec3 r2 = r.add(v.scale(h / 2));
        DVec3 v2 = v.add(a1.scale(h / 2));
        DVec3 a2 = gravity(r2, mu).add(thrust);
        DVec3 r3 = r.add(v2.scale(h / 2));
        DVec3 v3 = v.add(a2.scale(h / 2));
        DVec3 a3 = gravity(r3, mu).add(thrust);
        DVec3 r4 = r.add(v3.scale(h));
        DVec3 v4 = v.add(a3.scale(h));
        DVec3 a4 = gravity(r4, mu).add(thrust);
        DVec3 position = r.add(v.add(v2.scale(2)).add(v3.scale(2)).add(v4).scale(h / 6));
        DVec3 velocity = v.add(a1.add(a2.scale(2)).add(a3.scale(2)).add(a4).scale(h / 6));
        return new StateVector(position, velocity);
    }

    private static DVec3 gravity(DVec3 r, double mu) {
        double d = r.length();
        return r.scale(-mu / (d * d * d));
    }

    /**
     * Switches the reference body when the vessel crosses a sphere of influence: out to the parent,
     * or in to one of this body's moons. Returns true if it changed.
     */
    private boolean updateSphereOfInfluence(SolarSystem system, double t) {
        if (body.parent() != null && state.position().length() > system.sphereOfInfluence(body)) {
            state = state.add(system.stateRelativeToParent(body, t));
            body = body.parent();
            return true;
        }
        // Acheron passing the Moon: the craft belongs to whichever of the two it is nearer.
        for (CelestialBody visitor : system.visitorsOf(body)) {
            StateVector fromParent = state.add(system.stateRelativeToParent(body, t));
            if (system.handOver(body, visitor, fromParent.position(), t)) {
                state = fromParent.sub(system.stateRelativeToParent(visitor, t));
                body = visitor;
                return true;
            }
        }
        for (CelestialBody moon : system.allChildrenOf(body)) {
            StateVector moonState = system.stateRelativeToParent(moon, t);
            if (state.position().sub(moonState.position()).length() < system.sphereOfInfluence(moon)) {
                state = state.sub(moonState);
                body = moon;
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("body", body.id());
        putVec(tag, "r", state.position());
        putVec(tag, "v", state.velocity());
        tag.putDouble("time", time);
        tag.putInt("cell", cell);
        if (anchor != null) {
            tag.putUUID("anchor", anchor);
        }
        return tag;
    }

    public static OrbitalFlight load(CompoundTag tag, SolarSystem system) {
        CelestialBody body = system.body(tag.getString("body"));
        if (body == null) {
            body = system.earth();
        }
        OrbitalFlight flight = new OrbitalFlight(system, body, new StateVector(getVec(tag, "r"), getVec(tag, "v")),
                tag.getDouble("time"), tag.getInt("cell"));
        if (tag.hasUUID("anchor")) {
            flight.anchor = tag.getUUID("anchor");
        }
        return flight;
    }

    private static void putVec(CompoundTag tag, String key, DVec3 v) {
        tag.putDouble(key + "x", v.x());
        tag.putDouble(key + "y", v.y());
        tag.putDouble(key + "z", v.z());
    }

    private static DVec3 getVec(CompoundTag tag, String key) {
        return new DVec3(tag.getDouble(key + "x"), tag.getDouble(key + "y"), tag.getDouble(key + "z"));
    }
}
