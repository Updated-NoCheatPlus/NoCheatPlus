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
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.net.entity.ClientEntityTracker;
import fr.neatmonster.nocheatplus.checks.net.transaction.TransactionChannel;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.components.registry.feature.TickListener;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Sends the transaction ping and accepts only the replies this plugin wrote.
 * One ping goes out at the end of each server tick so queued confirmations flush.
 * Entity tracking uses that same clock.
 */
public class TransactionAdapter extends BaseAdapter implements TickListener {

    private static final boolean USE_PING;

    static {
        boolean ping = false;
        try {
            ping = ServerVersion.isAtLeast("1.17") && PacketType.Play.Server.PING.isSupported();
        }
        catch (Throwable ignored) {}
        USE_PING = ping;
    }

    private static PacketType[] listenTypes() {
        List<PacketType> types = new ArrayList<PacketType>();
        add(types, PacketType.Play.Client.PONG);
        add(types, PacketType.Play.Client.TRANSACTION);
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

    public TransactionAdapter(Plugin plugin) {
        super(plugin, ListenerPriority.LOW, listenTypes());
        TransactionChannel.setSender(new TransactionChannel.Sender() {
            @Override
            public boolean send(Player player, int wireId, boolean legacy) {
                return write(player, wireId, legacy);
            }
        }, USE_PING);
        NCPAPIProvider.getNoCheatPlusAPI().addComponent(this);
    }

    static boolean write(Player player, int wireId, boolean legacy) {
        try {
            PacketContainer packet;
            if (!legacy) {
                packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PING);
                packet.getIntegers().write(0, Integer.valueOf(wireId));
            }
            else {
                packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.TRANSACTION);
                packet.getIntegers().write(0, Integer.valueOf(0));
                packet.getShorts().write(0, Short.valueOf((short) wireId));
                packet.getBooleans().write(0, Boolean.FALSE);
            }
            // filters = false: this ping must not re-enter the listener that is sending it
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet, false);
            return true;
        }
        catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onTick(int tick, long timeLast) {
        Player[] players = BridgeMisc.getOnlinePlayers();
        for (int index = 0; index < players.length; index++) {
            Player player = players[index];
            if (player == null || !player.isOnline()) {
                continue;
            }
            ClientEntityTracker tracker = tracker(player);
            if (tracker != null) {
                // Also writes this tick's ping, which confirms tasks queued on lastSent + 1.
                tracker.onEndOfTick(player);
            }
            else {
                TransactionChannel channel = channel(player);
                if (channel != null) {
                    channel.send(player);
                }
            }
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        if (!accept(event)) {
            return;
        }
        try {
            Player player = event.getPlayer();
            TransactionChannel channel = channel(player);
            if (channel == null) {
                return;
            }
            PacketType type = event.getPacketType();
            Integer id = readId(event.getPacket(), type);
            if (id != null && channel.onReply(id.intValue())) {
                event.setCancelled(true);
            }
        }
        catch (Throwable throwable) {
            fail(throwable);
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
    }

    private static Integer readId(PacketContainer packet, PacketType type) {
        try {
            if (type == PacketType.Play.Client.PONG) {
                return packet.getIntegers().readSafely(0);
            }
            Short action = packet.getShorts().readSafely(0);
            return action == null ? null : Integer.valueOf(action.shortValue());
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static TransactionChannel channel(Player player) {
        if (player == null) {
            return null;
        }
        IPlayerData data = DataManager.getPlayerDataSafe(player);
        if (data == null) {
            return null;
        }
        return data.getGenericInstance(TransactionChannel.class);
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
            StaticLog.logWarning("Transaction ping listener failed: " + throwable.getMessage());
        }
    }
}
