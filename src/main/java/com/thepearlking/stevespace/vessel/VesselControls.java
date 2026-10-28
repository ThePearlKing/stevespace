package com.thepearlking.stevespace.vessel;

/**
 * The pilot's inputs for one tick.
 *
 * @param throttle 0..1
 * @param pitch    +1 tips the nose toward where the pilot faces (W), -1 away (S)
 * @param roll     +1 leans right (D), -1 left (A)
 * @param yaw      +1 turns right, -1 left
 * @param forward  RCS: +1 pushes the ship the way the pilot faces, -1 back
 * @param right    RCS: +1 to the pilot's right, -1 left
 * @param up       RCS: +1 toward the pilot's head, -1 toward their feet
 */
public record VesselControls(double throttle, double pitch, double roll, double yaw, double forward, double right, double up) {

    public static final VesselControls IDLE = new VesselControls(0, 0, 0, 0);

    public VesselControls(double throttle, double pitch, double roll, double yaw) {
        this(throttle, pitch, roll, yaw, 0, 0, 0);
    }

    /** Whether any RCS push is asked for. */
    public boolean translating() {
        return forward != 0 || right != 0 || up != 0;
    }

    /** Clamps values a client sends to their valid ranges. */
    public VesselControls sanitized() {
        return new VesselControls(clamp(throttle, 0, 1), clamp(pitch, -1, 1), clamp(roll, -1, 1), clamp(yaw, -1, 1),
                clamp(forward, -1, 1), clamp(right, -1, 1), clamp(up, -1, 1));
    }

    private static double clamp(double v, double min, double max) {
        return Double.isNaN(v) ? 0 : Math.max(min, Math.min(max, v));
    }
}
