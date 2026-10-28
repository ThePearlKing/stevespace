package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.universe.DVec3;
import net.minecraft.core.Direction;
import org.joml.Matrix3d;
import org.joml.Quaterniond;

/**
 * The shapes of docking: which way a ship must point for its port to meet another square on
 * (docked ships join block to block, so they must be at right angles to each other), and a path
 * round the other ship to the front of its port.
 */
public final class DockGeometry {

    /** The 24 ways a cube can be turned, as rotations (vessel axes to vessel axes). */
    private static final Quaterniond[] TURNS = new Quaterniond[24];
    private static final int[][][] MATRICES = new int[24][][];

    static {
        int n = 0;
        int[][] perms = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        for (int[] p : perms) {
            for (int signs = 0; signs < 8; signs++) {
                int[][] m = new int[3][3];
                for (int row = 0; row < 3; row++) {
                    m[row][p[row]] = ((signs >> row) & 1) == 0 ? 1 : -1;
                }
                int det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                        - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                        + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
                if (det != 1) {
                    continue;
                }
                Matrix3d matrix = new Matrix3d();
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        matrix.set(col, row, m[row][col]);
                    }
                }
                MATRICES[n] = m;
                TURNS[n++] = new Quaterniond().setFromNormalized(matrix);
            }
        }
    }

    private DockGeometry() {
    }

    /**
     * The attitude (fixed space) a ship should hold so that its port facing {@code mine} meets the
     * other ship's port facing {@code theirs} face to face, square: of the four such attitudes,
     * the one nearest to how it points now.
     *
     * @param current the ship's attitude now (vessel axes to fixed space)
     * @param other   the other ship's attitude (vessel axes to fixed space)
     */
    public static Quaterniond dockAttitude(Quaterniond current, Quaterniond other, Direction mine, Direction theirs) {
        Quaterniond best = null;
        double closest = -1;
        for (int i = 0; i < 24; i++) {
            int[][] m = MATRICES[i];
            // My port's direction, in the other ship's axes, must point straight back at its port.
            int x = m[0][0] * mine.getStepX() + m[0][1] * mine.getStepY() + m[0][2] * mine.getStepZ();
            int y = m[1][0] * mine.getStepX() + m[1][1] * mine.getStepY() + m[1][2] * mine.getStepZ();
            int z = m[2][0] * mine.getStepX() + m[2][1] * mine.getStepY() + m[2][2] * mine.getStepZ();
            if (x != -theirs.getStepX() || y != -theirs.getStepY() || z != -theirs.getStepZ()) {
                continue;
            }
            if (m[1][1] != 1) {
                continue; // that would tip one ship over against the other: never
            }
            Quaterniond candidate = new Quaterniond(other).mul(TURNS[i]).normalize();
            double near = Math.abs(candidate.dot(current));
            if (near > closest) {
                closest = near;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Whether two ports can meet with both ships upright to each other (no tipping over): top to
     * bottom, or side to side.
     */
    public static boolean canMeet(Direction mine, Direction theirs) {
        if (mine.getAxis() == Direction.Axis.Y || theirs.getAxis() == Direction.Axis.Y) {
            return mine == theirs.getOpposite();
        }
        return true;
    }

    /** Angle (radians) between two attitudes. */
    public static double angle(Quaterniond a, Quaterniond b) {
        return 2 * Math.acos(Math.min(1, Math.abs(a.dot(b))));
    }

    /** A vector in vessel axes turned by an attitude. */
    public static DVec3 turn(Quaterniond attitude, double x, double y, double z) {
        var v = attitude.transform(new org.joml.Vector3d(x, y, z));
        return new DVec3(v.x, v.y, v.z);
    }

    /**
     * Where to head on the way from {@code from} to {@code to} without going through a ship centred
     * at {@code centre} (keeping {@code clearance} from it): straight there if the way is clear,
     * otherwise a point out to the side, round the ship.
     */
    public static DVec3 around(DVec3 from, DVec3 to, DVec3 centre, double clearance) {
        DVec3 path = to.sub(from);
        double length = path.length();
        if (length < 1e-6) {
            return to;
        }
        DVec3 along = path.scale(1 / length);
        double s = Math.max(0, Math.min(length, centre.sub(from).dot(along)));
        DVec3 nearest = from.add(along.scale(s));
        if (nearest.sub(centre).length() >= clearance || from.sub(centre).length() < clearance) {
            return to;
        }
        DVec3 a = from.sub(centre).normalize();
        DVec3 b = to.sub(centre).normalize();
        DVec3 side = a.add(b);
        if (side.length() < 0.2) {
            // Right behind it: go round any way at right angles.
            side = a.cross(Math.abs(a.y()) < 0.9 ? DVec3.Y : new DVec3(1, 0, 0));
        }
        return centre.add(side.normalize().scale(clearance * 1.4));
    }
}
