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

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LocationData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerKeyboardInput;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketFlying;
import fr.neatmonster.nocheatplus.checks.net.model.DataPacketInput;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.math.TrigUtil;

/**
 * Misc. checks related to flying packets.
 * Currently only has a simplistic check for extreme moves.
 */
public class Moving extends Check {

    public Moving() {
        super(CheckType.NET_MOVING);
    }

    /**
     * Checks a player
     * 
     * @param player
     * @param packetData
     * @param data
     * @param cc
     * @param pData
     * @param cc
     * @return True, if to cancel the event
     */
    public boolean check(final Player player, final DataPacketFlying packetData, final NetData data, final NetConfig cc, 
                         final IPlayerData pData, final Plugin plugin) {
        // TODO: This will trigger if the client is waiting for chunks to load (Slow fall, on join or after a teleport)
        // TODO Replace this check with a packet-sync thing: Sync flying packets with a PlayerMoveEvent, flag incoming packets which don't fire any move event.
        boolean cancel = false;
        final long now = System.currentTimeMillis();
        final boolean debug = pData.isDebugActive(CheckType.NET_MOVING);
        final List<String> tags = new ArrayList<String>();
        if (now > pData.getLastJoinTime() && pData.getLastJoinTime() + 10000 > now) {
            tags.add("login_grace");
        	return false;
        }
        if (player.isDead()) {
            tags.add("dead_grace");
            data.movingVL *= 0.5;
            return false;
        }
        if (packetData != null && packetData.hasPos) {
            final MovingData mData = pData.getGenericInstance(MovingData.class);
            /** Actual Location on the server */
            final Location useLoc = new Location(null, 0, 0, 0);
            final Location rawKnownLocation = player.getLocation(useLoc);
            if (rawKnownLocation == null || rawKnownLocation.getWorld() == null) {
                // Folia compatibility: a disconnecting player can leave the temporary Location without a world.
                tags.add("known_world_null_grace");
                useLoc.setWorld(null);
                return false;
            }
            // Folia/packet safety: keep packet-thread teleport models away from mutable temporary Location state.
            final Location knownLocation = LocUtil.clone(rawKnownLocation);
            useLoc.setWorld(null);
            data.recordMovingKnownLocation(knownLocation, now);
            /** Claimed Location sent by the client */
            final Location packetLocation = new Location(null, packetData.getX(), packetData.getY(), packetData.getZ());
            //final double distanceSq = TrigUtil.distanceSquared(knownLocation, packetLocation);
            final double yDistance = Math.abs(knownLocation.getY() - packetLocation.getY());
            final double hDistance = TrigUtil.xzDistance(knownLocation, packetLocation);
            final double distance = TrigUtil.distance(knownLocation, packetLocation);
            final boolean extremeMove = yDistance > 100.0 || distance > 100.0;

            final boolean teleportGrace = data.isWithinMovingTeleportGrace(now);
            final boolean outgoingPositionGrace = data.isWithinOutgoingPositionGrace(now, knownLocation);
            final boolean serverPositionJumpModel = data.isWithinServerPositionJumpStalePacketModel(now,
                    knownLocation, packetLocation, packetData);
            final boolean teleportCommandModel = data.isWithinTeleportCommandStalePacketModel(now,
                    knownLocation, packetLocation, packetData);
            final boolean teleportResyncStalePacketDropModel = (serverPositionJumpModel || teleportCommandModel)
                    && data.isWithinTeleportResyncStalePacketDropModel(now, knownLocation, packetLocation, packetData);
            final boolean expectedOutgoingPositionGrace = extremeMove && data.consumeExpectedOutgoingPosition(packetData);
            final boolean serverPositionJumpRecoveryGrace = extremeMove
                    && !serverPositionJumpModel
                    && !teleportCommandModel
                    && isServerPositionJumpRecoveryContext(mData)
                    && data.consumeServerPositionJumpRecoveryGrace(now, knownLocation);
            // NetMoving compatibility: match extreme packets against teleport/server-position history before flagging.
            if (extremeMove && (teleportGrace || outgoingPositionGrace || expectedOutgoingPositionGrace
                    || serverPositionJumpModel || serverPositionJumpRecoveryGrace || teleportCommandModel)) {
                if (teleportGrace) {
                    tags.add("teleport_grace");
                }
                if (outgoingPositionGrace) {
                    tags.add("outgoing_position_grace");
                }
                if (expectedOutgoingPositionGrace) {
                    tags.add("expected_outgoing_position_grace");
                }
                if (serverPositionJumpModel) {
                    tags.add("server_position_jump_stale_packet_model");
                }
                if (serverPositionJumpRecoveryGrace) {
                    tags.add("server_position_jump_recovery_grace");
                }
                if (teleportCommandModel) {
                    tags.add("teleport_command_stale_packet_model");
                }
                if (serverPositionJumpModel || teleportCommandModel) {
                    tags.add("teleport_resync_history_reset");
                }
                if (teleportResyncStalePacketDropModel) {
                    tags.add("teleport_resync_stale_packet_drop");
                }
                if (CheckUtils.shouldLogDebugToConsole()
                        && !(serverPositionJumpModel || teleportCommandModel)) {
                    final String reason = serverPositionJumpModel || teleportCommandModel ? "model" : "grace";
                    logConsoleDetails(reason, tags, player, packetData, knownLocation, packetLocation, hDistance, yDistance,
                            distance, data, mData, pData, now);
                }
                if (serverPositionJumpModel || teleportCommandModel) {
                    final String reason = serverPositionJumpModel ? "server_position_jump_stale_packet_model"
                            : "teleport_command_stale_packet_model";
                    // Teleport model: accepted stale packets are deleted from packet history, not kept as movement data.
                    if (teleportResyncStalePacketDropModel) {
                        data.acceptMovingTeleportStalePacketContinuation(player, knownLocation, packetLocation,
                                packetData, now, reason);
                    }
                    else {
                        data.acceptMovingTeleportResync(player, plugin, mData, knownLocation, packetLocation,
                                packetData, now, reason);
                    }
                }
                data.movingVL *= 0.98;
                return false;
            }

            // 100 it's the minimum [Math.max(100, config distance)]distance for the 'moved too quickly' check to fire
            // See PlayerConnection.java
            if (extremeMove/*distanceSq > 100.0 || hDistance > 100.0*/) {
                data.movingVL++ ;
                tags.add("invalid_pos");
                // Diagnostic info: separate net extreme-move flags from teleport/grace branches.
                tags.add(0, "subcheck_netmoving_extreme_move");
                if (CheckUtils.shouldLogDebugToConsole()) {
                    logConsoleDetails("violation", tags, player, packetData, knownLocation, packetLocation, hDistance, yDistance,
                            distance, data, mData, pData, now);
                }
                final ViolationData vd = new ViolationData(this, player, data.movingVL, 1.0, cc.movingActions);
                if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                cancel = executeActions(vd).willCancel();
            }
            else {
                data.movingVL *= 0.98;
            }
        }

        if (debug) {
            final StringBuilder builder = new StringBuilder(500);
            if (packetData != null && packetData.hasPos) {
                final Location packetLocation = new Location(null, packetData.getX(), packetData.getY(), packetData.getZ());
                final Location serverLocation = player.getLocation();
                builder.append(CheckUtils.getLogMessagePrefix(player, type));
                builder.append("\nPacket location: " + LocUtil.simpleFormat(packetLocation));
                builder.append("\nServer location: " + LocUtil.simpleFormat(serverLocation));
                builder.append("\nDeltas: h= " + TrigUtil.distance(serverLocation, packetLocation) + ", y= " + Math.abs(serverLocation.getY() - packetLocation.getY()));
            }
            else {
            	builder.append("Empty packet (no position)");
            }
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.TRACE_FILE, builder.toString());
        }
        return cancel;
    }

    private boolean isServerPositionJumpRecoveryContext(final MovingData data) {
        // False-positive tuning: death/respawn and portal/server jumps can leave move history invalid for one packet.
        final PlayerMoveData currentMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        return data.joinOrRespawn
                || !data.hasSetBack()
                || !currentMove.toIsValid
                || !lastMove.toIsValid;
    }

    private void logConsoleDetails(final String reason, final List<String> tags,
                                   final Player player, final DataPacketFlying packetData,
                                   final Location knownLocation, final Location packetLocation,
                                   final double hDistance, final double yDistance, final double distance,
                                   final NetData data, final MovingData mData, final IPlayerData pData,
                                   final long now) {
        try {
            final String joinedTags = StringUtil.join(tags, "+");
            player.getServer().getLogger().info(new StringBuilder(700)
                    .append("[NCP][NetMoving][detail] player=").append(player.getName())
                    .append(" reason=").append(reason)
                    .append(" subcheck=").append("violation".equals(reason) ? "NETMOVING_EXTREME_MOVE"
                            : "model".equals(reason) ? "NETMOVING_TELEPORT_RESYNC_MODEL" : "NETMOVING_GRACE")
                    .append(" summary=net_moving{reason=").append(reason)
                    .append(",h=").append(StringUtil.fdec3.format(hDistance))
                    .append(",y=").append(StringUtil.fdec3.format(yDistance))
                    .append(",total=").append(StringUtil.fdec3.format(distance))
                    .append(",tags=").append(joinedTags.isEmpty() ? "none" : joinedTags)
                    .append('}')
                    .append(" uuid=").append(player.getUniqueId())
                    .append(" client=").append(pData.getClientVersion())
                    .append(" now=").append(now)
                    .append(" joinAge=").append(now - pData.getLastJoinTime())
                    .append(" keepAliveAge=").append(data.lastKeepAliveTime <= 0L ? -1L : now - data.lastKeepAliveTime)
                    .append(" movingVL=").append(StringUtil.fdec3.format(data.movingVL))
                    .append(" teleportGraceAge=").append(data.getMovingTeleportGraceAge(now))
                    .append(" outgoingGraceAge=").append(data.getOutgoingPositionGraceAge(now))
                    .append(" known=").append(LocUtil.simpleFormat(knownLocation))
                    .append(" packet=").append(LocUtil.simpleFormat(packetLocation))
                    .append(" hDist=").append(StringUtil.fdec3.format(hDistance))
                    .append(" yDist=").append(StringUtil.fdec3.format(yDistance))
                    .append(" distance=").append(StringUtil.fdec3.format(distance))
                    .append(" vector=known-packet(").append(formatVector(knownLocation.getX() - packetLocation.getX(),
                            knownLocation.getY() - packetLocation.getY(), knownLocation.getZ() - packetLocation.getZ())).append(')')
                    .append(" packetMeta=").append(formatPacket(packetData, now))
                    .append(" tags=").append(joinedTags)
                    .toString());
            player.getServer().getLogger().info(new StringBuilder(900)
                    .append("[NCP][NetMoving][teleport] player=").append(player.getName())
                    .append(" reason=").append(reason)
                    .append(" ").append(data.describeMovingTeleportState(now, knownLocation, packetLocation, packetData))
                    .toString());
            player.getServer().getLogger().info(new StringBuilder(1100)
                    .append("[NCP][NetMoving][model] player=").append(player.getName())
                    .append(" reason=").append(reason)
                    .append(" playerState=").append(formatPlayerState(player))
                    .append(" movingData=").append(formatMovingData(mData, knownLocation))
                    .append(" input=").append(formatKeyboardInput(mData.input))
                    .append(" packetModel=").append(formatPacketModel(data, packetData, knownLocation, packetLocation, now))
                    .toString());
        }
        catch (Throwable ignored) {}
    }

    private String formatPlayerState(final Player player) {
        final Vector velocity = player.getVelocity();
        return new StringBuilder(240)
                .append("world=").append(player.getWorld() == null ? "null" : player.getWorld().getName())
                .append(",gameMode=").append(player.getGameMode())
                .append(",dead=").append(player.isDead())
                .append(",health=").append(StringUtil.fdec3.format(player.getHealth()))
                .append(",food=").append(player.getFoodLevel())
                .append(",sprint=").append(player.isSprinting())
                .append(",sneak=").append(player.isSneaking())
                .append(",fly=").append(player.isFlying())
                .append(",allowFlight=").append(player.getAllowFlight())
                .append(",vehicle=").append(player.isInsideVehicle())
                .append(",walkSpeed=").append(StringUtil.fdec3.format(player.getWalkSpeed()))
                .append(",fall=").append(StringUtil.fdec3.format(player.getFallDistance()))
                .append(",velocity=").append(formatVector(velocity.getX(), velocity.getY(), velocity.getZ()))
                .toString();
    }

    private String formatMovingData(final MovingData data, final Location ref) {
        final StringBuilder builder = new StringBuilder(700);
        builder.append("{moveCount=").append(data.getPlayerMoveCount())
                .append(",sfVL=").append(StringUtil.fdec3.format(data.survivalFlyVL))
                .append(",sfJumpPhase=").append(data.sfJumpPhase)
                .append(",liftOff=").append(data.liftOffEnvelope)
                .append(",joinOrRespawn=").append(data.joinOrRespawn)
                .append(",timeSinceSetBack=").append(data.timeSinceSetBack)
                .append(",setBack=").append(data.hasSetBack() ? formatBukkitLocation(data.getSetBack(ref)) : "none")
                .append(",morePacketsSetBack=").append(data.hasMorePacketsSetBack() ? formatBukkitLocation(data.getMorePacketsSetBack()) : "none")
                .append(",teleported=").append(data.hasTeleported() ? formatBukkitLocation(data.getTeleported()) : "none")
                .append(",speed=walk:").append(StringUtil.fdec3.format(data.walkSpeed))
                .append("->").append(StringUtil.fdec3.format(data.nextWalkSpeed))
                .append("/tick:").append(data.speedTick)
                .append(",frictionH=").append(StringUtil.fdec3.format(data.lastFrictionHorizontal))
                .append("->").append(StringUtil.fdec3.format(data.nextFrictionHorizontal))
                .append(",stuckH=").append(StringUtil.fdec3.format(data.lastStuckInBlockHorizontal))
                .append("->").append(StringUtil.fdec3.format(data.nextStuckInBlockHorizontal))
                .append(",lastGravity=").append(StringUtil.fdec3.format(data.lastGravity))
                .append(",nextGravity=").append(StringUtil.fdec3.format(data.nextGravity))
                .append(",currentMove=").append(formatPlayerMove(data.playerMoves.getCurrentMove()))
                .append(",lastMove=").append(formatPlayerMove(data.playerMoves.getFirstPastMove()))
                .append('}');
        return builder.toString();
    }

    private String formatPlayerMove(final PlayerMoveData move) {
        final StringBuilder builder = new StringBuilder(500);
        builder.append("{valid=").append(move.valid)
                .append(",toValid=").append(move.toIsValid)
                .append(",from=").append(formatLocationData(move.from));
        if (move.toIsValid) {
            builder.append(",to=").append(formatLocationData(move.to))
                    .append(",actual=").append(formatVector(move.xDistance, move.yDistance, move.zDistance))
                    .append(",h=").append(StringUtil.fdec3.format(move.hDistance))
                    .append(",distSq=").append(StringUtil.fdec3.format(move.distanceSquared))
                    .append(",allowed=").append(formatVector(move.xAllowedDistance, move.yAllowedDistance, move.zAllowedDistance))
                    .append(",hAllowed=").append(StringUtil.fdec3.format(move.hAllowedDistance))
                    .append(",over=").append(formatVector(move.xDistance - move.xAllowedDistance,
                            move.yDistance - move.yAllowedDistance, move.zDistance - move.zAllowedDistance));
        }
        builder.append(",flyCheck=").append(move.flyCheck)
                .append(",modelFlying=").append(move.modelFlying == null ? "none" : move.modelFlying.getClass().getSimpleName())
                .append(",impulse=").append(move.hasImpulse).append('/').append(move.forwardImpulse).append('/').append(move.strafeImpulse)
                .append(",collide=").append(move.collideX).append('/').append(move.collideY).append('/').append(move.collideZ)
                .append(",hCollide=").append(move.collidesHorizontally)
                .append(",minorHCollide=").append(move.negligibleHorizontalCollision)
                .append(",touchedGround=").append(move.touchedGround)
                .append(",lostGround=").append(move.fromLostGround).append('/').append(move.toLostGround)
                .append(",jump=").append(move.isJump)
                .append(",step=").append(move.isStepUp)
                .append(",multi=").append(move.multiMoveCount)
                .append(",hidden=").append(move.hiddenDistanceIndex).append('/').append(move.hiddenYDistanceIndex)
                .append(",correctedPre=").append(formatVector(move.xCorrectedDistancePre, move.yCorrectedDistancePre, move.zCorrectedDistancePre))
                .append(",correctedPost=").append(formatVector(move.xCorrectedDistancePost, 0.0, move.zCorrectedDistancePost))
                .append(",verVelUsed=").append(move.verVelUsed)
                .append('}');
        return builder.toString();
    }

    private String formatPacketModel(final NetData data, final DataPacketFlying packetData,
                                     final Location knownLocation, final Location packetLocation,
                                     final long now) {
        final DataPacketFlying[] flyingQueue = data.copyFlyingQueue();
        final DataPacketInput[] inputQueue = data.copyInputQueue();
        return new StringBuilder(900)
                .append("{queueSize=").append(flyingQueue.length)
                .append(",inputQueueSize=").append(inputQueue.length)
                .append(",lastMatchedMoveToSeq=").append(data.getLastMatchedMoveToSequence())
                .append(",currentPacketSeq=").append(packetData == null ? -1L : packetData.getSequence())
                .append(",clientMotion=").append(formatClientMotion(flyingQueue))
                .append(",knownFromPreviousPacket=").append(formatKnownFromPreviousPacket(flyingQueue, knownLocation))
                .append(",packetFromKnown=").append(formatLocationDelta(knownLocation, packetLocation))
                .append(",recentPackets=").append(formatFlyingQueue(flyingQueue, now))
                .append(",recentInputs=").append(formatInputQueue(inputQueue))
                .append('}')
                .toString();
    }

    private String formatClientMotion(final DataPacketFlying[] queue) {
        if (queue.length < 2 || !queue[0].hasPos || !queue[1].hasPos) {
            return "none";
        }
        return formatPacketDelta(queue[1], queue[0]);
    }

    private String formatKnownFromPreviousPacket(final DataPacketFlying[] queue, final Location knownLocation) {
        if (queue.length < 2 || !queue[1].hasPos) {
            return "none";
        }
        return formatPositionDelta(queue[1].getX(), queue[1].getY(), queue[1].getZ(),
                knownLocation.getX(), knownLocation.getY(), knownLocation.getZ());
    }

    private String formatFlyingQueue(final DataPacketFlying[] queue, final long now) {
        if (queue.length == 0) {
            return "[]";
        }
        final StringBuilder builder = new StringBuilder(500);
        builder.append('[');
        final int max = Math.min(queue.length, 6);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                builder.append(';');
            }
            builder.append(formatPacket(queue[i], now));
            if (i + 1 < queue.length && queue[i].hasPos && queue[i + 1].hasPos) {
                builder.append(",dPrev=").append(formatPacketDelta(queue[i + 1], queue[i]));
            }
        }
        if (queue.length > max) {
            builder.append(";...");
        }
        builder.append(']');
        return builder.toString();
    }

    private String formatInputQueue(final DataPacketInput[] queue) {
        if (queue.length == 0) {
            return "[]";
        }
        final StringBuilder builder = new StringBuilder(220);
        builder.append('[');
        final int max = Math.min(queue.length, 6);
        for (int i = 0; i < max; i++) {
            if (i > 0) {
                builder.append(';');
            }
            builder.append(formatInput(queue[i]));
        }
        if (queue.length > max) {
            builder.append(";...");
        }
        builder.append(']');
        return builder.toString();
    }

    private String formatPacket(final DataPacketFlying packetData, final long now) {
        if (packetData == null) {
            return "null";
        }
        final StringBuilder builder = new StringBuilder(220);
        builder.append("{seq=").append(packetData.getSequence())
                .append(",age=").append(now >= packetData.time ? now - packetData.time : -(packetData.time - now))
                .append(",type=").append(packetData.getSimplifiedContentType())
                .append(",tracked=").append(packetData.isTracked())
                .append(",ground=").append(packetData.onGround)
                .append(",hCollision=").append(packetData.horizontalCollision);
        if (packetData.hasPos) {
            builder.append(",pos=").append(formatVector(packetData.getX(), packetData.getY(), packetData.getZ()));
        }
        if (packetData.hasLook) {
            builder.append(",look=").append(StringUtil.fdec3.format(packetData.getYaw()))
                    .append('/').append(StringUtil.fdec3.format(packetData.getPitch()));
        }
        builder.append('}');
        return builder.toString();
    }

    private String formatKeyboardInput(final PlayerKeyboardInput input) {
        return new StringBuilder(120)
                .append("current=").append(input.getForwardDir()).append('/').append(input.getStrafeDir())
                .append('(').append(StringUtil.fdec3.format(input.getForward())).append('/')
                .append(StringUtil.fdec3.format(input.getStrafe())).append(')')
                .append(",last=(").append(StringUtil.fdec3.format(input.getLastForward())).append('/')
                .append(StringUtil.fdec3.format(input.getLastStrafe())).append(')')
                .append(",keys=jump:").append(input.isSpaceBarPressed()).append("->").append(input.wasSpaceBarPressed())
                .append(",sneak:").append(input.isShift()).append("->").append(input.wasShifting())
                .append(",sprint:").append(input.isSprintingKeyPressed()).append("->").append(input.wasSprintingKeyPressed())
                .toString();
    }

    private String formatInput(final DataPacketInput input) {
        if (input == null) {
            return "none";
        }
        return new StringBuilder(80)
                .append("f:").append(input.forward)
                .append(",b:").append(input.backward)
                .append(",l:").append(input.left)
                .append(",r:").append(input.right)
                .append(",j:").append(input.jump)
                .append(",shift:").append(input.shift)
                .append(",sprint:").append(input.sprint)
                .toString();
    }

    private String formatPacketDelta(final DataPacketFlying from, final DataPacketFlying to) {
        return formatPositionDelta(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private String formatLocationDelta(final Location from, final Location to) {
        return formatPositionDelta(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private String formatPositionDelta(final double fromX, final double fromY, final double fromZ,
                                       final double toX, final double toY, final double toZ) {
        final double xDistance = toX - fromX;
        final double yDistance = toY - fromY;
        final double zDistance = toZ - fromZ;
        final double hDistance = Math.sqrt(xDistance * xDistance + zDistance * zDistance);
        final double total = Math.sqrt(hDistance * hDistance + yDistance * yDistance);
        return new StringBuilder(120)
                .append("x=").append(StringUtil.fdec3.format(xDistance))
                .append(",y=").append(StringUtil.fdec3.format(yDistance))
                .append(",z=").append(StringUtil.fdec3.format(zDistance))
                .append(",h=").append(StringUtil.fdec3.format(hDistance))
                .append(",total=").append(StringUtil.fdec3.format(total))
                .toString();
    }

    private String formatLocationData(final LocationData loc) {
        final StringBuilder builder = new StringBuilder(140);
        builder.append(loc.getWorldName()).append('@').append(LocUtil.simpleFormat(loc));
        if (loc.extraPropertiesValid) {
            builder.append(",ground=").append(loc.onGround)
                    .append(",reset=").append(loc.resetCond)
                    .append(",liquid=").append(loc.inLiquid)
                    .append(",web=").append(loc.inWeb)
                    .append(",powder=").append(loc.inPowderSnow)
                    .append(",ice=").append(loc.onIce || loc.onBlueIce)
                    .append(",slime=").append(loc.onSlimeBlock)
                    .append(",honey=").append(loc.onHoneyBlock);
        }
        else {
            builder.append(",flags=unknown");
        }
        return builder.toString();
    }

    private String formatBukkitLocation(final Location loc) {
        return loc == null ? "null" : (loc.getWorld() == null ? "null" : loc.getWorld().getName()) + '@' + LocUtil.simpleFormat(loc);
    }

    private String formatVector(final double x, final double y, final double z) {
        return StringUtil.fdec3.format(x) + "," + StringUtil.fdec3.format(y) + "," + StringUtil.fdec3.format(z);
    }
}
