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

import org.bukkit.Location;

import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * Console-only formatting for net packet diagnostics.
 */
final class NetDiagnostics {

    private NetDiagnostics() {}

    static String formatKeepAliveState(final NetData data, final long now) {
        final DataPacketFlying latest = data.getCurrentFlyingPacket();
        return new StringBuilder(500)
                .append("packetTime=").append(data.keepAlivePacketTime)
                .append(",packetAge=").append(data.keepAlivePacketTime <= 0L ? -1L : now - data.keepAlivePacketTime)
                .append(",previousPacketAge=").append(data.keepAlivePreviousPacketTime <= 0L ? -1L : now - data.keepAlivePreviousPacketTime)
                .append(",delta=").append(data.keepAlivePacketDelta)
                .append(",idAvailable=").append(data.keepAlivePacketIdAvailable)
                .append(",idType=").append(data.keepAlivePacketIdType)
                .append(",id=").append(data.keepAlivePacketIdAvailable ? Long.toString(data.keepAlivePacketId) : "none")
                .append(",previousId=").append(data.keepAlivePreviousPacketIdAvailable ? Long.toString(data.keepAlivePreviousPacketId) : "none")
                .append(",duplicateId=").append(data.keepAliveDuplicateId)
                .append(",expectedResponse=").append(data.keepAliveExpectedResponse)
                .append(",expectedAge=").append(data.keepAliveExpectedResponseAge)
                .append(",outgoingSeen=").append(data.keepAliveOutgoingSeen)
                .append(",outgoingAge=").append(data.keepAliveOutgoingPacketTime <= 0L ? -1L : now - data.keepAliveOutgoingPacketTime)
                .append(",outgoingId=").append(data.keepAliveOutgoingPacketIdAvailable ? Long.toString(data.keepAliveOutgoingPacketId) : "none")
                .append(",pendingOutgoing=").append(data.keepAlivePendingRequests)
                .append(",fieldCounts=long:").append(data.keepAlivePacketLongCount).append("/int:").append(data.keepAlivePacketIntCount)
                .append(",outgoingFieldCounts=long:").append(data.keepAliveOutgoingPacketLongCount).append("/int:").append(data.keepAliveOutgoingPacketIntCount)
                .append(",async=").append(data.keepAlivePacketAsync)
                .append(",thread=").append(data.keepAlivePacketThread)
                .append(",outgoingAsync=").append(data.keepAliveOutgoingPacketAsync)
                .append(",outgoingThread=").append(data.keepAliveOutgoingPacketThread)
                .append(",lastSharedKeepAliveAge=").append(data.lastKeepAliveTime <= 0L ? -1L : now - data.lastKeepAliveTime)
                .append(",teleportAges=event:").append(data.getMovingTeleportGraceAge(now))
                .append(",outgoing:").append(data.getOutgoingPositionGraceAge(now))
                .append(",serverJump:").append(data.getServerPositionJumpGraceAge(now))
                .append(",command:").append(data.getTeleportCommandGraceAge(now))
                .append(",latestFlying=").append(formatLatestFlying(latest, now))
                .append(",matchedMoveSeq=").append(data.getLastMatchedMoveToSequence())
                .append(",teleportQueue=").append(data.teleportQueue.getDebugState(now))
                .toString();
    }

    static String formatTeleportResyncState(final long movingDataKey, final long requestTime,
                                            final long appliedKey, final String reason,
                                            final double x, final double y, final double z,
                                            final float yaw, final float pitch,
                                            final int droppedPackets, final Location knownLocation,
                                            final long now) {
        if (movingDataKey <= 0L) {
            return "none";
        }
        return new StringBuilder(150)
                .append("{key=").append(movingDataKey)
                .append(",age=").append(requestTime <= 0L ? -1L : now - requestTime)
                .append(",applied=").append(appliedKey == movingDataKey)
                .append(",reason=").append(reason)
                .append(",target=").append(formatStoredLocation(x, y, z, yaw, pitch))
                .append(",targetToKnown=").append(formatStoredDistance(requestTime, x, y, z, knownLocation))
                .append(",droppedPackets=").append(droppedPackets)
                .append('}')
                .toString();
    }

