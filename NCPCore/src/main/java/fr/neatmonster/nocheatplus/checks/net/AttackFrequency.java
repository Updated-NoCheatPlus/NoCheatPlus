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
package fr.neatmonster.nocheatplus.checks.net;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;

public class AttackFrequency extends Check {

    public AttackFrequency() {
        super(CheckType.NET_ATTACKFREQUENCY);
    }

    /**
     * Checks a player
     * (Checks hasBypass on violation only)
     * 
     * @param player
     * @param time milliseconds
     * @param data
     * @param cc
     * @param pData
     * @return true if successful
     */
    public boolean check(final Player player, final long time, final NetData data, final NetConfig cc, final IPlayerData pData) {
        // Update frequency.
        data.attackFrequencySeconds.add(time, 1f);
        double maxVL = 0.0;
        float maxLimit = 0f;
        String tags = null;
        // TODO: option to normalize the vl / stats to per second? 
        // HALF
        float sum = data.attackFrequencySeconds.bucketScore(0); // HALF
        float limit = cc.attackFrequencyLimitSecondsHalf;
        if (sum - limit > maxVL) {
            maxVL = sum - limit;
            maxLimit = limit;
            tags = "sec_half";
        }
        // ONE (update sum).
        sum += data.attackFrequencySeconds.bucketScore(1);
        limit = cc.attackFrequencyLimitSecondsOne;
        if (sum - limit > maxVL) {
            maxVL = sum - limit;
            maxLimit = limit;
            tags = "sec_one";
        }
        // TWO (update sum).
        sum += data.attackFrequencySeconds.sliceScore(2, 4, 1f);
        limit = cc.attackFrequencyLimitSecondsTwo;
        if (sum - limit > maxVL) {
            maxVL = sum - limit;
            maxLimit = limit;
            tags = "sec_two";
        }
        // FOUR (update sum).
        sum += data.attackFrequencySeconds.sliceScore(4, 8, 1f);
        limit = cc.attackFrequencyLimitSecondsFour;
        if (sum - limit > maxVL) {
            maxVL = sum - limit;
            maxLimit = limit;
            tags = "sec_four";
        }
        // EIGHT (update sum).
        sum += data.attackFrequencySeconds.sliceScore(8, 16, 1f);
        limit = cc.attackFrequencyLimitSecondsEight;
        if (sum - limit > maxVL) {
            maxVL = sum - limit;
            maxLimit = limit;
            tags = "sec_eight";
        }

        if (pData.isDebugActive(CheckType.NET_ATTACKFREQUENCY)) {
            player.sendMessage("AttackFrequency: " + data.attackFrequencySeconds.toLine());
        }

        boolean cancel = false;
        if (maxVL > 0.0) {
            // Trigger a violation.
            final ViolationData vd = new ViolationData(this, player, maxVL, 1.0, cc.attackFrequencyActions);
            if (pData.isDebugActive(type) || vd.needsParameters()) {
                vd.setParameter(ParameterName.PACKETS, Integer.toString((int) sum));
                vd.setParameter(ParameterName.LIMIT, Integer.toString((int) maxLimit));
                vd.setParameter(ParameterName.TAGS, tags);
            }
            cancel = executeActions(vd).willCancel();
            // Feed Improbable.
            if (cc.attackFrequencyImprobableWeight > 0.0f) {
            	TickTask.requestImprobableUpdate(player.getUniqueId(), cc.attackFrequencyImprobableWeight);
            }
        }

        return cancel;
    }

}
