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

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Task for scheduling a passenger set back. Resets the vehicleSetPassengerTaskId in
 * the MovingData for the player.
 * 
 *
 */
public class VehicleSetPassengerTask implements Runnable{
    private final Entity vehicle;
    private final Player player;
    private final IHandle<IEntityAccessVehicle> handleVehicle;

    /**
     * 
     * @param handleVehicle
     * @param vehicle
     * @param player
     *            The player because of whom this teleport is happening. This
     *            should be the player in charge of steering, but that needn't
     *            be the case in future.
     */
    public VehicleSetPassengerTask(IHandle<IEntityAccessVehicle> handleVehicle, Entity vehicle, Player player) {
        this.vehicle = vehicle;
        this.player = player;
        this.handleVehicle = handleVehicle;
    }

    @Override
    public void run() {
        final IPlayerData pData = DataManager.getPlayerData(player);
        final MovingData data = pData.getGenericInstance(MovingData.class);
        data.vehicleSetPassengerTaskId = null;
        try {
            // TODO: Distance?
            //final double dist = TrigUtil.distance(player.getLocation(), vehicle.getLocation());
            if (player.getWorld() != vehicle.getWorld()) return;
            if (!handleVehicle.getHandle().addPassenger(player, vehicle)) {
                // TODO: What?
            }
        }
        catch(Throwable t){
            StaticLog.logSevere(t);
        }
    }

}
