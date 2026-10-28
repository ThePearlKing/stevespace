package com.thepearlking.stevespace.universe;

/** Position and velocity at one instant, relative to some parent body (blocks, blocks/second). */
public record StateVector(DVec3 position, DVec3 velocity) {

    public StateVector add(StateVector other) {
        return new StateVector(position.add(other.position), velocity.add(other.velocity));
    }

    public StateVector sub(StateVector other) {
        return new StateVector(position.sub(other.position), velocity.sub(other.velocity));
    }
}
