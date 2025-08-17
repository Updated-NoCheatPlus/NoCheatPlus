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
import fr.neatmonster.nocheatplus.players.IPlayerData;

public class KeepAliveFrequency extends Check {

    public KeepAliveFrequency() {
        super(CheckType.NET_KEEPALIVEFREQUENCY);
    }
    
    /**
     * Checks hasBypass on violation only.
     * @param player
     * @param time
     * @param data
     * @param cc
     * @return If to cancel.
     */
    public boolean check(final Player player, final long time, final NetData data, final NetConfig cc, final IPlayerData pData) {
        data.keepAliveFreq.add(time, 1f);
        final float first = data.keepAliveFreq.bucketScore(0);
        final long now = System.currentTimeMillis();
        
        if (now > pData.getLastJoinTime() && pData.getLastJoinTime() + cc.keepAliveFrequencyStartupDelay * 1000 < now) {
            return false;
        }
        
        if (first > 1f) {
            // Trigger a violation.
            final double vl = Math.max(first - 1f, data.keepAliveFreq.score(1f) - data.keepAliveFreq.numberOfBuckets());
            if (executeActions(player, vl, 1.0, cc.keepAliveFrequencyActions).willCancel()) {
                return true;
            }
        }
        return false;
    }
}
