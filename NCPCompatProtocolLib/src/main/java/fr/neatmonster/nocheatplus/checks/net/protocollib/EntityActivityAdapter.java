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

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

import fr.neatmonster.nocheatplus.checks.net.entity.ClientEntity;
import fr.neatmonster.nocheatplus.checks.net.entity.ClientEntityTracker;
import fr.neatmonster.nocheatplus.checks.net.entity.EntityPacketReader;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Keeps {@link ClientEntityTracker} in step with the packets each client actually receives.
 * The transaction clock is {@link fr.neatmonster.nocheatplus.checks.net.protocollib.TransactionAdapter}.
 */
public class EntityActivityAdapter extends BaseAdapter {

    private static PacketType[] listenTypes() {
        List<PacketType> types = new ArrayList<PacketType>();
        add(types, PacketType.Play.Server.SPAWN_ENTITY);
        add(types, PacketType.Play.Server.SPAWN_ENTITY_LIVING);
        add(types, PacketType.Play.Server.NAMED_ENTITY_SPAWN);
        add(types, PacketType.Play.Server.SPAWN_ENTITY_PAINTING);
        add(types, PacketType.Play.Server.ENTITY_TELEPORT);
        add(types, PacketType.Play.Server.ENTITY_POSITION_SYNC);
        add(types, PacketType.Play.Server.REL_ENTITY_MOVE);
        add(types, PacketType.Play.Server.REL_ENTITY_MOVE_LOOK);
        add(types, PacketType.Play.Server.ENTITY_LOOK);
        add(types, PacketType.Play.Server.ENTITY_DESTROY);
        add(types, PacketType.Play.Server.ENTITY_METADATA);
        add(types, PacketType.Play.Server.ENTITY_STATUS);
        add(types, PacketType.Play.Server.MOUNT);
        add(types, PacketType.Play.Server.ATTACH_ENTITY);
        add(types, PacketType.Play.Client.POSITION);
        add(types, PacketType.Play.Client.POSITION_LOOK);
        add(types, PacketType.Play.Client.LOOK);
        add(types, PacketType.Play.Client.FLYING);
        add(types, PacketType.Play.Client.VEHICLE_MOVE);
        add(types, PacketType.Play.Client.CLIENT_TICK_END);
        return types.toArray(new PacketType[types.size()]);
    }

    private static void add(List<PacketType> types, PacketType type) {
        try {
            if (type != null && type.isSupported()) {
                types.add(type);
            }
        }
        catch (Throwable ignored) {}
    }

    private int failures;

