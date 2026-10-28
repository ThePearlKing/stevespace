package com.thepearlking.stevespace.universe;

/** The view from a point in space with fixed axes (a vessel cell's east/up/south at launch). */
public record SpaceView(DVec3 position, DVec3 xAxis, DVec3 yAxis, DVec3 zAxis) implements SkyView {

    @Override
    public DVec3 toLocal(DVec3 inertial) {
        return new DVec3(inertial.dot(xAxis), inertial.dot(yAxis), inertial.dot(zAxis));
    }

    @Override
    public DVec3 toInertial(DVec3 local) {
        return xAxis.scale(local.x()).add(yAxis.scale(local.y())).add(zAxis.scale(local.z()));
    }
}
