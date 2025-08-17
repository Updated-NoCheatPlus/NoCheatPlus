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
package fr.neatmonster.nocheatplus.checks.blockplace;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * This check verifies if the player isn't throwing items too quickly, like eggs or arrows.
 */
public class Speed extends Check {

    /**
     * Instantiates a new speed check.
     */
    public Speed() {
        super(CheckType.BLOCKPLACE_SPEED);
    }

    /**
     * Checks a player.
     * 
     * @param player
     *            the player
     * @param cc 
     * @param pData
     * @return true, if successful
     */
    public boolean check(final Player player, final BlockPlaceConfig cc, final IPlayerData pData) {

        final BlockPlaceData data = pData.getGenericInstance(BlockPlaceData.class);
        boolean cancel = false;

        // Has the player thrown items too quickly?
        if (data.speedLastTime != 0 && System.currentTimeMillis() - data.speedLastTime < cc.speedInterval) {
            if (data.speedLastRefused) {
                final double difference = cc.speedInterval - System.currentTimeMillis() + data.speedLastTime;

                // They failed, increase this violation level.
                data.speedVL += difference;
                // Execute whatever actions are associated with this check and the violation level and find out if we
                // should cancel the event.
                cancel = executeActions(player, data.speedVL, difference, cc.speedActions).willCancel();
            }
            data.speedLastRefused = true;
        } 
        else {
            // Reward them by lowering their violation level.
            data.speedVL *= 0.9D;
            data.speedLastRefused = false;
        }
        data.speedLastTime = System.currentTimeMillis();
        return cancel;
    }
}
