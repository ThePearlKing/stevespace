package com.thepearlking.stevespace.vessel;

/**
 * The pilot's inputs for one tick.
 *
 * @param throttle 0..1
 * @param pitch    +1 tips the nose toward where the pilot faces (W), -1 away (S)
 * @param roll     +1 leans right (D), -1 left (A)
 * @param yaw      +1 turns right (E), -1 left (Q)
 */
public record VesselControls(double throttle, double pitch, double roll, double yaw) {

    public static final VesselControls IDLE = new VesselControls(0, 0, 0, 0);

    /** Clamps values a client sends to their valid ranges. */
    public VesselControls sanitized() {
        return new VesselControls(clamp(throttle, 0, 1), clamp(pitch, -1, 1), clamp(roll, -1, 1), clamp(yaw, -1, 1));
    }

    private static double clamp(double v, double min, double max) {
        return Double.isNaN(v) ? 0 : Math.max(min, Math.min(max, v));
    }
}
