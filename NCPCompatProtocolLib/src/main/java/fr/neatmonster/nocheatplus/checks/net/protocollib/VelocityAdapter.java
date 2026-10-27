/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.checks.net.protocollib;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BukkitConverters;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Adapter for handling velocity related packet
 */
public class VelocityAdapter extends BaseAdapter {
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(
                PacketType.Play.Server.EXPLOSION,
                PacketType.Play.Server.ENTITY_VELOCITY
        ));
        return types.toArray(new PacketType[types.size()]);
    }

    public VelocityAdapter(Plugin plugin) {
        super(plugin, ListenerPriority.LOWEST, initPacketTypes());
    }

    @Override
    public void onPacketSending(final PacketEvent event) {
        handleOutgoingVelocityPacket(event);
    }
    
    private void handleOutgoingVelocityPacket(PacketEvent event) {
        try {
            if (event.isPlayerTemporary()) return;
        } 
        catch (NoSuchMethodError e) {
            if (event.getPlayer() == null) {
                counters.add(ProtocolLibComponent.idNullPlayer, 1);
                return;
            }
            if (DataManager.getPlayerDataSafe(event.getPlayer()) == null) {
                return;
            }
        }
        if (event.getPacketType() == PacketType.Play.Server.EXPLOSION) {
            handleExplosionPacket(event);
        } else {
            handleEntityVelocityPacket(event);
        }
    }

    private void handleEntityVelocityPacket(PacketEvent event) {
        final Player player = event.getPlayer();
        final PacketContainer packet = event.getPacket();
        final StructureModifier<Integer> ints = packet.getIntegers();
        Integer id = ints.readSafely(0);
        id = (id == null) ? -1 : id;
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (id != player.getEntityId() || !pData.isCheckActive(CheckType.MOVING, player)) return;
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);
        if (ints.size() == 4) {
            final Double velX = ints.read(1) / 8000D;
            final Double velY = ints.read(2) / 8000D;
            final Double velZ = ints.read(3) / 8000D;
            data.addVelocity(player, cc, velX, velY, velZ);
        } else {
            final Vector vec = packet.getVectors().readSafely(0);
            if (vec != null) {
                data.addVelocity(player, cc, vec.getX(), vec.getY(), vec.getZ());
            }
        }
    }

    private void handleExplosionPacket(PacketEvent event) {
        final Player player = event.getPlayer();
        final PacketContainer packet = event.getPacket();
        final StructureModifier<Float> floats = packet.getFloat();
        final IPlayerData pData = DataManager.getPlayerData(player);
        if (!pData.isCheckActive(CheckType.MOVING, player)) return;
        final MovingData data = pData.getGenericInstance(MovingData.class);
        final MovingConfig cc = pData.getGenericInstance(MovingConfig.class);

        if (floats.size() != 4) {
            Optional<Vector> read = packet.getOptionals(BukkitConverters.getVectorConverter()).read(0);
            if (read.isPresent()) {
                final Vector vec = read.get();
                data.addVelocity(player, cc, vec.getX(), vec.getY(), vec.getZ(), VelocityFlags.ORIGIN_EXPLOSION | VelocityFlags.ADDITIVE);
            }
            return;
        }

        //final Float strength = floats.read(0);

        final Float velX = floats.read(1);
        final Float velY = floats.read(2);
        final Float velZ = floats.read(3);
        if (Math.abs(velX) == 0.0 && Math.abs(velZ) == 0.0 && Math.abs(velY) == 0.0) return;
        // Process velocity.
        data.addVelocity(player, cc, velX, velY, velZ, VelocityFlags.ORIGIN_EXPLOSION | VelocityFlags.ADDITIVE);
    }
}