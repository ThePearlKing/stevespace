package com.thepearlking.stevespace.nuke;

import com.thepearlking.stevespace.radio.Radio;
import com.thepearlking.stevespace.remote.RemoteControl;
import com.thepearlking.stevespace.universe.CelestialBody;
import com.thepearlking.stevespace.universe.DVec3;
import com.thepearlking.stevespace.universe.ServerUniverse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The electromagnetic pulse of a burst above the air: gamma rays knock electrons out of the upper
 * atmosphere and the planet's field swings them into a pulse that reaches everything with a line
 * of sight to the burst (Starfish Prime blew streetlights and burglar alarms in Hawaii, 1,450 km
 * away). Machines and batteries near anyone standing in it are drained.
 */
public final class Emp {

    private static final int CHUNKS = 6;

    private Emp() {
    }

    static void strike(MinecraftServer server, CelestialBody body, DVec3 burst, double t) {
        if (burst == null || body.surfaceDimension() == null) {
            return;
        }
        ServerLevel ground = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(body.surfaceDimension())));
        if (ground == null) {
            return;
        }
        for (ServerPlayer player : ground.players()) {
            DVec3 at = RemoteControl.absolute(ground, player.position(), t);
            if (at == null || !Radio.lineOfSight(ServerUniverse.system(), at, burst, t)) {
                continue; // the planet itself shields them
            }
            int cx = player.blockPosition().getX() >> 4;
            int cz = player.blockPosition().getZ() >> 4;
            for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
                for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                    LevelChunk chunk = ground.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof com.thepearlking.stevespace.energy.PowerBlockEntity power && power.buffer().getEnergyStored() > 0) {
                            power.buffer().set(0);
                            power.setChanged();
                        }
                    }
                }
            }
        }
    }
}
