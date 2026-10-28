package com.thepearlking.stevespace.energy;

import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.LocalSky;
import com.thepearlking.stevespace.universe.ServerUniverse;
import com.thepearlking.stevespace.universe.SolarSystem;
import com.thepearlking.stevespace.universe.Weather;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceDimension;
import com.thepearlking.stevespace.world.planet.PlanetGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * How much sunlight falls on a solar panel, as a fraction of full sun at Earth's distance:
 * falls off with the square of the distance from the Sun, follows the Sun's height in the sky,
 * and is cut by thick air (Venus' clouds, Titan's smog, a Martian dust storm).
 */
public final class Sunlight {

    private Sunlight() {
    }

    public static double at(ServerLevel level, BlockPos pos) {
        // Panels have cells on both faces: in space the Sun can just as well be underneath.
        if (!level.canSeeSky(pos.above()) && !(SpaceDimension.isSpace(level) && clearBelow(level, pos))) {
            return 0;
        }
        SolarSystem system = ServerUniverse.system();
        double t = ServerUniverse.time(level.getServer());
        CelestialBody body;
        double height;
        if (SpaceDimension.isSpace(level)) {
            ParkedShips.Parked ship = ParkedShips.get(level.getServer()).at(pos.getX() + 0.5);
            body = ship == null ? system.earth() : system.body(ship.body());
            height = 1; // in orbit the panels can face the Sun
        } else {
            body = ServerUniverse.bodyOf(level);
            if (body == null) {
                return 0;
            }
            PlanetGeometry geometry = new PlanetGeometry(body.radius());
            double elevation = LocalSky.at(system, body, geometry.latitude(pos.getZ()), geometry.longitude(pos.getX()), 0, t)
                    .elevationOf(system, system.sun(), t);
            height = Math.max(0, Math.sin(elevation));
        }
        if (body == null) {
            return 0;
        }
        double air = switch (body.id()) {
            case "earth" -> 0.8;
            case "venus" -> 0.12;
            case "titan" -> 0.01;
            case "mars" -> 0.6 * (1 - 0.85 * Weather.marsStorm(t));
            default -> 1;
        };
        if (SpaceDimension.isSpace(level)) {
            air = 1;
        }
        CelestialBody planet = body;
        while (planet.parent() != null && !planet.parent().id().equals(system.sun().id())) {
            planet = planet.parent();
        }
        double distance = planet.parent() == null ? SolarSystem.AU : planet.orbit().semiMajorAxis();
        double inverseSquare = Math.pow(SolarSystem.AU / distance, 2);
        return Math.min(8, inverseSquare) * height * air;
    }

    /** Nothing under the panel for a good way down. */
    private static boolean clearBelow(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < 64 && p.getY() > level.getMinBuildHeight(); i++) {
            p.move(0, -1, 0);
            if (!level.getBlockState(p).isAir()) {
                return false;
            }
        }
        return true;
    }
}