    static String formatTeleportResyncApplied(final String playerName, final String action, final String reason,
                                              final double x, final double y, final double z,
                                              final float yaw, final float pitch) {
        return new StringBuilder(160)
                .append("[NCP][NetMoving][resync] player=").append(playerName)
                .append(" action=").append(action)
                .append(" reason=").append(reason == null ? "unknown" : reason)
                .append(" target=").append(formatStoredLocation(x, y, z, yaw, pitch))
                .toString();
    }

    static String formatTeleportStaleDrop(final String playerName, final String action, final String reason,
                                          final int droppedPackets, final int loggedCount,
                                          final int queueSizeBeforeClear,
                                          final double targetX, final double targetY, final double targetZ,
                                          final float targetYaw, final float targetPitch,
                                          final long targetTime, final Location knownLocation,
                                          final long serverJumpTime, final double serverJumpFromX,
                                          final double serverJumpFromY, final double serverJumpFromZ,
                                          final long commandTime, final double commandX,
                                          final double commandY, final double commandZ,
                                          final Location packetLocation, final DataPacketFlying packetData,
                                          final long now) {
        return new StringBuilder(420)
                .append("[NCP][NetMoving][stale-drop] player=").append(playerName)
                .append(" action=").append(action)
                .append(" reason=").append(reason == null ? "unknown" : reason)
                .append(" droppedPackets=").append(droppedPackets)
                .append(" loggedCount=").append(loggedCount)
                .append(" queueSizeBeforeClear=").append(queueSizeBeforeClear)
                .append(" target=").append(formatStoredLocation(targetX, targetY, targetZ, targetYaw, targetPitch))
                .append(" targetToKnown=").append(formatStoredDistance(targetTime, targetX, targetY, targetZ, knownLocation))
                .append(" packetToServerJumpFrom=").append(formatStoredDistance(serverJumpTime,
                        serverJumpFromX, serverJumpFromY, serverJumpFromZ, packetLocation))
                .append(" packetToCommand=").append(formatStoredDistance(commandTime, commandX, commandY, commandZ, packetLocation))
                .append(" packet=").append(formatPacket(packetData, now))
                .toString();
    }

    static String formatStoredLocation(final double x, final double y, final double z, final float yaw, final float pitch) {
        return new StringBuilder(90)
                .append("x=").append(StringUtil.fdec3.format(x))
                .append(",y=").append(StringUtil.fdec3.format(y))
                .append(",z=").append(StringUtil.fdec3.format(z))
                .append(",yaw=").append(StringUtil.fdec3.format(yaw))
                .append(",pitch=").append(StringUtil.fdec3.format(pitch))
                .toString();
    }

    static String formatStoredDistance(final long storedTime, final double x, final double y, final double z, final Location loc) {
        if (storedTime <= 0L || loc == null) {
            return "none";
        }
        final double xDiff = loc.getX() - x;
        final double yDiff = loc.getY() - y;
        final double zDiff = loc.getZ() - z;
        return new StringBuilder(80)
                .append("h=").append(StringUtil.fdec3.format(Math.sqrt(xDiff * xDiff + zDiff * zDiff)))
                .append("/y=").append(StringUtil.fdec3.format(Math.abs(yDiff)))
                .append("/total=").append(StringUtil.fdec3.format(Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff)))
                .toString();
    }

    static String formatPacket(final DataPacketFlying packetData, final long now) {
        if (packetData == null) {
            return "none";
        }
        return new StringBuilder(120)
                .append("seq=").append(packetData.getSequence())
                .append(",age=").append(now - packetData.time)
                .append(",type=").append(packetData.getSimplifiedContentType())
                .append(",tracked=").append(packetData.isTracked())
                .append(",ground=").append(packetData.onGround)
                .append(",hCollision=").append(packetData.horizontalCollision)
                .append(",hasPos=").append(packetData.hasPos)
                .toString();
    }

    private static String formatLatestFlying(final DataPacketFlying latest, final long now) {
        if (latest == null) {
            return "none";
        }
        return new StringBuilder(160)
                .append("seq=").append(latest.getSequence())
                .append(",age=").append(now - latest.time)
                .append(",tracked=").append(latest.isTracked())
                .append(",type=").append(latest.getSimplifiedContentType())
                .append(",ground=").append(latest.onGround)
                .append(",hcollide=").append(latest.horizontalCollision)
                .append(",hasPos=").append(latest.hasPos)
                .append(",hasLook=").append(latest.hasLook)
                .toString();
    }
}
