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

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.TickTask;


/**
 * Check frequency of (pos/look/) flying packets, disregarding packet content.
 * 
 * @author asofold
 *
 */
public class FlyingFrequency extends Check {

    public FlyingFrequency() {
        super(CheckType.NET_FLYINGFREQUENCY);
    }

    /**
     * Always update data, check bypass on violation only.
     * 
     * @param player
     * @param packetData
     * @param time
     * @param data
     * @param cc
     * @param pData
     * @return
     */
    public boolean check(final Player player, final DataPacketFlying packetData, final long time, 
                         final NetData data, final NetConfig cc, final IPlayerData pData) {
        boolean cancel = false;
        data.flyingFrequencyAll.add(time, 1f);
        final float allScore = data.flyingFrequencyAll.score(1f);
        final float fullTime = data.flyingFrequencyAll.bucketDuration() * data.flyingFrequencyAll.numberOfBuckets();
        float amount = allScore / cc.flyingFrequencySeconds;
        // Check if packets are above frequency limit
        if (amount > cc.flyingFrequencyPPS) {
            // Scale according to current lag factor
            amount /= TickTask.getLag((long)fullTime);
            // Re-check if packets are still above limit.
            if (amount > cc.flyingFrequencyPPS) {
                double violation = amount - cc.flyingFrequencyPPS;
                cancel = executeActions(player, violation, 1.0 / cc.flyingFrequencySeconds, cc.flyingFrequencyActions).willCancel();
            }
        }
        return cancel;
    }
}
