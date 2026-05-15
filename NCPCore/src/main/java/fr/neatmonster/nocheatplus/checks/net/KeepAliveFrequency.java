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
import fr.neatmonster.nocheatplus.utilities.CheckUtils;

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
        final long now = System.currentTimeMillis();
        final long joinTime = pData.getLastJoinTime();
        if (joinTime > 0L && now < joinTime + cc.keepAliveFrequencyStartupDelay) {
            return false;
        }
        if (data.keepAliveExpectedResponse && !data.keepAliveDuplicateId) {
            return false;
        }
        data.keepAliveFreq.add(time, 1f);
        final float first = data.keepAliveFreq.bucketScore(0);
        
        if (first > 1f) {
            // Trigger a violation.
            final float fullScore = data.keepAliveFreq.score(1f);
            if (isModeledBoundaryBurst(data, fullScore, first)) {
                return false;
            }
            final double vl = Math.max(first - getFirstBucketViolationLimit(data), fullScore - data.keepAliveFreq.numberOfBuckets());
            final boolean cancel = executeActions(player, vl, 1.0, cc.keepAliveFrequencyActions).willCancel();
            if (CheckUtils.shouldLogDebugToConsole()) {
                // Diagnostic info: bucket details separate duplicate packets from normal boundary timing.
                logConsoleDetails(player, time, now, joinTime, data, cc, pData, first, fullScore, vl,
                        cancel, describeKeepAliveModel(data, fullScore, first), getFirstBucketViolationLimit(data));
            }
            return cancel;
        }
        return false;
    }

    private boolean isModeledBoundaryBurst(final NetData data, final float fullScore, final float first) {
        // KeepAlive model: if outgoing tracking is unavailable, only treat small monotonic id bursts as timing leftovers.
        if (data.keepAliveDuplicateId || fullScore > data.keepAliveFreq.numberOfBuckets() + 2f) {
            return false;
        }
        // Folia/netty can timestamp two adjacent valid replies in the same millisecond; do not cancel a single pair.
        return first <= 2f || isUntrackedMonotonicBurst(data, fullScore, first);
    }

    private boolean isUntrackedMonotonicBurst(final NetData data, final float fullScore, final float first) {
        if (data.keepAliveOutgoingSeen || fullScore > data.keepAliveFreq.numberOfBuckets()) {
            return false;
        }
        if (!data.keepAlivePacketIdAvailable || !data.keepAlivePreviousPacketIdAvailable) {
            return false;
        }
        if (data.keepAlivePacketId <= data.keepAlivePreviousPacketId) {
            return false;
        }
        return first <= getFallbackFirstBucketLimit(data);
    }

    private float getFirstBucketViolationLimit(final NetData data) {
        if (data.keepAliveDuplicateId) {
            return 1f;
        }
        if (!data.keepAliveOutgoingSeen
                && data.keepAlivePacketIdAvailable
                && data.keepAlivePreviousPacketIdAvailable
                && data.keepAlivePacketId > data.keepAlivePreviousPacketId) {
            return getFallbackFirstBucketLimit(data);
        }
        return 2f;
    }

    private float getFallbackFirstBucketLimit(final NetData data) {
        return Math.max(3f, Math.min(6f, data.keepAliveFreq.numberOfBuckets() / 4f));
    }

    private String describeKeepAliveModel(final NetData data, final float fullScore, final float first) {
        if (data.keepAliveDuplicateId) {
            return "duplicate-id";
        }
        if (data.keepAliveOutgoingSeen) {
            return "unmatched-outgoing";
        }
        if (isUntrackedMonotonicBurst(data, fullScore, first)) {
            return "untracked-monotonic-burst";
        }
        return "bucket-frequency";
    }

    private void logConsoleDetails(final Player player, final long packetTime, final long now, final long joinTime,
                                   final NetData data, final NetConfig cc, final IPlayerData pData,
                                   final float first, final float fullScore, final double vl,
                                   final boolean cancel, final String model, final float firstLimit) {
        try {
            player.getServer().getLogger().info(KeepAliveDiagnostics.formatDetail(player, packetTime, now, joinTime,
                    data, cc, pData, first, fullScore, vl, cancel, model, firstLimit));
        }
        catch (Throwable ignored) {}
    }
}
