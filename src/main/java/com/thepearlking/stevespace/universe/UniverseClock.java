package com.thepearlking.stevespace.universe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The solar system's master clock (seconds of universe time), saved with the world.
 * Planets' positions, rotation and every orbit are functions of this one number.
 */
public final class UniverseClock extends SavedData {

    private static final String NAME = "stevespace_universe";

    private double time;
    private double warp = 1;
    private boolean started;
    /** Rotation anchor of the current day length (see SolarSystem). */
    private double dayLength = -1;
    private double rotationAnchorTime;
    private double rotationAnchorTurns;

    public static UniverseClock get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(UniverseClock::new, UniverseClock::load, null), NAME);
    }

    private static UniverseClock load(CompoundTag tag, HolderLookup.Provider registries) {
        UniverseClock clock = new UniverseClock();
        clock.time = tag.getDouble("time");
        clock.warp = Math.max(1, tag.getDouble("warp"));
        clock.started = tag.getBoolean("started");
        clock.dayLength = tag.contains("day_length") ? tag.getDouble("day_length") : -1;
        clock.rotationAnchorTime = tag.getDouble("rotation_anchor_time");
        clock.rotationAnchorTurns = tag.getDouble("rotation_anchor_turns");
        return clock;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("time", time);
        tag.putDouble("warp", warp);
        tag.putBoolean("started", started);
        tag.putDouble("day_length", dayLength);
        tag.putDouble("rotation_anchor_time", rotationAnchorTime);
        tag.putDouble("rotation_anchor_turns", rotationAnchorTurns);
        return tag;
    }

    public double time() {
        return time;
    }

    public double warp() {
        return warp;
    }

    public boolean isStarted() {
        return started;
    }

    /** Sets the starting time of a new world (once). */
    public void start(double time) {
        this.time = time;
        this.started = true;
        setDirty();
    }

    public void advance(double seconds) {
        time += seconds;
        setDirty();
    }

    public void setTime(double time) {
        this.time = time;
        setDirty();
    }

    public double dayLength() {
        return dayLength;
    }

    public double rotationAnchorTime() {
        return rotationAnchorTime;
    }

    public double rotationAnchorTurns() {
        return rotationAnchorTurns;
    }

    /** Records a new day length taking effect now, with Earth having turned {@code turns} times. */
    public void setDayLength(double seconds, double anchorTime, double turns) {
        this.dayLength = seconds;
        this.rotationAnchorTime = anchorTime;
        this.rotationAnchorTurns = turns;
        setDirty();
    }

    public void setWarp(double warp) {
        this.warp = Math.max(1, warp);
        setDirty();
    }
}
