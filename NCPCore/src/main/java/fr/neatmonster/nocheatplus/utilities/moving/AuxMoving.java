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
package fr.neatmonster.nocheatplus.utilities.moving;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveInfo;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.feature.IRegisterAsGenericInstance;

/**
 * Non-static utility, (to be) registered as generic instance.
 * 
 * @author asofold
 *
 */
public class AuxMoving implements IRegisterAsGenericInstance {

    // TODO: Move more non-static stuff here.

    /**
     * Unused instances.<br>
     * Might be better due to cascading events in case of actions or plugins doing strange things.
     */
    private final List<PlayerMoveInfo> parkedPlayerMoveInfo = new ArrayList<PlayerMoveInfo>(10);

    /**
     * Unused instances.<br>
     * Might be better due to cascading events in case of actions or plugins doing strange things.
     */
    private final List<VehicleMoveInfo> parkedVehicleMoveInfo = new ArrayList<VehicleMoveInfo>(10);

    private final IGenericInstanceHandle<MCAccess> mcAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(MCAccess.class);

    public synchronized PlayerMoveInfo usePlayerMoveInfo() {
        if (parkedPlayerMoveInfo.isEmpty()) {
            return new PlayerMoveInfo(mcAccess);
        }
        else {
            return parkedPlayerMoveInfo.remove(parkedPlayerMoveInfo.size() - 1);
        }
    }

    /**
     * Cleanup and add to parked.
     * @param moveInfo
     */
    public synchronized void returnPlayerMoveInfo(final PlayerMoveInfo moveInfo) {
        moveInfo.cleanup();
        parkedPlayerMoveInfo.add(moveInfo);
    }

    public synchronized VehicleMoveInfo useVehicleMoveInfo() {
        if (parkedVehicleMoveInfo.isEmpty()) {
            return new VehicleMoveInfo(mcAccess);
        }
        else {
            return parkedVehicleMoveInfo.remove(parkedVehicleMoveInfo.size() - 1);
        }
    }

    /**
     * Cleanup and add to parked.
     * @param moveInfo
     */
    public synchronized void returnVehicleMoveInfo(final VehicleMoveInfo moveInfo) {
        moveInfo.cleanup();
        parkedVehicleMoveInfo.add(moveInfo);
    }

    /**
     * Determine "some jump amplifier": 1 is jump boost, 2 is jump boost II. <br>
     * NOTE: This is not the original amplifier value (use mcAccess for that).
     * @param mcPlayer
     * @return
     */
    public synchronized final double getJumpAmplifier(final Player player) {
        return MovingUtil.getJumpAmplifier(player, mcAccess.getHandle());
    }

    /**
     * Convenience method to do data.resetPositions, data.adjustLiftOffEnvelope and
     * data.adjustMediumProperties, wrapping given loc with a PlayerLocation
     * instance.
     * 
     * @param player
     * @param loc
     * @param data
     * @param cc
     */
    public synchronized void resetPositionsAndMediumProperties(final Player player, final Location loc, final MovingData data, final MovingConfig cc) {
        final PlayerMoveInfo moveInfo = usePlayerMoveInfo();
        moveInfo.set(player, loc, null, cc.yOnGround);
        data.resetPlayerPositions(moveInfo.from);
        data.adjustLiftOffEnvelope(moveInfo.from);
        data.adjustMediumProperties(loc, cc, player, data.playerMoves.getCurrentMove());
        returnPlayerMoveInfo(moveInfo);
    }

    /**
     * Convenience method for calling data.resetVehiclePositions (analogous to
     * resetPosisionsAndMediumProperties for players), wrapping vehicleLocation
     * with a RichEntityLocation(VehicleMoveInfo).
     * 
     * @param vehicle
     * @param vehicleLocation
     * @param data
     * @param cc
     */
    public synchronized void resetVehiclePositions(final Entity vehicle, final Location vehicleLocation, final MovingData data, final MovingConfig cc) {
        final VehicleMoveInfo vMoveInfo = useVehicleMoveInfo();
        vMoveInfo.set(vehicle, vehicleLocation, null, cc.yOnGround);
        data.resetVehiclePositions(vMoveInfo.from);
        returnVehicleMoveInfo(vMoveInfo);
    }

    /**
     * Clear parked MovingInfo instances. Called on reload and data removal and
     * setMCAccess.
     */
    public synchronized void clear() {
        // Call cleanup on all parked info, just in case.
        // (Players)
        for (final PlayerMoveInfo info : parkedPlayerMoveInfo) {
            info.cleanup();
        }
        parkedPlayerMoveInfo.clear();
        // (Vehicles)
        for (final VehicleMoveInfo info : parkedVehicleMoveInfo) {
            info.cleanup();
        }
        parkedVehicleMoveInfo.clear();
    }

}
