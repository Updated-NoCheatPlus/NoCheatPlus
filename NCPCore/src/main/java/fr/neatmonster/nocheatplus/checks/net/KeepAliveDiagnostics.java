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

import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Console-only formatting for KeepAliveFrequency diagnostics.
 */
final class KeepAliveDiagnostics {

    private KeepAliveDiagnostics() {}

    static String formatDetail(final Player player, final long packetTime, final long now, final long joinTime,
                               final NetData data, final NetConfig cc, final IPlayerData pData,
                               final float first, final float fullScore, final double vl,
                               final boolean cancel, final String model, final float firstLimit) {
        final long bucketDuration = data.keepAliveFreq.bucketDuration();
        final int buckets = data.keepAliveFreq.numberOfBuckets();
        return new StringBuilder(900)
                .append("[NCP][KeepAliveFrequency][detail] player=").append(player.getName())
                .append(" uuid=").append(player.getUniqueId())
                .append(" client=").append(pData.getClientVersion())
                .append(" summary=keepalive_bucket{first=").append(StringUtil.fdec3.format(first))
                .append(",full=").append(StringUtil.fdec3.format(fullScore))
                .append(",expected=").append(data.keepAliveFreq.numberOfBuckets())
                .append(",cancel=").append(cancel)
                .append('}')
                .append(" packetTime=").append(packetTime)
                .append(" now=").append(now)
                .append(" joinAge=").append(joinTime <= 0L ? -1L : now - joinTime)
                .append(" startupDelay=").append(cc.keepAliveFrequencyStartupDelay)
                .append(" vl=").append(StringUtil.fdec3.format(vl))
                .append(" cancel=").append(cancel)
                .append(" model=").append(model)
                .append(" firstLimit=").append(StringUtil.fdec3.format(firstLimit))
                .append(" firstBucket=").append(StringUtil.fdec3.format(first))
                .append(" fullScore=").append(StringUtil.fdec3.format(fullScore))
                .append(" expectedFull=").append(buckets)
                .append(" bucketDuration=").append(bucketDuration)
                .append(" bucketAge=").append(now - data.keepAliveFreq.lastAccess())
                .append(" lastUpdateAge=").append(now - data.keepAliveFreq.lastUpdate())
                .append(" lag1s=").append(StringUtil.fdec3.format(TickTask.getLag(bucketDuration, true)))
                .append(" lagWindow=").append(StringUtil.fdec3.format(TickTask.getLag(bucketDuration * buckets, true)))
                .append(" buckets=").append(formatBuckets(data, Math.min(6, buckets)))
                .append(" state=").append(data.describeKeepAliveState(now))
                .toString();
    }

    private static String formatBuckets(final NetData data, final int limit) {
        final StringBuilder builder = new StringBuilder(80);
        builder.append('[');
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(StringUtil.fdec3.format(data.keepAliveFreq.bucketScore(i)));
        }
        if (limit < data.keepAliveFreq.numberOfBuckets()) {
            builder.append(",...");
        }
        builder.append(']');
        return builder.toString();
    }
}