    public EntityActivityAdapter(Plugin plugin) {
        super(plugin, ListenerPriority.LOW, listenTypes());
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (!accept(event)) {
            return;
        }
        try {
            Player player = event.getPlayer();
            ClientEntityTracker tracker = tracker(player);
            if (tracker == null) {
                return;
            }
            PacketType type = event.getPacketType();
            if (type == PacketType.Play.Client.CLIENT_TICK_END) {
                tracker.onClientTick(true);
                return;
            }
            tracker.onClientTick(false);
        }
        catch (Throwable throwable) {
            fail(throwable);
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled() || !accept(event)) {
            return;
        }
        try {
            Player player = event.getPlayer();
            ClientEntityTracker tracker = tracker(player);
            if (tracker == null) {
                return;
            }
            PacketType type = event.getPacketType();
            PacketContainer packet = event.getPacket();
            if (type == PacketType.Play.Server.SPAWN_ENTITY || type == PacketType.Play.Server.SPAWN_ENTITY_LIVING
                    || type == PacketType.Play.Server.NAMED_ENTITY_SPAWN || type == PacketType.Play.Server.SPAWN_ENTITY_PAINTING) {
                EntityPacketReader.Spawn spawn = EntityPacketReader.readSpawn(packet);
                if (spawn != null) {
                    tracker.onSpawn(player, spawn.id, spawn.uuid, kind(type, spawn.typeName), spawn.typeName, spawn.typeId,
                            spawn.x, spawn.y, spawn.z, spawn.positionKnown, spawn.yaw, spawn.pitch);
                }
            }
            else if (type == PacketType.Play.Server.REL_ENTITY_MOVE || type == PacketType.Play.Server.REL_ENTITY_MOVE_LOOK
                    || type == PacketType.Play.Server.ENTITY_LOOK) {
                boolean look = type != PacketType.Play.Server.REL_ENTITY_MOVE;
                boolean move = type != PacketType.Play.Server.ENTITY_LOOK;
                EntityPacketReader.Relative relative = EntityPacketReader.readRelative(packet, move, look);
                if (relative != null) {
                    tracker.onRelativeMove(player, relative.id, relative.dx, relative.dy, relative.dz, relative.hasPosition, relative.yaw, relative.pitch);
                }
            }
            else if (type == PacketType.Play.Server.ENTITY_TELEPORT || type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
                EntityPacketReader.Absolute absolute = EntityPacketReader.readAbsolute(packet);
                if (absolute != null) {
                    tracker.onAbsoluteMove(player, absolute.id, absolute.x, absolute.y, absolute.z,
                            absolute.relativeX, absolute.relativeY, absolute.relativeZ, absolute.yaw, absolute.pitch,
                            type == PacketType.Play.Server.ENTITY_TELEPORT);
                }
            }
            else if (type == PacketType.Play.Server.ENTITY_DESTROY) {
                tracker.onDestroy(player, EntityPacketReader.readDestroy(packet));
            }
            else if (type == PacketType.Play.Server.ENTITY_METADATA) {
                Integer id = packet.getIntegers().readSafely(0);
                if (id != null) {
                    tracker.onMetadata(player, id.intValue(), EntityPacketReader.readSharedFlags(packet),
                            EntityPacketReader.readFireworkAttached(packet));
                }
            }
            else if (type == PacketType.Play.Server.ENTITY_STATUS) {
                Integer id = packet.getIntegers().readSafely(0);
                Byte status = packet.getBytes().readSafely(0);
                if (id != null && status != null) {
                    tracker.onStatus(player, id.intValue(), status.byteValue());
                }
            }
            else if (type == PacketType.Play.Server.MOUNT) {
                EntityPacketReader.Mount mount = EntityPacketReader.readMount(packet);
                if (mount != null) {
                    tracker.onMount(player, mount.vehicleId, mount.passengers);
                }
            }
            else if (type == PacketType.Play.Server.ATTACH_ENTITY && !ServerVersion.isAtLeast("1.9")) {
                EntityPacketReader.Attach attach = EntityPacketReader.readAttach(packet);
                if (attach != null) {
                    tracker.onLegacyAttach(player, attach.passengerId, attach.vehicleId);
                }
            }
        }
        catch (Throwable throwable) {
            fail(throwable);
        }
    }

    private static ClientEntity.Kind kind(PacketType type, String typeName) {
        if (type == PacketType.Play.Server.NAMED_ENTITY_SPAWN || "PLAYER".equals(typeName)) {
            return ClientEntity.Kind.PLAYER;
        }
        if (type == PacketType.Play.Server.SPAWN_ENTITY_LIVING) {
            return ClientEntity.Kind.LIVING;
        }
        if (type == PacketType.Play.Server.SPAWN_ENTITY_PAINTING) {
            return ClientEntity.Kind.PAINTING;
        }
        return ClientEntity.Kind.OBJECT;
    }

    private static ClientEntityTracker tracker(Player player) {
        if (player == null) {
            return null;
        }
        IPlayerData data = DataManager.getPlayerDataSafe(player);
        if (data == null) {
            return null;
        }
        return data.getGenericInstance(ClientEntityTracker.class);
    }

    private static boolean accept(PacketEvent event) {
        try {
            if (event.isPlayerTemporary()) {
                return false;
            }
        }
        catch (NoSuchMethodError ignored) {}
        return event.getPlayer() != null;
    }

    private void fail(Throwable throwable) {
        if (failures++ < 3) {
            StaticLog.logWarning("Entity tracker packet listener failed: " + throwable.getMessage());
        }
    }

}
