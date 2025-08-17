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

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * A check used to verify if the player isn't placing blocks too quickly.
 */
public class FastPlace extends Check {

    /**
     * Instantiates a new fast place check.
     */
    public FastPlace() {
        super(CheckType.BLOCKPLACE_FASTPLACE);
    }

    /**
     * Checks a player.
     * 
     * @param player
     *            the player
     * @param block
     *            the block
     * @param tick
     * @param data
     * @param cc
     * @param pData
     * @return true, if successful
     */
    public boolean check(final Player player, final Block block, final int tick, final BlockPlaceData data, 
                         final BlockPlaceConfig cc, final IPlayerData pData) {

        data.fastPlaceBuckets.add(System.currentTimeMillis(), 1f);
        final boolean lag = pData.getCurrentWorldData().shouldAdjustToLag(type);
        // Full period frequency.
        final float fullScore = data.fastPlaceBuckets.score(1f);

        // Short term limit.
        if (tick < data.fastPlaceShortTermTick) {
            // TickTask got reset.
            data.fastPlaceShortTermTick = tick;
            data.fastPlaceShortTermCount = 1;
        }
        else if (tick - data.fastPlaceShortTermTick < cc.fastPlaceShortTermTicks) {
            // Account for server side lag.
            if (!lag || TickTask.getLag(50L * (tick - data.fastPlaceShortTermTick), true) < 1.2f) {
                // Within range, add.
                data.fastPlaceShortTermCount ++;
            }
            else {
                // Too much lag, reset.
                data.fastPlaceShortTermTick = tick;
                data.fastPlaceShortTermCount = 1;
            }
        }
        else { 
            data.fastPlaceShortTermTick = tick;
            data.fastPlaceShortTermCount = 1;
        }

        // Find out if one of both are violations:
        final float fullViolation;
        if (fullScore > cc.fastPlaceLimit) {
            // Account for server side lag.
            fullViolation = lag ? fullScore / TickTask.getLag(data.fastPlaceBuckets.bucketDuration() * data.fastPlaceBuckets.numberOfBuckets(), true) - cc.fastPlaceLimit
                                : fullScore - cc.fastPlaceLimit;
        }
        else fullViolation = 0;
        final float shortTermViolation = data.fastPlaceShortTermCount - cc.fastPlaceShortTermLimit; 
        final float violation = Math.max(fullViolation, shortTermViolation);
        boolean cancel = false;

        if (violation > 0.0) {
            final double change = (double) violation;
            data.fastPlaceVL += change;
            cancel = executeActions(player, data.fastPlaceVL, change, cc.fastPlaceActions).willCancel();
        }
        else if (data.fastPlaceVL > 0D && fullScore < cc.fastPlaceLimit * .75) {
            data.fastPlaceVL *= 0.95;
        }
        return cancel;
    }
}