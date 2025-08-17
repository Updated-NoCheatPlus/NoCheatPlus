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
package fr.neatmonster.nocheatplus.checks.moving.vehicle;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.location.setback.SetBackEntry;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveData;
import fr.neatmonster.nocheatplus.compat.SchedulerHelper;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Classic (Evenprime) more packets check for players inside vehicles
 */
public class VehicleMorePackets extends Check {

    /**
     * The usual number of packets per timeframe.
     * 20 would be for perfect internet connections, 22 is good enough.
     */
    private final static int packetsPerTimeframe = 22;

    /**
     * Instantiates a new more packet vehicle check.
     */
    public VehicleMorePackets() {
        super(CheckType.MOVING_VEHICLE_MOREPACKETS);
    }

    /**
     * Checks a player with the old-style MorePackets check:
     * 1. Players get assigned a certain amount of "free" packets as a limit initially
     * 2. Every move packet reduces that limit by 1
     * 3. If more than 1 second of time passed, the limit gets increased
     *    by 22 * time in seconds, up to 50 and they gets a new "setback" location
     * 4. If the player reaches limit = 0 -> teleport them back to "setback"
     * 5. If there was a long pause (maybe lag), limit may be up to 100
     * 
     * @param player
     *            the player
     * @param thisMove
     * @param setBack Already decided set back, if not null.
     * @param data
     * @param cc 
     * @param pData 
     * @return the new to location where to setback the player.
     */
    public SetBackEntry check(final Player player, final VehicleMoveData thisMove, final SetBackEntry setBack, 
                              final MovingData data, final MovingConfig cc, final IPlayerData pData) {
        // TODO: Should refactor and use the NetStatic check instead.
        // Take time once, first:
        final long time = System.currentTimeMillis();
        final boolean debug = pData.isDebugActive(type);
        final boolean allowSettingSetBack = setBack == null && !SchedulerHelper.isTaskScheduled(data.vehicleSetBackTaskId);
        SetBackEntry newTo = null;
        // Take a packet from the buffer.
        data.vehicleMorePacketsBuffer--;

        if (setBack != null || SchedulerHelper.isTaskScheduled(data.vehicleSetBackTaskId)){
            // Short version !
            // TODO: This is bad. Needs to check if still scheduled (a BukkitTask thing) and just skip.
            return data.vehicleSetBacks.getValidMidTermEntry();
        }

        // Player used up buffer, they fail the check.
        if (data.vehicleMorePacketsBuffer < 0) {
            // Increment violation level.
            data.vehicleMorePacketsVL = -data.vehicleMorePacketsBuffer;
            // Execute whatever actions are associated with this check and the violation level and find out if we should
            // cancel the event.
            final ViolationData vd = new ViolationData(this, player, data.vehicleMorePacketsVL, -data.vehicleMorePacketsBuffer, cc.vehicleMorePacketsActions);
            if (debug || vd.needsParameters()) {
                vd.setParameter(ParameterName.PACKETS, Integer.toString(-data.vehicleMorePacketsBuffer));
            }
            if (executeActions(vd).willCancel()){
                newTo = data.vehicleSetBacks.getValidMidTermEntry();
            }
        }
        
        // More than 1 second elapsed, but how many?
        if (data.vehicleMorePacketsLastTime + 1000 < time) {
            final double seconds = (time - data.vehicleMorePacketsLastTime) / 1000.0;
            // For each second, fill the buffer.
            data.vehicleMorePacketsBuffer += packetsPerTimeframe * seconds;
            // If there was a long pause (maybe server lag?), allow buffer to grow up to 100.
            if (seconds > 2) {
                if (data.vehicleMorePacketsBuffer > 100) {
                    data.vehicleMorePacketsBuffer = 100;
                }
            } 
            else if (data.vehicleMorePacketsBuffer > MovingData.vehicleMorePacketsBufferDefault) {
                // Clamp to max value.
                data.vehicleMorePacketsBuffer = MovingData.vehicleMorePacketsBufferDefault;
            }

            // Set the new "last" time.
            data.vehicleMorePacketsLastTime = time;
            // Set the new set back location.
            if (allowSettingSetBack && newTo == null) {
                data.vehicleSetBacks.setMidTermEntry(thisMove.from);
                if (debug) {
                    debug(player, "Update vehicle morepackets set back: " + thisMove.from);
                }
            }
        }
        else if (data.vehicleMorePacketsLastTime > time) {
            // Security check, maybe system time changed.
            data.vehicleMorePacketsLastTime = time;
        }
        return newTo;
    }
}
