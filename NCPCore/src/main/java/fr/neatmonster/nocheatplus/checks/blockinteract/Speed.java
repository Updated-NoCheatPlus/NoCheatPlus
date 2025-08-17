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
package fr.neatmonster.nocheatplus.checks.blockinteract;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * The speed check will identify players who spam interactions to the server.
 */
public class Speed extends Check {
    
    /**
     * Instantiates a new speed check
     */
    public Speed() {
        super(CheckType.BLOCKINTERACT_SPEED);
    }
    
    /**
     * Checks a player
     * 
     * @param player
     * @param data
     * @param cc
     * @return true if failed.
     */
    public boolean check(final Player player, final BlockInteractData data, final BlockInteractConfig cc) {

        final long time = System.currentTimeMillis();
        boolean cancel = false;
        if (time < data.speedTime || time > data.speedTime + cc.speedInterval) {
            data.speedTime = time;
            data.speedCount = 0;
        }

        // Increase count with each interaction
        data.speedCount++;
        if (data.speedCount > cc.speedLimit) {
            // Lag correction
            final int correctedCount = (int) ((double) data.speedCount / TickTask.getLag(time - data.speedTime, true));
            if (correctedCount > cc.speedLimit) {
                data.speedVL ++;
                if (executeActions(player, data.speedVL, 1, cc.speedActions).willCancel()){
                    cancel = true;
                }
            }
            // keep vl. // Not sure either.
            else data.addPassedCheck(this.type); 
        }
        else {
            data.speedVL *= 0.99;
            data.addPassedCheck(this.type);
        }
        return cancel;
    }
}