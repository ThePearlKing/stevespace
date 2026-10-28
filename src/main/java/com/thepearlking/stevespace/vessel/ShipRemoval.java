package com.thepearlking.stevespace.vessel;

import com.thepearlking.stevespace.network.DockListS2C;
import com.thepearlking.stevespace.network.ShipRemoveS2C;
import com.thepearlking.stevespace.network.SpaceNetwork;
import com.thepearlking.stevespace.world.ParkedShips;
import com.thepearlking.stevespace.world.SpaceCells;
import com.thepearlking.stevespace.world.SpaceDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Removing one of your ships from the map: flying, or parked in orbit. Never one you (or anyone)
 * are aboard, and never someone else's.
 */
public final class ShipRemoval {

    private ShipRemoval() {
    }

    public static void handle(ServerPlayer player, UUID id, boolean confirm) {
        VesselEntity vessel = null;
        for (VesselEntity v : VesselEntity.serverVessels()) {
            if (!v.isRemoved() && v.getUUID().equals(id)) {
                vessel = v;
            }
        }
        ServerLevel space = player.server.getLevel(SpaceDimension.KEY);
        ParkedShips.Parked parked = null;
        if (vessel == null && space != null) {
            for (ParkedShips.Parked p : ParkedShips.get(player.server).all()) {
                if (id.equals(p.id()) || p.id() == null && id.equals(new UUID(0, p.cell()))) {
                    parked = p;
                }
            }
        }
        VesselStructure structure;
        String name;
        net.minecraft.core.BlockPos origin = null;
        if (vessel != null) {
            if (!vessel.ownedBy(player)) {
                return;
            }
            if (!vessel.playersAboard(2).isEmpty() || !vessel.getPassengers().isEmpty()) {
                player.displayClientMessage(Component.translatable("stevespace.ship.remove.aboard"), true);
                return;
            }
            structure = vessel.structure();
            name = vessel.getName().getString();
        } else if (parked != null) {
            if (parked.owner() != null && !parked.owner().equals(player.getUUID())) {
                return;
            }
            for (int cx = parked.min().getX() >> 4; cx <= parked.max().getX() >> 4; cx++) {
                for (int cz = parked.min().getZ() >> 4; cz <= parked.max().getZ() >> 4; cz++) {
                    space.getChunk(cx, cz);
                }
            }
            origin = Rendezvous.parkedOrigin(space, parked);
            if (origin == null) {
                ParkedShips.get(player.server).remove(parked.cell());
                return;
            }
            var scan = VesselAssembler.scan(space, origin, com.thepearlking.stevespace.config.SpaceConfig.MAX_VESSEL_BLOCKS.get(), false);
            if (!scan.ok()) {
                return;
            }
            structure = scan.structure();
            name = parked.name();
            try {
                var c = Component.Serializer.fromJson(name, space.registryAccess());
                if (c != null) {
                    name = c.getString();
                }
            } catch (RuntimeException ignored) {
                // plain text
            }
            for (ServerPlayer p : space.players()) {
                if (parked.contains(p.getX(), p.getY(), p.getZ(), 2)) {
                    player.displayClientMessage(Component.translatable("stevespace.ship.remove.aboard"), true);
                    return;
                }
            }
        } else {
            return;
        }
        if (!confirm) {
            boolean port = structure.blocks().values().stream()
                    .anyMatch(b -> b.getBlock() instanceof com.thepearlking.stevespace.block.DockingPortBlock);
            SpaceNetwork.sendTo(player, new ShipRemoveS2C(id, name, DockListS2C.Shape.of(structure.blocks()), port));
            return;
        }
        if (vessel != null) {
            if (vessel.materialized()) {
                VesselAssembler.removeFromWorld((ServerLevel) vessel.level(), vessel.blockOrigin(), structure);
            }
            vessel.discard(); // gives its cell back
        } else {
            VesselAssembler.removeFromWorld(space, origin, structure);
            ParkedShips.get(player.server).remove(parked.cell());
            SpaceCells.get(player.server).free(parked.cell());
        }
        com.thepearlking.stevespace.StevesSpace.LOGGER.info("{} removed their ship {}", player.getName().getString(), name);
        player.displayClientMessage(Component.translatable("stevespace.ship.remove.done", name), true);
    }
}
