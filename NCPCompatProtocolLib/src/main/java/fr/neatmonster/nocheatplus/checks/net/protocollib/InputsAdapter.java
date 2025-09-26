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

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.AutoWrapper;

import fr.neatmonster.nocheatplus.checks.net.NetData;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketInput;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Listen to the Steer_vehicle packet for extrapolating input information sent by the player (1.21.2+)
 * The packet is actually named "Player_Input" in the Minecraft protocol, but ProtocolLib uses the former name for compatibility.
 */
public class InputsAdapter extends BaseAdapter {
    
    private static AutoWrapper<DataPacketInput> INPUT_WRAPPER; 
    
    private static PacketType[] initPacketTypes() {
        final List<PacketType> types = new LinkedList<PacketType>(Arrays.asList(PacketType.Play.Client.STEER_VEHICLE));
        return types.toArray(new PacketType[types.size()]);
    }
    
    public InputsAdapter(Plugin plugin) {
        super(plugin, ListenerPriority.MONITOR, initPacketTypes());
    }
    
    @Override
    public void onPacketReceiving(final PacketEvent event) {
        handleInputPacket(event);
    }
    
    private void handleInputPacket(PacketEvent event) {
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
        final Player player = event.getPlayer();
        final PacketContainer packet = event.getPacket();
        final IPlayerData pData = DataManager.getPlayerData(player);
        final NetData data = pData.getGenericInstance(NetData.class);
        // Instead of a primitive, we're being handed the full Input object from Mojang’s code. 
        StructureModifier<Object> objs = packet.getModifier().withType(Object.class);
        Object raw = objs.read(0);
        if (INPUT_WRAPPER == null) {
            INPUT_WRAPPER = AutoWrapper.wrap(DataPacketInput.class, raw.getClass());
        }
        final DataPacketInput in = INPUT_WRAPPER.wrap(raw);
        data.addInputQueue(in);
    }
}
